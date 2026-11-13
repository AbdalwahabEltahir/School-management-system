package qa.sijill.sijill.pickup.model;

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

/** One arrival, from "I have arrived" to release, decline or cancel. */
@Getter @Setter
@Entity
@Table(name = "pickup_requests")
public class PickupRequest extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "student_name", nullable = false, length = 200)
    private String studentName;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "section_label", nullable = false, length = 40)
    private String sectionLabel;

    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    /** Null when the parent collects the child themselves. */
    @Column(name = "collector_id")
    private UUID collectorId;

    @Column(name = "collector_name", nullable = false, length = 200)
    private String collectorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "collector_relationship", nullable = false, length = 12)
    private Relationship collectorRelationship;

    @Column(name = "collector_id_last4", length = 4)
    private String collectorIdLast4;

    @Column(name = "collector_phone", length = 30)
    private String collectorPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PickupStatus status = PickupStatus.ARRIVED;

    @Column(name = "arrived_at", nullable = false)
    private Instant arrivedAt = Instant.now();

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_by_name", length = 200)
    private String verifiedByName;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_by_name", length = 200)
    private String decidedByName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decline_reason", length = 300)
    private String declineReason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
