package qa.hafez.hafez.issues.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.issues.event.IssueEvents;
import qa.hafez.hafez.issues.model.IncidentType;
import qa.hafez.hafez.issues.model.Issue;
import qa.hafez.hafez.issues.model.IssueEvent;
import qa.hafez.hafez.issues.model.IssueEventType;
import qa.hafez.hafez.issues.model.IssueStatus;
import qa.hafez.hafez.issues.repository.IssueEventRepository;
import qa.hafez.hafez.issues.repository.IssueRepository;
import qa.hafez.hafez.shared.events.ActivityActions;
import qa.hafez.hafez.shared.events.AuditPublisher;
import qa.hafez.hafez.shared.events.PersonStatusChanged;
import qa.hafez.hafez.shared.exception.ConflictException;
import qa.hafez.hafez.shared.exception.ForbiddenException;
import qa.hafez.hafez.shared.exception.NotFoundException;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.model.Severity;
import qa.hafez.hafez.shared.security.AccessPolicy;
import qa.hafez.hafez.shared.security.CurrentUser;
import qa.hafez.hafez.shared.util.SchoolCalendar;

/**
 * The issue <b>state machine</b>: the only class that creates issues and changes their status
 * (proposal 5.1 and 5.2). Everything else asks this service, so every transition is checked, recorded as
 * an {@link IssueEvent}, audited and announced as a domain event in one place.
 *
 * <pre>
 *  open ──► OWNER_NOTIFIED ──reminder──► REMINDER_SENT ──a school day──► NEEDS_FOLLOW_UP
 *       ──► NEEDS_REVIEW (uncertain file / no owner: coordinator decides)
 *       ──► OWNER_DISAGREES / OWNER_ASKED_FOR_HELP (owner replies; coordinator answers)
 *  closed: FIXED (only by a re-check) · ALLOWED_EXCEPTION · RULE_PAUSED · CLOSED_RULE_REMOVED
 * </pre>
 *
 * <p>This service depends on nothing but shared code: it does not know about rules, files or
 * notifications. The rule engine calls {@link #open}; user actions live in {@link IssueWorkflowService};
 * the timers in {@link IssueFollowUpService}.
 */
@Service
@RequiredArgsConstructor
public class IssueService {

    /** The list filters of the follow-up screen (FR-11.4): All open, Needs me, Urgent, Closed. */
    public enum Filter { ALL, ALL_OPEN, NEEDS_ME, URGENT, CLOSED }

    /** The four counts on the principal's "My school" screen (FR-11.6). */
    public record Counts(long open, long urgent, long needFollowUp, long fixed) {}

    /**
     * Everything needed to open an issue. The rule engine fills it from the file and the rule version, so
     * this module needs neither entity.
     *
     * @param ownerId      the owning person; null if the owner is not in the directory
     * @param incidentHint a major-incident type this issue amounts to (see R6, R10); may be null
     * @param uncertain    true when the file's classification is uncertain
     */
    public record OpenCommand(UUID orgId, UUID siteId, UUID fileId, UUID subjectId, UUID ruleId, String ruleCode,
                              UUID ruleVersionId, UUID ownerId, Severity severity, int reminderDays,
                              String detailEn, String detailAr, boolean uncertain, IncidentType incidentHint) {}

    private final IssueRepository issues;
    private final IssueEventRepository events;
    private final AuditPublisher audit;
    private final ApplicationEventPublisher publisher;
    private final AccessPolicy policy;
    private final Clock clock;

    // ================================================================== creating

    /**
     * Opens an issue: one rule broken by one file (FR-1.3).
     *
     * <p>If the file is uncertain, or its owner is not in the directory, the issue starts as
     * {@code NEEDS_REVIEW} and only the coordinator is told (design principle 4). Otherwise it starts as
     * {@code OWNER_NOTIFIED}: the owner is told once and the reminder is scheduled.
     */
    @Transactional
    public Issue open(OpenCommand cmd) {
        Instant now = Instant.now(clock);
        boolean needsReview = cmd.uncertain() || cmd.ownerId() == null;

        Issue issue = new Issue();
        issue.setOrgId(cmd.orgId());
        issue.setSiteId(cmd.siteId());
        issue.setFileId(cmd.fileId());
        issue.setRuleId(cmd.ruleId());
        issue.setRuleVersionId(cmd.ruleVersionId());
        issue.setOwnerId(cmd.ownerId());
        issue.setSubjectId(cmd.subjectId());
        issue.setSeverity(cmd.severity());
        issue.setDetailEn(cmd.detailEn());
        issue.setDetailAr(cmd.detailAr());
        issue.setOpenedAt(now);
        issue.setStatus(needsReview ? IssueStatus.NEEDS_REVIEW : IssueStatus.OWNER_NOTIFIED);
        if (!needsReview) {
            issue.setReminderDueAt(SchoolCalendar.addSchoolDays(now, cmd.reminderDays()));
        }
        issues.save(issue);

        addEvent(issue, null, IssueEventType.OPENED, null, issue.getStatus(), cmd.ruleCode());
        audit.log(issue.getOrgId(), issue.getSiteId(), ActivityActions.ISSUE_OPENED)
                .on("Issue", issue.getId()).subject(issue.getSubjectId())
                .change(null, cmd.ruleCode() + " " + issue.getStatus()).save();
        publisher.publishEvent(new IssueEvents.Opened(issue.getId(), needsReview, cmd.incidentHint()));
        return issue;
    }

    // ================================================================== transitions

    /** Moves an issue to another status, recording the event and the audit entry. */
    @Transactional
    public void changeStatus(Issue issue, IssueStatus to, UUID actorId, IssueEventType type, String note) {
        IssueStatus from = issue.getStatus();
        if (from == to) {
            return;
        }
        issue.setStatus(to);
        if (!to.isOpen()) { // closing: stop all timers
            issue.setClosedAt(Instant.now(clock));
            issue.setReminderDueAt(null);
            issue.setFollowUpDueAt(null);
        }
        issues.save(issue);
        addEvent(issue, actorId, type, from, to, note);
        audit.log(issue.getOrgId(), issue.getSiteId(), ActivityActions.ISSUE_STATUS_CHANGED)
                .by(actorId).on("Issue", issue.getId()).subject(issue.getSubjectId())
                .change(from.name(), to.name()).save();
    }

    /** Closes an open issue with a closed status (FIXED, ALLOWED_EXCEPTION, RULE_PAUSED, CLOSED_RULE_REMOVED). */
    @Transactional
    public void closeAs(Issue issue, IssueStatus closedStatus, UUID actorId, String note) {
        if (closedStatus.isOpen()) {
            throw new IllegalArgumentException(closedStatus + " is not a closed status");
        }
        if (!issue.isOpen()) {
            throw new ConflictException("This issue is already closed.");
        }
        changeStatus(issue, closedStatus, actorId, IssueEventType.CLOSED, note);
    }

    /** A re-check found the file still broken: refresh the wording of "what is wrong" and the subject. */
    @Transactional
    public void refreshDetail(Issue issue, String detailEn, String detailAr, UUID subjectId) {
        issue.setDetailEn(detailEn);
        issue.setDetailAr(detailAr);
        issue.setSubjectId(subjectId);
        issues.save(issue);
    }

    /**
     * A coordinator classified an uncertain file and the rule now clearly applies: the issue leaves
     * "Needs review" and the owner is told for the first time.
     */
    @Transactional
    public void promoteFromReview(Issue issue, int reminderDays) {
        issue.setReminderDueAt(SchoolCalendar.addSchoolDays(Instant.now(clock), reminderDays));
        changeStatus(issue, IssueStatus.OWNER_NOTIFIED, null, IssueEventType.STATUS_CHANGED, "Classified by the coordinator");
        publisher.publishEvent(new IssueEvents.Opened(issue.getId(), false, null));
    }

    /** A leaver's open issues move to the coordinator (section 4, "Left"). Triggered by the person's status change. */
    @EventListener
    @Transactional
    public void onPersonStatusChanged(PersonStatusChanged event) {
        if (!event.hasLeft()) {
            return;
        }
        for (Issue issue : issues.findByOwnerIdAndStatusIn(event.personId(), IssueStatus.OPEN)) {
            issue.setOwnerId(null);
            issue.setReminderDueAt(null);
            changeStatus(issue, IssueStatus.NEEDS_REVIEW, null, IssueEventType.REASSIGNED,
                    "The owner left; moved to the coordinator");
            audit.log(issue.getOrgId(), issue.getSiteId(), ActivityActions.ISSUE_REASSIGNED)
                    .on("Issue", issue.getId()).subject(issue.getSubjectId()).save();
            publisher.publishEvent(new IssueEvents.Reassigned(issue.getId()));
        }
    }

    /** Adds one step to the issue's history (FR-5.1). */
    @Transactional
    public void addEvent(Issue issue, UUID actorId, IssueEventType type, IssueStatus from, IssueStatus to, String note) {
        IssueEvent e = new IssueEvent();
        e.setOrgId(issue.getOrgId());
        e.setSiteId(issue.getSiteId());
        e.setIssueId(issue.getId());
        e.setActorId(actorId);
        e.setEventType(type);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setNote(note == null ? null : (note.length() > 1000 ? note.substring(0, 1000) : note));
        events.save(e);
    }

    // ================================================================== reading (role-scoped)

    /** One issue, if the user is allowed to see it; otherwise "not found". */
    @Transactional(readOnly = true)
    public Issue getVisible(CurrentUser user, UUID issueId) {
        Issue issue = issues.findById(issueId).orElseThrow(() -> new NotFoundException("The issue"));
        policy.requireView(user, issue.scope(), "The issue");
        return issue;
    }

    /** The issue's history, oldest first. */
    @Transactional(readOnly = true)
    public List<IssueEvent> history(CurrentUser user, UUID issueId) {
        getVisible(user, issueId);
        return events.findByIssueIdOrderByCreatedAtAsc(issueId);
    }

    /** The issues of one file that the user may see (the file-details screen). */
    @Transactional(readOnly = true)
    public List<Issue> forFile(CurrentUser user, UUID fileId) {
        return issues.findByFileIdOrderByOpenedAtDesc(fileId).stream()
                .filter(i -> policy.canView(user, i.scope())).toList();
    }

    /**
     * The issues a user may see, narrowed by a filter. Teachers see their own, coordinators their
     * subject's, principals their school's. School IT and Leadership see <i>counts only</i>, never
     * individual issues.
     */
    @Transactional(readOnly = true)
    public List<Issue> listFor(CurrentUser user, Filter filter) {
        List<Issue> base = switch (user.role()) {
            case TEACHER, STUDENT, PARENT -> issues.findByOwnerIdOrderByOpenedAtDesc(user.personId());
            case COORDINATOR -> user.subjectId() == null
                    ? issues.findBySiteIdOrderByOpenedAtDesc(user.siteId())
                    : issues.findBySiteIdAndSubjectIdOrderByOpenedAtDesc(user.siteId(), user.subjectId());
            case PRINCIPAL -> issues.findBySiteIdOrderByOpenedAtDesc(user.siteId());
            case SCHOOL_IT, LEADERSHIP -> throw new ForbiddenException("Your role sees counts only, not individual issues.");
        };
        return base.stream().filter(i -> matches(user, i, filter)).sorted(urgentFirst()).toList();
    }

    private boolean matches(CurrentUser user, Issue issue, Filter filter) {
        return switch (filter) {
            case ALL -> true;
            case ALL_OPEN -> issue.isOpen();
            case URGENT -> issue.isOpen() && issue.getSeverity() == Severity.URGENT;
            case CLOSED -> !issue.isOpen();
            // "Needs me": for a coordinator, what waits for her; for a teacher, what waits for them.
            case NEEDS_ME -> user.is(Role.COORDINATOR)
                    ? IssueStatus.NEEDS_COORDINATOR.contains(issue.getStatus())
                    : issue.getStatus() == IssueStatus.OWNER_NOTIFIED || issue.getStatus() == IssueStatus.REMINDER_SENT;
        };
    }

    /** Urgent first, then newest first. */
    private Comparator<Issue> urgentFirst() {
        return Comparator.comparing((Issue i) -> i.getSeverity() == Severity.URGENT ? 0 : 1)
                .thenComparing(Issue::getOpenedAt, Comparator.reverseOrder());
    }

    /** The four counts of a school; used by the principal's "My school" screen and School IT. */
    @Transactional(readOnly = true)
    public Counts counts(UUID siteId) {
        return new Counts(
                issues.countBySiteIdAndStatusIn(siteId, IssueStatus.OPEN),
                issues.countBySiteIdAndStatusInAndSeverity(siteId, IssueStatus.OPEN, Severity.URGENT),
                issues.countBySiteIdAndStatus(siteId, IssueStatus.NEEDS_FOLLOW_UP),
                issues.countBySiteIdAndStatus(siteId, IssueStatus.FIXED));
    }
}
