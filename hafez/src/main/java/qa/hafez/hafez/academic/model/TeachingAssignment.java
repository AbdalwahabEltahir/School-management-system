package qa.hafez.hafez.academic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import qa.hafez.hafez.shared.model.BaseEntity;

/** Which teacher teaches which subject to which section (FR-018). */
@Getter @Setter
@Entity
@Table(name = "teaching_assignments")
public class TeachingAssignment extends BaseEntity {

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;
}
