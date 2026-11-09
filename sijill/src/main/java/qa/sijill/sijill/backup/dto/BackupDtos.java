package qa.sijill.sijill.backup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

import qa.sijill.sijill.backup.model.RestoreRequest;
import qa.sijill.sijill.backup.model.RestoreScope;
import qa.sijill.sijill.backup.service.RestoreService;

/** Request and response shapes of the backup and restore API. */
public final class BackupDtos {

    private BackupDtos() {}

    public record RestoreView(UUID id, UUID fileId, String scope, String target, String reason, UUID requestedBy,
                              String status, UUID firstApproverId, UUID secondApproverId, Instant restoredAt,
                              Instant requestedAt) {
        public static RestoreView from(RestoreRequest r) {
            return new RestoreView(r.getId(), r.getFileId(), r.getScope().name(), r.getTarget(), r.getReason(),
                    r.getRequestedBy(), r.getStatus().name(), r.getFirstApproverId(), r.getSecondApproverId(),
                    r.getRestoredAt(), r.getCreatedAt());
        }
    }

    /** Ask for a restore. {@code fileId} is for a single file; {@code target} names a drive. */
    public record RestoreRequestBody(UUID fileId, @NotNull RestoreScope scope, String target, @NotBlank String reason) {
        public RestoreService.RequestCommand toCommand() {
            return new RestoreService.RequestCommand(fileId, scope, target, reason);
        }
    }
}
