package qa.sijill.sijill.issues.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.issues.event.FileRechecker;
import qa.sijill.sijill.issues.event.IssueEvents;
import qa.sijill.sijill.issues.model.Issue;
import qa.sijill.sijill.issues.model.IssueEventType;
import qa.sijill.sijill.issues.model.IssueStatus;
import qa.sijill.sijill.issues.repository.IssueRepository;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.security.AccessPolicy;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.shared.util.SchoolCalendar;

/**
 * What people can <b>do</b> to an issue (proposal 5.1, 5.2 and the transitions of FR-2.4).
 *
 * <p><b>The owner</b> can press <i>I've fixed it</i>, <i>This looks wrong</i> (a note is required) or
 * <i>I need help</i>. <b>The subject coordinator</b> answers: keep the rule, help and mark fixed, remind,
 * or record a follow-up (allowing an exception is in {@link ExceptionService}).
 *
 * <p>Nobody can close an issue by clicking: "fixed" always triggers a <b>real re-check</b> through the
 * {@link FileRechecker} port (FR-11.8), and only that re-check closes the issue (design principle 5).
 */
@Service
@RequiredArgsConstructor
public class IssueWorkflowService {

    /** Result of a "fixed" attempt: either closed, or still broken with the current explanation. */
    public record FixResult(boolean fixed, String stillWrongEn, String stillWrongAr) {}

    private final IssueService issueService;
    private final IssueRepository issues;
    private final FileRechecker rechecker;
    private final ApplicationEventPublisher publisher;
    private final AccessPolicy policy;
    private final Clock clock;

    // ================================================================== owner actions

    /** "I've fixed it": refresh the file from the workspace and re-check. Closes only if the check passes. */
    @Transactional
    public FixResult ownerMarksFixed(CurrentUser user, UUID issueId) {
        Issue issue = loadForOwner(user, issueId);
        return recheck(issue, user);
    }

    /** "This looks wrong": the owner disagrees. A note is required. The coordinator decides. */
    @Transactional
    public Issue ownerDisagrees(CurrentUser user, UUID issueId, String note) {
        Issue issue = loadForOwner(user, issueId);
        if (note == null || note.isBlank()) {
            throw new BadRequestException("Please tell us why this looks wrong.");
        }
        issue.setOwnerNote(note.trim());
        issueService.changeStatus(issue, IssueStatus.OWNER_DISAGREES, user.personId(),
                IssueEventType.STATUS_CHANGED, note.trim());
        publisher.publishEvent(new IssueEvents.OwnerResponded(issue.getId(),
                "المالك يرى أن الملاحظة غير صحيحة", "The owner thinks this looks wrong"));
        return issue;
    }

    /** "I need help": the coordinator is asked to help. */
    @Transactional
    public Issue ownerAsksForHelp(CurrentUser user, UUID issueId) {
        Issue issue = loadForOwner(user, issueId);
        issueService.changeStatus(issue, IssueStatus.OWNER_ASKED_FOR_HELP, user.personId(),
                IssueEventType.STATUS_CHANGED, "The owner asked for help");
        publisher.publishEvent(new IssueEvents.OwnerResponded(issue.getId(),
                "المالك يطلب المساعدة", "The owner asked for help"));
        return issue;
    }

    // ================================================================== coordinator actions

    /**
     * The owner disagreed and the coordinator keeps the rule: back to "Reminder sent", and the answer is
     * saved as feedback for the checker (FR-2.4).
     */
    @Transactional
    public Issue keepRule(CurrentUser user, UUID issueId) {
        Issue issue = loadForCoordinator(user, issueId);
        if (issue.getStatus() != IssueStatus.OWNER_DISAGREES) {
            throw new ConflictException("The owner has not disagreed with this issue.");
        }
        issueService.addEvent(issue, user.personId(), IssueEventType.FEEDBACK_SAVED, issue.getStatus(),
                IssueStatus.REMINDER_SENT, "Coordinator kept the rule; saved as feedback for the checker");
        issue.setFollowUpDueAt(SchoolCalendar.addSchoolDays(Instant.now(clock), 1));
        issueService.changeStatus(issue, IssueStatus.REMINDER_SENT, user.personId(),
                IssueEventType.STATUS_CHANGED, "The coordinator kept the rule");
        publisher.publishEvent(new IssueEvents.DecisionMade(issue.getId(),
                "أبقى المنسق القاعدة على ملفك؛ يرجى مراجعة الخطوات",
                "The coordinator kept the rule for your file; please review the steps"));
        return issue;
    }

    /** The coordinator helped (or fixed it herself) and marks it fixed: Hafez re-checks before closing. */
    @Transactional
    public FixResult coordinatorMarksFixed(CurrentUser user, UUID issueId) {
        return recheck(loadForCoordinator(user, issueId), user);
    }

    /** The coordinator reminds the owner. */
    @Transactional
    public Issue remind(CurrentUser user, UUID issueId) {
        Issue issue = loadForCoordinator(user, issueId);
        issue.setFollowUpDueAt(SchoolCalendar.addSchoolDays(Instant.now(clock), 1));
        issueService.changeStatus(issue, IssueStatus.REMINDER_SENT, user.personId(),
                IssueEventType.REMINDER_SENT, "The coordinator sent a reminder");
        publisher.publishEvent(new IssueEvents.ReminderDue(issue.getId()));
        return issue;
    }

    /**
     * The coordinator records that she followed up (spoke to the owner, visited, …). Nothing is closed:
     * "the next check decides" (FR-2.4). The issue leaves the follow-up list and returns to it a school
     * day later if it is still unresolved.
     */
    @Transactional
    public Issue recordFollowUp(CurrentUser user, UUID issueId, String note) {
        Issue issue = loadForCoordinator(user, issueId);
        issueService.addEvent(issue, user.personId(), IssueEventType.FOLLOW_UP_RECORDED, issue.getStatus(),
                issue.getStatus(), note);
        if (issue.getStatus() == IssueStatus.NEEDS_FOLLOW_UP) {
            issue.setFollowUpDueAt(SchoolCalendar.addSchoolDays(Instant.now(clock), 1));
            issueService.changeStatus(issue, IssueStatus.REMINDER_SENT, user.personId(),
                    IssueEventType.STATUS_CHANGED, "Follow-up recorded");
        }
        return issue;
    }

    /** "Mark all N follow-ups as done" (FR-11.4). Returns how many were recorded. */
    @Transactional
    public int markAllFollowUpsDone(CurrentUser user) {
        List<Issue> due = issueService.listFor(user, IssueService.Filter.NEEDS_ME).stream()
                .filter(i -> i.getStatus() == IssueStatus.NEEDS_FOLLOW_UP).toList();
        due.forEach(i -> recordFollowUp(user, i.getId(), "Marked as followed up"));
        return due.size();
    }

    // ================================================================== helpers

    private Issue loadForOwner(CurrentUser user, UUID issueId) {
        Issue issue = issueService.getVisible(user, issueId);
        policy.requireOwner(user, issue.scope());
        requireOpen(issue);
        return issue;
    }

    private Issue loadForCoordinator(CurrentUser user, UUID issueId) {
        Issue issue = issueService.getVisible(user, issueId);
        policy.requireCoordinator(user, issue.scope());
        requireOpen(issue);
        return issue;
    }

    /**
     * Re-checks the file and reports whether this issue closed. If it did not, the owner is told what is
     * still wrong (FR-2.6).
     */
    private FixResult recheck(Issue issue, CurrentUser actor) {
        rechecker.recheck(issue.getFileId());

        Issue current = issues.findById(issue.getId()).orElseThrow();
        if (!current.isOpen()) {
            return new FixResult(true, null, null);
        }
        issueService.addEvent(current, actor.personId(), IssueEventType.STILL_BROKEN, current.getStatus(),
                current.getStatus(), current.getDetailEn());
        publisher.publishEvent(new IssueEvents.StillBroken(current.getId()));
        return new FixResult(false, current.getDetailEn(), current.getDetailAr());
    }

    private void requireOpen(Issue issue) {
        if (!issue.isOpen()) {
            throw new ConflictException("This issue is already closed.");
        }
    }
}
