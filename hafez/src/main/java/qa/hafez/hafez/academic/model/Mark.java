package qa.hafez.hafez.academic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import qa.hafez.hafez.shared.model.BaseEntity;

/** A student's mark for an assessment: a number from 0 to the maximum, or absent (BR-005). */
@Getter @Setter
@Entity
@Table(name = "marks")
public class Mark extends BaseEntity {

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "mark_value", nullable = true)
    private BigDecimal value;

    @Column(name = "absent", nullable = false)
    private boolean absent = false;
}
