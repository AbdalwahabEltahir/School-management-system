package qa.sijill.sijill.audit.dto;

import java.time.Instant;
import java.util.UUID;

import qa.sijill.sijill.audit.model.ActivityLogEntry;
import qa.sijill.sijill.audit.service.ActivityLogService;

/** Response shapes of the activity-log API. */
public final class AuditDtos {

    private AuditDtos() {}

    public record LogEntryView(long seq, Instant at, UUID actorId, String action, String entityType, UUID entityId,
                               String before, String after) {
        public static LogEntryView from(ActivityLogEntry e) {
            return new LogEntryView(e.getSeq(), e.getCreatedAt(), e.getActorId(), e.getAction(), e.getEntityType(),
                    e.getEntityId(), e.getBeforeState(), e.getAfterState());
        }
    }

    /** Result of verifying the hash chain: intact, or the first broken sequence number. */
    public record VerificationView(boolean valid, long entriesChecked, Long firstBrokenSeq) {
        public static VerificationView from(ActivityLogService.ChainVerification v) {
            return new VerificationView(v.valid(), v.entriesChecked(), v.firstBrokenSeq());
        }
    }
}
