package qa.hafez.hafez.checks.job;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import qa.hafez.hafez.checks.service.CheckRunService;
import qa.hafez.hafez.issues.service.ExceptionService;
import qa.hafez.hafez.issues.service.IssueFollowUpService;
import qa.hafez.hafez.notifications.service.MessageDispatchService;
import qa.hafez.hafez.organisation.model.Site;
import qa.hafez.hafez.organisation.repository.SiteRepository;
import qa.hafez.hafez.planning.service.PlanningService;
import qa.hafez.hafez.shared.model.JobType;
import qa.hafez.hafez.shared.web.CorrelationIds;

/**
 * <b>The schedule of every background job in one place.</b> Each job only calls a service; the logic
 * lives there. Every run gets its own correlation ID, so its log lines can be found together.
 *
 * <pre>
 *  every minute   deliver due messages                          MessageDispatchService
 *  every 5 min    change feed check (changes within minutes)    CheckRunService
 *  hourly         reminders, follow-ups, urgent escalation      IssueFollowUpService
 *  00:30          end exceptions whose date has passed          ExceptionService
 *  02:00          nightly full check of every school            CheckRunService
 *  Thursday 08:00 remind teachers who have not submitted plans  PlanningService
 * </pre>
 * Times are Qatar time. Switch every job off with {@code hafez.jobs.enabled=false} (tests do).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hafez.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);
    private static final String ZONE = "Asia/Qatar";

    private final CheckRunService checkRuns;
    private final IssueFollowUpService followUps;
    private final ExceptionService exceptions;
    private final MessageDispatchService dispatcher;
    private final PlanningService planning;
    private final SiteRepository sites;

    @Scheduled(cron = "0 0 2 * * *", zone = ZONE)
    public void nightlyFullCheck() {
        job("nightly-check", () -> forEachSite(JobType.FULL_CHECK));
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void changeFeedCheck() {
        job("change-feed", () -> forEachSite(JobType.CHANGE_FEED_CHECK));
    }

    @Scheduled(cron = "0 5 * * * *", zone = ZONE)
    public void hourlyFollowUps() {
        job("follow-ups", () -> log.info("Follow-ups touched {} issues", followUps.runDueFollowUps()));
    }

    @Scheduled(cron = "0 30 0 * * *", zone = ZONE)
    public void endDueExceptions() {
        job("exceptions", () -> log.info("Ended {} exceptions", exceptions.expireDue()));
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    public void deliverMessages() {
        job("messages", () -> {
            int sent = dispatcher.dispatchDue();
            if (sent > 0) {
                log.info("Delivered {} messages", sent);
            }
        });
    }

    @Scheduled(cron = "0 0 8 * * THU", zone = ZONE)
    public void planningReminders() {
        job("planning-reminders", () -> log.info("Reminded {} teachers", planning.remindMissing()));
    }

    private void forEachSite(JobType type) {
        List<Site> all = sites.findAll();
        for (Site site : all) {
            try {
                checkRuns.runScheduled(site, type);
            } catch (RuntimeException e) { // one school must never stop the others
                log.error("{} failed for {}", type, site.getName(), e);
            }
        }
    }

    /** Runs a job under its own correlation ID and never lets an exception escape to the scheduler. */
    private void job(String name, Runnable work) {
        CorrelationIds.runWithNewId(name, () -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                log.error("Scheduled job {} failed", name, e);
            }
        });
    }
}
