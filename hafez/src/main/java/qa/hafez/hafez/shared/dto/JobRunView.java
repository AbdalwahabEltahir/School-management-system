package qa.hafez.hafez.shared.dto;

import java.time.Instant;
import java.util.UUID;

import qa.hafez.hafez.shared.model.JobRun;

/** A check or backup run as the API returns it (never the entity). */
public record JobRunView(UUID id, UUID siteId, String type, String status, Instant startedAt, Instant finishedAt,
                         int itemsProcessed, int itemsSkipped, int issuesOpened, int issuesClosed, boolean verified,
                         String correlationId, String note) {

    public static JobRunView from(JobRun r) {
        return new JobRunView(r.getId(), r.getSiteId(), r.getType().name(), r.getStatus().name(), r.getStartedAt(),
                r.getFinishedAt(), r.getItemsProcessed(), r.getItemsSkipped(), r.getIssuesOpened(), r.getIssuesClosed(),
                r.isVerified(), r.getCorrelationId(), r.getNote());
    }
}
