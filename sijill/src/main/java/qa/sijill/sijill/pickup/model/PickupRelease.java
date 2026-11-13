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

/** The chain-of-custody log: who released which child to whom, and exactly when. Rows are never changed (a database rule enforces it). */
@Getter @Setter
@Entity
@Table(name = "pickup_release_log")
public class PickupRelease extends BaseEntity {

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "student_name", nullable = false, length = 200)
    private String studentName;

    @Column(name = "section_label", nullable = false, length = 40)
    private String sectionLabel;

    @Column(name = "collector_name", nullable = false, length = 200)
    private String collectorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "collector_relationship", nullable = false, length = 12)
    private Relationship collectorRelationship;

    @Column(name = "released_by", nullable = false)
    private UUID releasedBy;

    @Column(name = "released_by_name", nullable = false, length = 200)
    private String releasedByName;

    @Column(name = "released_at", nullable = false)
    private Instant releasedAt;
}
