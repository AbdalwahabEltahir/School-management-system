package qa.sijill.sijill.academic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import qa.sijill.sijill.shared.model.BaseEntity;

/** A link a teacher shares with a section for one subject (FR-037). Hafez never copies the file. */
@Getter @Setter
@Entity
@Table(name = "study_materials")
public class StudyMaterial extends BaseEntity {

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "link", nullable = false, length = 1000)
    private String link;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
