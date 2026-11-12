package qa.hafez.hafez.checks.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import qa.hafez.hafez.dashboard.service.ScoreService;
import qa.hafez.hafez.files.integration.FileClassifier;
import qa.hafez.hafez.files.model.FileProfile;
import qa.hafez.hafez.files.model.SchoolFile;
import qa.hafez.hafez.files.repository.FileProfileRepository;
import qa.hafez.hafez.files.repository.SchoolFileRepository;
import qa.hafez.hafez.files.service.ClassificationService;
import qa.hafez.hafez.files.service.FileService;
import qa.hafez.hafez.issues.service.IncidentService;
import qa.hafez.hafez.organisation.model.SettingKeys;
import qa.hafez.hafez.organisation.model.Site;
import qa.hafez.hafez.organisation.repository.SiteRepository;
import qa.hafez.hafez.organisation.service.SettingsService;
import qa.hafez.hafez.rules.service.RuleEngineService;
import qa.hafez.hafez.shared.events.ActivityActions;
import qa.hafez.hafez.shared.events.AuditPublisher;
import qa.hafez.hafez.shared.exception.ConflictException;
import qa.hafez.hafez.shared.exception.ForbiddenException;
import qa.hafez.hafez.shared.exception.NotFoundException;
import qa.hafez.hafez.shared.model.JobRun;
import qa.hafez.hafez.shared.model.JobStatus;
import qa.hafez.hafez.shared.model.JobType;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.repository.JobRunRepository;
import qa.hafez.hafez.shared.security.AccessPolicy;
import qa.hafez.hafez.shared.security.CurrentUser;
import qa.hafez.hafez.shared.web.CorrelationIds;
import qa.hafez.hafez.workspace.WorkspaceConnector;
import qa.hafez.hafez.workspace.WorkspaceFile;

/**
 * Runs the check pipeline for a school (proposal 6): <b>read</b> the workspace → <b>understand</b> each
 * file → apply the <b>rules</b> → issues and messages follow through events.
 *
 * <pre>
 *  connector.listAllFiles / listChanges
 *      └─ per file (own transaction, so one bad file never blocks the rest):
 *           FileService.upsertFromWorkspace  → metadata, sharing, snapshot
 *           FileClassifier + ClassificationService → profile (or "Needs review" if unreadable)
 *           RuleEngineService.evaluateFile   → issues opened / closed
 *      └─ files that vanished from the workspace are recorded as deleted (rule R10)
 *      └─ bulk-deletion check, health-score snapshot
 * </pre>
 * A run is a {@link JobRun} with a correlation ID, counts and the skipped files, shown under "Recent
 * checks" (FR-8.2). Runs started by a click return at once and continue in the background.
 */
@Service
@RequiredArgsConstructor
public class CheckRunService {

    private static final Logger log = LoggerFactory.getLogger(CheckRunService.class);
    private static final int MAX_SKIPPED_NAMES_IN_NOTE = 20;

    private final SiteRepository sites;
    private final JobRunRepository runs;
    private final SchoolFileRepository files;
    private final FileProfileRepository profiles;
    private final FileService fileService;
    private final ClassificationService classification;
    private final RuleEngineService ruleEngine;
    private final IncidentService incidents;
    private final ScoreService scores;
    private final SettingsService settings;
    private final AuditPublisher audit;
    private final AccessPolicy policy;
    private final ObjectProvider<WorkspaceConnector> connector;
    private final ObjectProvider<FileClassifier> classifier;
    private final TransactionTemplate tx;
    private final ThreadPoolTaskExecutor checkExecutor;
    private final Clock clock;

    // ================================================================== starting a run

    /**
     * "Run a full check now" / "Run the first check". School IT for their own school, Leadership for any.
     * Returns the new run immediately; the work continues in the background.
     */
    public JobRun start(CurrentUser user, UUID siteId) {
        Site site = sites.findById(siteId).filter(Site::isActive).orElseThrow(() -> new NotFoundException("The school"));
        policy.requireRole(user, Role.SCHOOL_IT, Role.EAA_ADMIN);
        policy.requireSiteAccess(user, siteId, site.getOrgId());
        requireReady(site);

        JobType type = site.getFirstCheckAt() == null ? JobType.FIRST_CHECK : JobType.MANUAL_CHECK;
        JobRun run = newRun(site, type, user.personId());
        String correlationId = run.getCorrelationId();
        checkExecutor.execute(() -> CorrelationIds.runWith(correlationId, () -> execute(run.getId())));
        return run;
    }

    /** Called by {@code ScheduledJobs}: the nightly full check or the change feed. Runs on the calling thread. */
    public void runScheduled(Site site, JobType type) {
        if (!site.isActive() || !site.isChecksEnabled() || connector.getIfAvailable() == null) {
            return;
        }
        if (type == JobType.CHANGE_FEED_CHECK && site.getFirstCheckAt() == null) {
            return; // the first check is started by a person, not by the change feed
        }
        execute(newRun(site, type, null).getId());
    }

    /** "Recent checks with skipped files" (FR-8.2). */
    public List<JobRun> recent(CurrentUser user, UUID siteId) {
        Site site = sites.findById(siteId).orElseThrow(() -> new NotFoundException("The school"));
        if (!user.isAny(Role.SCHOOL_IT, Role.LEADERSHIP, Role.EAA_ADMIN)) {
            throw new ForbiddenException("Check runs are not available for your role.");
        }
        policy.requireSiteAccess(user, siteId, site.getOrgId());
        return runs.findTop20BySiteIdAndTypeInOrderByStartedAtDesc(siteId, JobType.CHECKS);
    }

    private void requireReady(Site site) {
        if (connector.getIfAvailable() == null) {
            throw new ConflictException("The school workspace is not connected yet.");
        }
        if (!site.isChecksEnabled()) {
            throw new ConflictException("The connection is switched off. Reconnect it first.");
        }
    }

    private JobRun newRun(Site site, JobType type, UUID startedBy) {
        JobRun run = new JobRun();
        run.setOrgId(site.getOrgId());
        run.setSiteId(site.getId());
        run.setType(type);
        run.setStartedBy(startedBy);
        run.setStartedAt(Instant.now(clock));
        run.setCorrelationId(CorrelationIds.current());
        return runs.save(run);
    }

    // ================================================================== running

    /** Executes one run to completion. Never throws: failures are recorded on the run. */
    void execute(UUID runId) {
        JobRun run = runs.findById(runId).orElseThrow();
        Site site = sites.findById(run.getSiteId()).orElseThrow();
        List<String> skipped = new java.util.ArrayList<>();
        log.info("Check run {} started for {} ({})", runId, site.getName(), run.getType());

        try {
            WorkspaceConnector workspace = connector.getObject();
            List<WorkspaceFile> source = run.getType() == JobType.CHANGE_FEED_CHECK
                    ? workspace.listChanges(site.getDomain(), changeFeedSince(site))
                    : workspace.listAllFiles(site.getDomain());

            Set<String> seen = new HashSet<>();
            for (WorkspaceFile wf : source) {
                seen.add(wf.id());
                try {
                    var result = tx.execute(status -> processFile(site, wf));
                    run.setItemsProcessed(run.getItemsProcessed() + 1);
                    run.setIssuesOpened(run.getIssuesOpened() + result.opened());
                    run.setIssuesClosed(run.getIssuesClosed() + result.closed());
                } catch (RuntimeException e) { // locked, oversized or unreadable: skip and log, never stop the run
                    run.setItemsSkipped(run.getItemsSkipped() + 1);
                    skipped.add(wf.name());
                    log.warn("Skipped file {} in run {}: {}", wf.id(), runId, e.getMessage());
                }
            }
            if (run.getType() != JobType.CHANGE_FEED_CHECK) {
                markVanishedFilesDeleted(site, seen);
            }
            afterRun(site);
            run.setStatus(JobStatus.SUCCEEDED);
        } catch (RuntimeException e) {
            run.setStatus(JobStatus.FAILED);
            skipped.add("FAILED: " + e.getMessage());
            log.error("Check run {} failed", runId, e);
            reportConnectionLostIfDown(site);
        } finally {
            run.setFinishedAt(Instant.now(clock));
            run.setNote(note(skipped));
            runs.save(run);
            audit.log(site.getOrgId(), site.getId(), ActivityActions.CHECK_RUN).by(run.getStartedBy())
                    .on("JobRun", run.getId()).change(null, run.getType() + " " + run.getStatus()
                            + " (" + run.getItemsProcessed() + " files)").save();
        }
    }

    /** One file, in its own transaction: ingest, classify if needed, evaluate the rules. */
    private RuleEngineService.Result processFile(Site site, WorkspaceFile wf) {
        SchoolFile file = fileService.upsertFromWorkspace(site, wf);

        FileClassifier fileClassifier = classifier.getIfAvailable();
        if (fileClassifier != null && needsClassification(file)) {
            fileClassifier.classify(file).ifPresentOrElse(
                    c -> classification.apply(file, c),
                    () -> classification.markUnreadable(file));
        }
        return ruleEngine.evaluateFile(file.getId());
    }

    /** Classify new files and files changed since their profile was made; leave coordinator answers alone. */
    private boolean needsClassification(SchoolFile file) {
        Optional<FileProfile> profile = profiles.findByFileId(file.getId());
        return profile.isEmpty()
                || (file.getModifiedAt() != null && file.getModifiedAt().isAfter(profile.get().getCreatedAt()))
                || labelledByOlderClassifier(profile.get());
    }

    /** A file the local classifier labelled with an older rule set is read again; a coordinator's answer never is. */
    private static boolean labelledByOlderClassifier(FileProfile p) {
        String v = p.getModelVersion();
        return p.getClassifiedBy() == null && v != null && v.startsWith("local-rules-")
                && !v.equals(qa.hafez.hafez.files.integration.LocalFileClassifier.MODEL_VERSION);
    }

    /** A full check lists every file; a live file that is missing from it was deleted in the workspace. */
    private void markVanishedFilesDeleted(Site site, Set<String> seen) {
        for (SchoolFile file : files.findBySiteIdAndTrashedAtIsNull(site.getId())) {
            if (!seen.contains(file.getWorkspaceFileId())) {
                tx.executeWithoutResult(status -> {
                    SchoolFile f = files.findById(file.getId()).orElseThrow();
                    f.setTrashedAt(Instant.now(clock));
                    ruleEngine.evaluateFile(f.getId()); // rule R10 raises its issue here
                });
            }
        }
    }

    private void afterRun(Site site) {
        int threshold = settings.getInt(site.getId(), SettingKeys.BULK_DELETION_THRESHOLD,
                SettingKeys.BULK_DELETION_THRESHOLD_DEFAULT);
        long recent = files.countBySiteIdAndTrashedAtAfter(site.getId(), Instant.now(clock).minus(1, ChronoUnit.HOURS));
        if (recent >= threshold) {
            incidents.raiseBulkDeletion(site.getOrgId(), site.getId(), recent);
        }
        tx.executeWithoutResult(status -> {
            Site s = sites.findById(site.getId()).orElseThrow();
            if (s.getFirstCheckAt() == null) {
                s.setFirstCheckAt(Instant.now(clock));
            }
            scores.snapshotToday(s);
        });
    }

    /** The change feed reads from the start of the last successful check, minus a small safety margin. */
    private Instant changeFeedSince(Site site) {
        return runs.findFirstBySiteIdAndTypeInAndStatusOrderByStartedAtDesc(site.getId(), JobType.CHECKS, JobStatus.SUCCEEDED)
                .map(r -> r.getStartedAt().minus(1, ChronoUnit.MINUTES))
                .orElse(Instant.now(clock).minus(10, ChronoUnit.MINUTES));
    }

    /** "Connection lost over 1 hour": no successful check for an hour raises an incident (proposal 5.4). */
    private void reportConnectionLostIfDown(Site site) {
        Instant hourAgo = Instant.now(clock).minus(1, ChronoUnit.HOURS);
        boolean recentSuccess = runs.findFirstBySiteIdAndTypeInAndStatusOrderByStartedAtDesc(
                site.getId(), JobType.CHECKS, JobStatus.SUCCEEDED).map(r -> r.getStartedAt().isAfter(hourAgo)).orElse(false);
        if (!recentSuccess) {
            incidents.connectionLost(site.getOrgId(), site.getId());
        }
    }

    private String note(List<String> skipped) {
        if (skipped.isEmpty()) {
            return null;
        }
        String joined = String.join("; ", skipped.stream().limit(MAX_SKIPPED_NAMES_IN_NOTE).toList());
        return skipped.size() > MAX_SKIPPED_NAMES_IN_NOTE ? joined + " … and " + (skipped.size() - MAX_SKIPPED_NAMES_IN_NOTE) + " more" : joined;
    }
}
