package qa.sijill.sijill.backup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.BaseEntity;

/**
 * A request to restore a deleted file, subject drive or whole school (FR-13.3, section 9.4).
 *
 * <p>Sijill only <i>tracks</i> the request: School IT restores from the trash or backup vault with the
 * workspace's own tools, so Sijill itself stays read-only. A whole drive or school needs two different
 * approvers ({@link #firstApproverId} and {@link #secondApproverId}).
 */
@Getter @Setter
@Entity
@Table(name = "restore_requests")
public class RestoreRequest extends BaseEntity {

    /** The file to restore, when {@link #scope} is FILE. */
    @Column(name = "file_id")
    private UUID fileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestoreScope scope;

    /** Human description of the target, e.g. "Mathematics subject drive". */
    @Column(nullable = false, length = 500)
    private String target;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestoreStatus status = RestoreStatus.REQUESTED;

    @Column(name = "first_approver_id")
    private UUID firstApproverId;

    @Column(name = "second_approver_id")
    private UUID secondApproverId;

    @Column(name = "restored_at")
    private Instant restoredAt;
}
