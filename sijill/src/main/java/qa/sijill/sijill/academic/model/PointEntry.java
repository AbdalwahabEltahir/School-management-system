package qa.sijill.sijill.academic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.BaseEntity;

/** Points a student earned, with the reason: one for each day present, or a teacher's recognition. */
@Getter @Setter
@Entity
@Table(name = "point_entries")
public class PointEntry extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "reason", nullable = false, length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 12)
    private PointSource source;

    @Column(name = "awarded_by")
    private UUID awardedBy;

    @Column(name = "awarded_on", nullable = false)
    private LocalDate awardedOn;
}
