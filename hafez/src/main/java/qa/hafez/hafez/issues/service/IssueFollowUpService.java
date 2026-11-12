package qa.hafez.hafez.issues.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.issues.event.IssueEvents;
import qa.hafez.hafez.issues.model.Issue;
import qa.hafez.hafez.issues.model.IssueEventType;
import qa.hafez.hafez.issues.model.IssueStatus;
import qa.hafez.hafez.issues.repository.IssueRepository;
import qa.hafez.hafez.shared.model.Severity;
import qa.hafez.hafez.shared.util.SchoolCalendar;

/**
 * The time-driven part of follow-up (FR-1.5), run hourly by {@code ScheduledJobs}:
 * <ol>
 *   <li>an owner who has not acted by the rule's reminder days gets a <b>reminder</b>
 *       (OWNER_NOTIFIED → REMINDER_SENT);</li>
 *   <li>one school day later the issue becomes <b>Needs follow-up</b> for the coordinator
 *       (REMINDER_SENT → NEEDS_FOLLOW_UP);</li>
 *   <li>an <b>urgent</b> issue still open after one school day becomes a leadership incident.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class IssueFollowUpService {

    private final IssueRepository issues;
    private final IssueService issueService;
    private final IncidentService incidents;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    /** Runs all three steps. Returns how many issues were touched. */
    @Transactional
    public int runDueFollowUps() {
        return sendDueReminders() + markDueFollowUps() + escalateUrgent();
    }

    int sendDueReminders() {
        Instant now = Instant.now(clock);
        List<Issue> due = issues.findByStatusAndReminderDueAtBefore(IssueStatus.OWNER_NOTIFIED, now);
        for (Issue issue : due) {
            issue.setReminderDueAt(null);
            issue.setFollowUpDueAt(SchoolCalendar.addSchoolDays(now, 1));
            issueService.changeStatus(issue, IssueStatus.REMINDER_SENT, null, IssueEventType.REMINDER_SENT,
                    "Reminder sent to the owner");
            publisher.publishEvent(new IssueEvents.ReminderDue(issue.getId()));
        }
        return due.size();
    }

    int markDueFollowUps() {
        List<Issue> due = issues.findByStatusAndFollowUpDueAtBefore(IssueStatus.REMINDER_SENT, Instant.now(clock));
        for (Issue issue : due) {
            issue.setFollowUpDueAt(null);
            issueService.changeStatus(issue, IssueStatus.NEEDS_FOLLOW_UP, null, IssueEventType.STATUS_CHANGED,
                    "Still open a school day after the reminder");
            publisher.publishEvent(new IssueEvents.NeedsFollowUp(issue.getId()));
        }
        return due.size();
    }

    int escalateUrgent() {
        Instant oneSchoolDayAgo = oneSchoolDayBefore(Instant.now(clock));
        List<Issue> overdue = issues.findBySeverityAndStatusInAndOpenedAtBefore(
                Severity.URGENT, IssueStatus.OPEN, oneSchoolDayAgo);
        overdue.forEach(incidents::urgentUnresolved); // raised once per issue (de-duplicated inside)
        return overdue.size();
    }

    /** The moment one school day before {@code now} (skipping the weekend). */
    private Instant oneSchoolDayBefore(Instant now) {
        var t = now.atZone(SchoolCalendar.ZONE).minusDays(1);
        while (!SchoolCalendar.isSchoolDay(t.toLocalDate())) {
            t = t.minusDays(1);
        }
        return t.toInstant();
    }
}
