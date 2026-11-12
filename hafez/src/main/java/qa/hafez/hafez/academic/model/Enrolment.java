package qa.hafez.hafez.academic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import qa.hafez.hafez.shared.model.BaseEntity;

/** A student's place in a section; a student is in exactly one section (BR-002). */
@Getter @Setter
@Entity
@Table(name = "enrolments")
public class Enrolment extends BaseEntity {

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 10)
    private Stage stage = Stage.SIJIL;
}
