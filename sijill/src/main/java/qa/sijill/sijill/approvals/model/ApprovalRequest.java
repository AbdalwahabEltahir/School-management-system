package qa.sijill.sijill.approvals.model;

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

/** One sensitive action waiting for (or already given) a second person's decision. */
@Getter @Setter
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest extends BaseEntity {

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "target_id")
    private UUID targetId;

    /** Small free-form detail the handler needs (for a role change: the new role). */
    @Column(name = "payload", length = 1000)
    private String payload;

    /** One plain sentence shown to the approver. */
    @Column(name = "summary", nullable = false, length = 300)
    private String summary;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_by_name", nullable = false, length = 200)
    private String requestedByName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_by_name", length = 200)
    private String decidedByName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;
}
