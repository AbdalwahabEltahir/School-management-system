package qa.hafez.hafez.rules.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.files.event.FileEvents;
import qa.hafez.hafez.files.model.FilePermission;
import qa.hafez.hafez.files.model.FileProfile;
import qa.hafez.hafez.files.model.SchoolFile;
import qa.hafez.hafez.files.repository.FilePermissionRepository;
import qa.hafez.hafez.files.repository.FileProfileRepository;
import qa.hafez.hafez.files.repository.SchoolFileRepository;
import qa.hafez.hafez.files.service.FileService;
import qa.hafez.hafez.issues.event.FileRechecker;
import qa.hafez.hafez.issues.model.ExceptionGrant;
import qa.hafez.hafez.issues.model.IncidentType;
import qa.hafez.hafez.issues.model.Issue;
import qa.hafez.hafez.issues.model.IssueStatus;
import qa.hafez.hafez.issues.repository.ExceptionGrantRepository;
import qa.hafez.hafez.issues.repository.IssueRepository;
import qa.hafez.hafez.issues.service.IssueService;
import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.people.model.PersonStatus;
import qa.hafez.hafez.people.repository.PersonRepository;
import qa.hafez.hafez.rules.check.CheckContext;
import qa.hafez.hafez.rules.check.CheckOutcome;
import qa.hafez.hafez.rules.check.CheckRegistry;
import qa.hafez.hafez.rules.model.CheckType;
import qa.hafez.hafez.rules.model.Rule;
import qa.hafez.hafez.rules.model.RuleStatus;
import qa.hafez.hafez.rules.model.RuleVersion;
import qa.hafez.hafez.rules.repository.RuleRepository;
import qa.hafez.hafez.rules.repository.RuleVersionRepository;
import qa.hafez.hafez.shared.events.PersonStatusChanged;
import qa.hafez.hafez.shared.exception.NotFoundException;

/**
 * <b>The rule engine</b> (stage 4 of the pipeline, proposal 6): decides, for one file, which rules are
 * broken and keeps the issues in step with that decision. It is also the {@link FileRechecker} the
 * {@code issues} module uses for "I've fixed it".
 *
 * <p>For every active rule that applies to the file's school it asks the {@code FileCheck} for the
 * rule's check type whether the condition is broken, then reconciles with the existing issue:
 * <pre>
 *  broken?   applies?   open issue?   exception?   result
 *  ───────   ────────   ───────────   ──────────   ─────────────────────────────────────────────
 *  no        –          yes           –            close as FIXED (only a re-check closes, FR-1.4)
 *  –         no         yes           –            close as FIXED (rule no longer applies)
 *  yes       yes/unk    –             yes          stay quiet (allowed exception)
 *  yes       yes        yes           –            keep it; refresh the wording; promote if it was in review
 *  yes       yes        no            no           open an issue, tell the owner
 *  yes       UNKNOWN    no            no           open it as NEEDS_REVIEW, tell only the coordinator
 * </pre>
 * "Applies" is decided from the file's profile: see {@link #appliesTo}. Uncertain files are never
 * silently passed as fine: they surface as "Needs review".
 *
 * <p>It re-checks automatically when a coordinator classifies a file ({@link FileEvents.Classified}) and
 * when a person starts leaving or has left ({@link PersonStatusChanged}, rule R8).
 */
@Service
@RequiredArgsConstructor
public class RuleEngineService implements FileRechecker {

    /** Does a rule's scope (document types / sensitivities) cover a file? */
    enum Applies { YES, NO, UNKNOWN }

    /** What one evaluation changed. */
    public record Result(int opened, int closed) {
        public static final Result NONE = new Result(0, 0);

        public Result plus(Result other) {
            return new Result(opened + other.opened, closed + other.closed);
        }
    }

    /** Dry-run of a rule against the school's current files (FR-8.1 "live impact preview"). */
    public record ImpactPreview(int filesChecked, int wouldBreak, int wouldNeedReview) {}

    private final SchoolFileRepository files;
    private final FilePermissionRepository permissions;
    private final FileProfileRepository profiles;
    private final FileService fileService;
    private final PersonRepository people;
    private final RuleRepository rules;
    private final RuleVersionRepository ruleVersions;
    private final IssueRepository issues;
    private final ExceptionGrantRepository exceptions;
    private final CheckRegistry registry;
    private final IssueService issueService;
    private final Clock clock;

    // ================================================================== re-check entry points

    /** The {@link FileRechecker} port: re-read the file from the workspace (read-only), then evaluate it. */
    @Override
    @Transactional
    public void recheck(UUID fileId) {
        fileService.refreshFromWorkspace(fileId);
        evaluateFile(fileId);
    }

    /** A coordinator answered "Needs review": judge the file again with its new classification. */
    @EventListener
    @Transactional
    public void on(FileEvents.Classified event) {
        evaluateFile(event.fileId());
    }

    /** A person is leaving (rule R8 asks for their files) or has left: re-check all their files. */
    @EventListener
    @Transactional
    public void on(PersonStatusChanged event) {
        if (event.isLeaving() || event.hasLeft()) {
            recheckOwnerFiles(event.email());
        }
    }

    // ================================================================== evaluate

    /** Evaluates every active rule against one file and updates the issues. */
    @Transactional
    public Result evaluateFile(UUID fileId) {
        SchoolFile file = files.findById(fileId).orElseThrow(() -> new NotFoundException("The file"));
        List<FilePermission> perms = permissions.findByFileId(fileId);
        FileProfile profile = profiles.findByFileId(fileId).orElse(null);
        Person owner = ownerOf(file);
        LocalDate today = LocalDate.now(clock);

        Result total = Result.NONE;
        for (Rule rule : rules.findApplicable(file.getOrgId(), file.getSiteId(), RuleStatus.ACTIVE)) {
            Optional<RuleVersion> version =
                    ruleVersions.findFirstByRuleIdAndApprovedAtIsNotNullOrderByVersionNumberDesc(rule.getId());
            if (version.isEmpty()) {
                continue; // a rule with no approved version is not in force yet
            }
            total = total.plus(evaluateRule(file, perms, profile, owner, rule, version.get(), today));
        }
        file.setLastCheckedAt(clock.instant());
        return total;
    }

    /** Re-checks all of a person's files; a leaver raises rule R8 for school files in a personal drive. */
    @Transactional
    public Result recheckOwnerFiles(String ownerEmail) {
        Result total = Result.NONE;
        for (SchoolFile file : files.findByOwnerEmailIgnoreCase(ownerEmail)) {
            total = total.plus(evaluateFile(file.getId()));
        }
        return total;
    }

    private Result evaluateRule(SchoolFile file, List<FilePermission> perms, FileProfile profile, Person owner,
                                Rule rule, RuleVersion version, LocalDate today) {
        CheckOutcome outcome = runCheck(file, perms, profile, owner, version, today);
        Applies applies = appliesTo(version, profile);
        Optional<Issue> open = issues.findFirstByFileIdAndRuleIdAndStatusIn(file.getId(), rule.getId(), IssueStatus.OPEN);

        // Nothing wrong, or the rule does not cover this file: close an open issue (only a re-check closes).
        if (!outcome.broken() || applies == Applies.NO) {
            if (open.isPresent()) {
                issueService.closeAs(open.get(), IssueStatus.FIXED, null,
                        outcome.broken() ? "The rule no longer applies to this file" : "Re-check found the problem is gone");
                return new Result(0, 1);
            }
            return Result.NONE;
        }

        // Broken, but a coordinator allowed an exception: stay quiet until it ends (FR-2.5).
        Optional<ExceptionGrant> exception = exceptions.findActive(file.getId(), rule.getId(), today);
        if (exception.isPresent()) {
            return Result.NONE;
        }

        // A person who has left cannot be told; the issue goes to the coordinator instead.
        Person responsible = owner != null && owner.getStatus() != PersonStatus.LEFT ? owner : null;

        if (open.isPresent()) {
            Issue issue = open.get();
            issueService.refreshDetail(issue, outcome.detailEn(), outcome.detailAr(), file.getSubjectId());
            if (issue.getStatus() == IssueStatus.NEEDS_REVIEW && applies == Applies.YES && issue.getOwnerId() != null) {
                issueService.promoteFromReview(issue, version.getReminderDays());
            }
            return Result.NONE;
        }

        issueService.open(new IssueService.OpenCommand(
                file.getOrgId(), file.getSiteId(), file.getId(), file.getSubjectId(),
                rule.getId(), rule.getCode(), version.getId(),
                responsible == null ? null : responsible.getId(),
                version.getSeverity(), version.getReminderDays(),
                outcome.detailEn(), outcome.detailAr(),
                applies == Applies.UNKNOWN, incidentHint(version.getCheckType())));
        return new Result(1, 0);
    }

    /** Exam shared early (R6) and important deletion (R10) amount to major incidents; nothing else does. */
    private IncidentType incidentHint(CheckType type) {
        return switch (type) {
            case SHARING_BEFORE_DATE -> IncidentType.EXAM_LEAK;
            case DELETED_OR_TRASHED -> IncidentType.IMPORTANT_DELETION;
            default -> null;
        };
    }

    /**
     * Runs the rule's check. A trashed file is only judged by the "deleted or trashed" rule; every other
     * rule treats it as fine, so deleting a file does not leave a trail of stale issues behind it.
     */
    private CheckOutcome runCheck(SchoolFile file, List<FilePermission> perms, FileProfile profile, Person owner,
                                  RuleVersion version, LocalDate today) {
        if (file.isTrashed() && version.getCheckType() != CheckType.DELETED_OR_TRASHED) {
            return CheckOutcome.ok();
        }
        return registry.get(version.getCheckType()).evaluate(new CheckContext(file, perms, profile, owner, version, today));
    }

    // ================================================================== scope

    /**
     * Does the rule's scope cover the file? A rule applies when the file's document type is listed
     * <i>or</i> its sensitivity is listed; a rule with both lists empty applies to everything. When the
     * file has no profile, or the profile is uncertain, the answer is {@link Applies#UNKNOWN}, which
     * routes the file to a person instead of passing it.
     */
    static Applies appliesTo(RuleVersion version, FileProfile profile) {
        if (version.getDocTypes().isEmpty() && version.getSensitivities().isEmpty()) {
            return Applies.YES;
        }
        if (profile == null || profile.isNeedsReview()) {
            return Applies.UNKNOWN;
        }
        boolean typeMatches = profile.getDocType() != null
                && version.getDocTypes().stream().anyMatch(t -> t.equalsIgnoreCase(profile.getDocType()));
        boolean sensitivityMatches = version.getSensitivities().contains(profile.getSensitivity());
        return typeMatches || sensitivityMatches ? Applies.YES : Applies.NO;
    }

    // ================================================================== dry run

    /**
     * "About N files would break this rule today": runs a (not yet saved) rule version against the
     * school's live files without creating issues or messages. Per-file lookups are fine for a preview;
     * batch the queries if a school ever reaches hundreds of thousands of files.
     */
    @Transactional(readOnly = true)
    public ImpactPreview previewImpact(RuleVersion draft, UUID siteId) {
        LocalDate today = LocalDate.now(clock);
        int checked = 0;
        int wouldBreak = 0;
        int wouldReview = 0;
        for (SchoolFile file : files.findBySiteIdAndTrashedAtIsNull(siteId)) {
            checked++;
            FileProfile profile = profiles.findByFileId(file.getId()).orElse(null);
            CheckOutcome outcome = runCheck(file, permissions.findByFileId(file.getId()), profile, ownerOf(file),
                    draft, today);
            if (!outcome.broken()) {
                continue;
            }
            switch (appliesTo(draft, profile)) {
                case YES -> wouldBreak++;
                case UNKNOWN -> wouldReview++;
                case NO -> { /* does not apply */ }
            }
        }
        return new ImpactPreview(checked, wouldBreak, wouldReview);
    }

    private Person ownerOf(SchoolFile file) {
        return file.getOwnerEmail() == null ? null : people.findByEmailIgnoreCase(file.getOwnerEmail()).orElse(null);
    }
}
