package qa.sijill.sijill.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One execution of a background job for a school: a check run ("Recent checks with skipped files",
 * FR-8.2) or a backup run ("Settings › Backup: status and last copy", section 9).
 *
 * <p>Check runs and backup runs have the same lifecycle (started → finished/failed, counters, a note),
 * so they share one table instead of two near-identical ones. {@link #getCorrelationId()} links the run
 * to every log line it produced.
 */
@Getter @Setter
@Entity
@Table(name = "job_runs")
public class JobRun extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.RUNNING;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** The person who started it; null for scheduled runs. */
    @Column(name = "started_by")
    private UUID startedBy;

    /** Files read (check) or files copied (backup). */
    @Column(name = "items_processed", nullable = false)
    private int itemsProcessed;

    /** Locked, oversized or unreadable files that were skipped and logged. */
    @Column(name = "items_skipped", nullable = false)
    private int itemsSkipped;

    @Column(name = "issues_opened", nullable = false)
    private int issuesOpened;

    @Column(name = "issues_closed", nullable = false)
    private int issuesClosed;

    /** Backup only: true once every copy was verified by checksum. */
    @Column(nullable = false)
    private boolean verified;

    /** Correlation ID of the run: search the logs for it to follow the whole run. */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    /** Short note: the failure reason, or what was skipped. */
    @Column(length = 4000)
    private String note;
}
