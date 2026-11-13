package qa.sijill.sijill.pickup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.BaseEntity;

/** A person a parent has approved to collect one child. */
@Getter @Setter
@Entity
@Table(name = "pickup_collectors")
public class PickupCollector extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Relationship relationship;

    @Column(length = 30)
    private String phone;

    /** Only the last four characters of the collector's ID are kept, for the staff check. */
    @Column(name = "id_last4", nullable = false, length = 4)
    private String idLast4;

    /** False once the parent removed the collector; the row stays so the history remains readable. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
