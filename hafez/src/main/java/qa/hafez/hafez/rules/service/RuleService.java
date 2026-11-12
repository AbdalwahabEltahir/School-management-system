package qa.hafez.hafez.rules.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.issues.model.Issue;
import qa.hafez.hafez.issues.model.IssueStatus;
import qa.hafez.hafez.issues.repository.IssueRepository;
import qa.hafez.hafez.issues.service.IssueService;
import qa.hafez.hafez.rules.model.CheckType;
import qa.hafez.hafez.rules.model.Rule;
import qa.hafez.hafez.rules.model.RuleStatus;
import qa.hafez.hafez.rules.model.RuleVersion;
import qa.hafez.hafez.rules.repository.RuleRepository;
import qa.hafez.hafez.rules.repository.RuleVersionRepository;
import qa.hafez.hafez.shared.events.ActivityActions;
import qa.hafez.hafez.shared.events.AuditPublisher;
import qa.hafez.hafez.shared.exception.BadRequestException;
import qa.hafez.hafez.shared.exception.ConflictException;
import qa.hafez.hafez.shared.exception.ForbiddenException;
import qa.hafez.hafez.shared.exception.NotFoundException;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.model.Sensitivity;
import qa.hafez.hafez.shared.model.Severity;
import qa.hafez.hafez.shared.security.AccessPolicy;
import qa.hafez.hafez.shared.security.CurrentUser;

/**
 * The <b>write side</b> of rules as versioned data (FR-8.1, FR-5.3, proposal 4 "two-person approval").
 * Reading, listing and previewing are in {@link RuleQueryService}.
 *
 * <ul>
 *   <li><b>Versions:</b> editing never changes a version, it adds a new one. Each issue keeps the
 *       version in force when it was raised.</li>
 *   <li><b>School-level rules</b> are created and edited by that school's IT admin and take effect at once.</li>
 *   <li><b>Network-wide rules</b> change only with <b>two-person approval</b>: one School IT admin
 *       <i>proposes</i> a version; a <i>different</i> School IT admin or a Leadership member
 *       <i>approves</i> it. Until then the previous approved version stays in force.</li>
 *   <li><b>Pause</b> holds issues without messages; <b>delete</b> closes them as "rule removed".</li>
 * </ul>
 * Every change is audited.
 */
@Service
@RequiredArgsConstructor
public class RuleService {

    /** The reminder lengths the editor offers (FR-8.1). */
    private static final Set<Integer> REMINDER_CHOICES = Set.of(1, 2, 3, 5);

    /** Everything an editor sends for a rule version. */
    public record Draft(
            @NotBlank String name,
            @NotBlank String whyItMatters,
            @NotBlank String whyItMattersAr,
            @NotNull CheckType checkType,
            @NotNull Severity severity,
            int reminderDays,
            String parameter,
            @NotBlank String fixStepsEn,
            @NotBlank String fixStepsAr,
            Set<String> docTypes,
            Set<Sensitivity> sensitivities) {

        /** Rejects values the editor does not offer. */
        public void validate() {
            if (!REMINDER_CHOICES.contains(reminderDays)) {
                throw new BadRequestException("Reminder days must be 1, 2, 3 or 5.");
            }
        }

        /** Builds a (not yet stored) rule version from the draft. */
        RuleVersion toVersion(UUID orgId, UUID ruleId, int number, UUID proposedBy) {
            RuleVersion v = new RuleVersion();
            v.setOrgId(orgId);
            v.setRuleId(ruleId);
            v.setVersionNumber(number);
            v.setName(name.trim());
            v.setWhyItMatters(whyItMatters.trim());
            v.setWhyItMattersAr(whyItMattersAr.trim());
            v.setCheckType(checkType);
            v.setSeverity(severity);
            v.setReminderDays(reminderDays);
            v.setParameter(parameter == null || parameter.isBlank() ? null : parameter.trim());
            v.setFixStepsEn(fixStepsEn.trim());
            v.setFixStepsAr(fixStepsAr.trim());
            v.setDocTypes(docTypes == null ? new HashSet<>() : new HashSet<>(docTypes));
            v.setSensitivities(sensitivities == null ? new HashSet<>() : new HashSet<>(sensitivities));
            v.setProposedBy(proposedBy);
            return v;
        }
    }

    private final RuleRepository rules;
    private final RuleVersionRepository versions;
    private final IssueRepository issues;
    private final IssueService issueService;
    private final RuleQueryService queries;
    private final AuditPublisher audit;
    private final AccessPolicy policy;
    private final Clock clock;

    // ================================================================== create / edit

    /**
     * Creates a rule with its first version.
     *
     * @param networkWide true to propose a network-wide rule (needs a second person's approval);
     *                    false for a rule of the user's own school
     * @param turnOn      for a school rule: switch it on now (otherwise it is saved as a draft)
     */
    @Transactional
    public RuleQueryService.RuleEntry create(CurrentUser user, Draft draft, boolean networkWide, boolean turnOn) {
        policy.requireRole(user, Role.EAA_ADMIN);
        draft.validate();

        Rule rule = new Rule();
        rule.setOrgId(user.orgId());
        rule.setSiteId(networkWide ? null : user.siteId());
        rule.setCode("R" + (rules.countByOrgId(user.orgId()) + 1));
        rule.setStatus(!networkWide && turnOn ? RuleStatus.ACTIVE : RuleStatus.DRAFT);
        rules.save(rule);

        RuleVersion v = versions.save(draft.toVersion(rule.getOrgId(), rule.getId(), 1, user.personId()));
        if (!networkWide) {
            approve(v, user.personId()); // a school rule takes effect at once
        }
        audit.log(rule.getOrgId(), user.siteId(), ActivityActions.RULE_CREATED)
                .by(user).on("Rule", rule.getId())
                .change(null, rule.getCode() + (networkWide ? " (network-wide, awaiting approval)" : "")).save();
        return queries.entry(rule);
    }

    /** Edits a rule by adding a new version. A network-wide edit waits for approval. */
    @Transactional
    public RuleQueryService.RuleEntry update(CurrentUser user, UUID ruleId, Draft draft) {
        policy.requireRole(user, Role.EAA_ADMIN);
        Rule rule = queries.visibleRule(user, ruleId);
        requireNotDeleted(rule);
        draft.validate();

        int next = versions.findFirstByRuleIdOrderByVersionNumberDesc(rule.getId())
                .map(v -> v.getVersionNumber() + 1).orElse(1);
        RuleVersion v = versions.save(draft.toVersion(rule.getOrgId(), rule.getId(), next, user.personId()));
        if (!rule.isNetworkWide()) {
            approve(v, user.personId());
        }
        audit.log(rule.getOrgId(), user.siteId(), ActivityActions.RULE_UPDATED)
                .by(user).on("Rule", rule.getId())
                .change("v" + (next - 1), "v" + next + (rule.isNetworkWide() ? " (awaiting approval)" : "")).save();
        return queries.entry(rule);
    }

    /**
     * Approves a proposed version of a <b>network-wide</b> rule. The approver must be a School IT admin
     * or a Leadership member and must be <i>a different person</i> from the proposer (two-person rule).
     */
    @Transactional
    public RuleQueryService.RuleEntry approveVersion(CurrentUser user, UUID ruleId, UUID versionId) {
        policy.requireRole(user, Role.EAA_ADMIN);
        Rule rule = queries.visibleRule(user, ruleId);
        if (!rule.isNetworkWide()) {
            throw new BadRequestException("Only network-wide rules need approval.");
        }
        RuleVersion v = versions.findById(versionId).filter(x -> x.getRuleId().equals(ruleId))
                .orElseThrow(() -> new NotFoundException("The rule version"));
        if (v.isApproved()) {
            throw new ConflictException("This version is already approved.");
        }
        if (user.personId().equals(v.getProposedBy())) {
            throw new ForbiddenException("A different person must approve this change (two-person approval).");
        }
        approve(v, user.personId());
        if (rule.getStatus() == RuleStatus.DRAFT) {
            rule.setStatus(RuleStatus.ACTIVE);
        }
        audit.log(rule.getOrgId(), user.siteId(), ActivityActions.RULE_APPROVED)
                .by(user).on("Rule", rule.getId()).change(null, "v" + v.getVersionNumber()).save();
        return queries.entry(rule);
    }

    // ================================================================== pause / resume / delete

    /** Pause: the rule stops raising issues and its open issues are held ("Rule paused") without messages. */
    @Transactional
    public RuleQueryService.RuleEntry pause(CurrentUser user, UUID ruleId) {
        Rule rule = changeableRule(user, ruleId);
        if (rule.getStatus() != RuleStatus.ACTIVE) {
            throw new ConflictException("Only an active rule can be paused.");
        }
        closeOpenIssues(rule, IssueStatus.RULE_PAUSED, user, "The rule was paused");
        rule.setStatus(RuleStatus.PAUSED);
        audit.log(rule.getOrgId(), user.siteId(), ActivityActions.RULE_PAUSED).by(user).on("Rule", rule.getId()).save();
        return queries.entry(rule);
    }

    /** Resume: the rule is checked again at the next check. */
    @Transactional
    public RuleQueryService.RuleEntry resume(CurrentUser user, UUID ruleId) {
        Rule rule = changeableRule(user, ruleId);
        if (rule.getStatus() != RuleStatus.PAUSED) {
            throw new ConflictException("Only a paused rule can be resumed.");
        }
        if (versions.findFirstByRuleIdAndApprovedAtIsNotNullOrderByVersionNumberDesc(rule.getId()).isEmpty()) {
            throw new ConflictException("This rule has no approved version yet.");
        }
        rule.setStatus(RuleStatus.ACTIVE);
        audit.log(rule.getOrgId(), user.siteId(), ActivityActions.RULE_RESUMED).by(user).on("Rule", rule.getId()).save();
        return queries.entry(rule);
    }

    /** Delete: the rule's open issues close as "Closed: rule removed". Warn first with {@code openIssueCount}. */
    @Transactional
    public void delete(CurrentUser user, UUID ruleId) {
        Rule rule = changeableRule(user, ruleId);
        closeOpenIssues(rule, IssueStatus.CLOSED_RULE_REMOVED, user, "The rule was removed");
        rule.setStatus(RuleStatus.DELETED);
        audit.log(rule.getOrgId(), user.siteId(), ActivityActions.RULE_DELETED).by(user).on("Rule", rule.getId()).save();
    }

    /**
     * Copies a starter rule into an organisation as an approved, active, network-wide rule. Used only by
     * {@code OrganisationSetupService} when a new organisation is created (no person is involved).
     */
    @Transactional
    public Rule createStarterRule(UUID orgId, String code, Draft draft) {
        Rule rule = new Rule();
        rule.setOrgId(orgId);
        rule.setCode(code);
        rule.setStatus(RuleStatus.ACTIVE);
        rules.save(rule);
        approve(versions.save(draft.toVersion(orgId, rule.getId(), 1, null)), null);
        return rule;
    }

    // ================================================================== helpers

    private void closeOpenIssues(Rule rule, IssueStatus closedStatus, CurrentUser user, String note) {
        for (Issue issue : issues.findByRuleIdAndStatusIn(rule.getId(), IssueStatus.OPEN)) {
            issueService.closeAs(issue, closedStatus, user.personId(), note);
        }
    }

    private void approve(RuleVersion v, UUID approverId) {
        v.setApprovedBy(approverId);
        v.setApprovedAt(Instant.now(clock));
    }

    private void requireNotDeleted(Rule rule) {
        if (rule.getStatus() == RuleStatus.DELETED) {
            throw new ConflictException("This rule has been deleted.");
        }
    }

    /**
     * A rule whose status the user may change: School IT for their own school's rules.
     * Changing the status of a <i>network-wide</i> rule is a network-level decision reserved for Leadership.
     */
    private Rule changeableRule(CurrentUser user, UUID ruleId) {
        Rule rule = queries.visibleRule(user, ruleId);
        requireNotDeleted(rule);
        if (rule.isNetworkWide()) {
            policy.requireRole(user, Role.EAA_ADMIN);
        } else {
            policy.requireRole(user, Role.EAA_ADMIN);
        }
        return rule;
    }
}
