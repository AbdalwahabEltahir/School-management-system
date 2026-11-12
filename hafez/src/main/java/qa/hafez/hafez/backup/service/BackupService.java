package qa.hafez.hafez.backup.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.organisation.model.Site;
import qa.hafez.hafez.organisation.repository.SiteRepository;
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

/**
 * The backup worker's reporting side and the "Settings › Backup" screen (section 9).
 *
 * <p>The worker itself (hourly copies of changed files, nightly reconciliation, encryption, the second
 * in-country provider) is a separate component that is <b>not built yet</b>. It will call
 * {@link #start} when a run begins and {@link #finish} when it ends; this class stores the runs as
 * {@link JobRun}s so the screen can show status and the last copy.
 */
@Service
@RequiredArgsConstructor
public class BackupService {

    private final JobRunRepository runs;
    private final SiteRepository sites;
    private final AccessPolicy policy;
    private final Clock clock;

    /** Called by the backup worker when a run begins. */
    @Transactional
    public JobRun start(UUID siteId, JobType type) {
        if (!JobType.BACKUPS.contains(type)) {
            throw new IllegalArgumentException(type + " is not a backup job");
        }
        Site site = sites.findById(siteId).orElseThrow(() -> new NotFoundException("The school"));
        JobRun run = new JobRun();
        run.setOrgId(site.getOrgId());
        run.setSiteId(siteId);
        run.setType(type);
        run.setStartedAt(Instant.now(clock));
        run.setCorrelationId(CorrelationIds.current());
        return runs.save(run);
    }

    /** Called by the backup worker when a run ends. {@code verified} means every copy matched its checksum. */
    @Transactional
    public JobRun finish(UUID runId, int filesCopied, boolean verified, boolean succeeded, String note) {
        JobRun run = runs.findById(runId).orElseThrow(() -> new NotFoundException("The backup run"));
        run.setItemsProcessed(filesCopied);
        run.setVerified(verified);
        run.setStatus(succeeded ? JobStatus.SUCCEEDED : JobStatus.FAILED);
        run.setFinishedAt(Instant.now(clock));
        run.setNote(note);
        return run;
    }

    /** Recent backup runs of a school: School IT and principal of that school, or Leadership for any. */
    @Transactional(readOnly = true)
    public List<JobRun> recent(CurrentUser user, UUID siteId) {
        if (!user.isAny(Role.SCHOOL_IT, Role.PRINCIPAL, Role.LEADERSHIP, Role.EAA_ADMIN)) {
            throw new ForbiddenException("Backup status is not available for your role.");
        }
        Site site = sites.findById(siteId).orElseThrow(() -> new NotFoundException("The school"));
        policy.requireSiteAccess(user, siteId, site.getOrgId());
        return runs.findTop20BySiteIdAndTypeInOrderByStartedAtDesc(siteId, JobType.BACKUPS);
    }
}
