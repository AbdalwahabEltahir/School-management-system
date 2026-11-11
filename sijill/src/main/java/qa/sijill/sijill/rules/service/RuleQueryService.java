package qa.sijill.sijill.rules.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.issues.model.IssueStatus;
import qa.sijill.sijill.issues.repository.IssueRepository;
import qa.sijill.sijill.rules.model.Rule;
import qa.sijill.sijill.rules.model.RuleStatus;
import qa.sijill.sijill.rules.model.RuleVersion;
import qa.sijill.sijill.rules.repository.RuleRepository;
import qa.sijill.sijill.rules.repository.RuleVersionRepository;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Language;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.AccessPolicy;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.shared.util.MessageTemplates;

/**
 * The <b>read side</b> of rules: what the rules list shows, a rule's versions, how many open issues a
 * deletion would close, and the live preview while editing (FR-8.1). Nothing here changes data; changes
 * are in {@link RuleService}.
 */
@Service
@RequiredArgsConstructor
public class RuleQueryService {

    /** A rule with its effective version, any version waiting for approval, and its open-issue count. */
    public record RuleEntry(Rule rule, RuleVersion effective, RuleVersion pending, long openIssues) {}

    /** Live preview of a draft: how many files would break it, and what the message would say. */
    public record Preview(RuleEngineService.ImpactPreview impact, MessageTemplates.Text messageEn,
                          MessageTemplates.Text messageAr) {}

    private final RuleRepository rules;
    private final RuleVersionRepository versions;
    private final IssueRepository issues;
    private final RuleEngineService engine;
    private final AccessPolicy policy;

    /**
     * The rules a user may see. Teachers, coordinators and principals see <i>active</i> rules ("The rules"
     * page); School IT and Leadership also see paused and draft rules.
     */
    @Transactional(readOnly = true)
    public List<RuleEntry> list(CurrentUser user) {
        if (user.isAny(Role.STUDENT, Role.PARENT)) {
            throw new qa.sijill.sijill.shared.exception.ForbiddenException("The rules are for school staff.");
        }
        boolean seesAll = user.isAny(Role.SCHOOL_IT, Role.LEADERSHIP);
        return rules.findVisible(user.orgId(), user.siteId()).stream()
                .filter(r -> seesAll || r.getStatus() == RuleStatus.ACTIVE)
                .map(this::entry)
                .toList();
    }

    /** All versions of a rule, newest first (School IT and Leadership). */
    @Transactional(readOnly = true)
    public List<RuleVersion> versions(CurrentUser user, UUID ruleId) {
        policy.requireRole(user, Role.SCHOOL_IT, Role.LEADERSHIP);
        return versions.findByRuleIdOrderByVersionNumberDesc(visibleRule(user, ruleId).getId());
    }

    /** How many open issues deleting the rule would close (the "use Pause instead" warning). */
    @Transactional(readOnly = true)
    public long openIssueCount(CurrentUser user, UUID ruleId) {
        policy.requireRole(user, Role.SCHOOL_IT, Role.LEADERSHIP);
        return issues.findByRuleIdAndStatusIn(visibleRule(user, ruleId).getId(), IssueStatus.OPEN).size();
    }

    /**
     * Live preview while editing: a dry run against the school's current files plus the message as the
     * owner would read it, in English and Arabic. Nothing is saved or sent.
     */
    @Transactional(readOnly = true)
    public Preview preview(CurrentUser user, RuleService.Draft draft) {
        policy.requireRole(user, Role.SCHOOL_IT);
        draft.validate();
        var impact = engine.previewImpact(draft.toVersion(user.orgId(), null, 0, user.personId()), user.siteId());

        String sampleFile = "Sample file";
        String foundEn = "(what Sijill found in the file)";
        String foundAr = "(ما وجده Sijill في الملف)";
        var en = MessageTemplates.issue(Language.EN, sampleFile, foundEn, foundAr, draft.whyItMatters(),
                draft.whyItMattersAr(), draft.fixStepsEn(), draft.fixStepsAr());
        var ar = MessageTemplates.issue(Language.AR, sampleFile, foundEn, foundAr, draft.whyItMatters(),
                draft.whyItMattersAr(), draft.fixStepsEn(), draft.fixStepsAr());
        return new Preview(impact, en, ar);
    }

    // ------------------------------------------------------------------ shared with RuleService

    /** A rule with its versions and open-issue count. */
    RuleEntry entry(Rule rule) {
        RuleVersion effective = versions.findFirstByRuleIdAndApprovedAtIsNotNullOrderByVersionNumberDesc(rule.getId()).orElse(null);
        RuleVersion newest = versions.findFirstByRuleIdOrderByVersionNumberDesc(rule.getId()).orElse(null);
        RuleVersion pending = newest != null && !newest.isApproved() ? newest : null;
        long open = issues.findByRuleIdAndStatusIn(rule.getId(), IssueStatus.OPEN).size();
        return new RuleEntry(rule, effective, pending, open);
    }

    /** A rule the user can see (their school's or network-wide), else "not found". */
    Rule visibleRule(CurrentUser user, UUID ruleId) {
        return rules.findById(ruleId)
                .filter(r -> r.getOrgId().equals(user.orgId()))
                .filter(r -> r.isNetworkWide() || user.is(Role.LEADERSHIP) || r.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new NotFoundException("The rule"));
    }
}
