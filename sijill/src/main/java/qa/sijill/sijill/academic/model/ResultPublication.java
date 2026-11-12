package qa.sijill.sijill.academic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import qa.sijill.sijill.shared.model.BaseEntity;

/** A section's term results made visible to its students (FR-033, BR-007). */
@Getter @Setter
@Entity
@Table(name = "result_publications")
public class ResultPublication extends BaseEntity {

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "term", nullable = false)
    private int term;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by", nullable = false)
    private UUID publishedBy;
}
