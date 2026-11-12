package qa.hafez.hafez.academic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import qa.hafez.hafez.shared.model.BaseEntity;

/** A test or piece of work in one term for one section and subject, with its maximum mark (FR-028). */
@Getter @Setter
@Entity
@Table(name = "assessments")
public class Assessment extends BaseEntity {

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "term", nullable = false)
    private int term;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "max_mark", nullable = false)
    private BigDecimal maxMark;

    @Column(name = "submitted_at", nullable = true)
    private Instant submittedAt;
}
