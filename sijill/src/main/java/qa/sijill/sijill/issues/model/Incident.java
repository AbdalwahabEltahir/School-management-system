package qa.sijill.sijill.issues.model;

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

/**
 * A major incident: the only thing leadership is alerted about (section 2 and 5.4) — a leaked exam,
 * an important deletion, a burst of deletions, an urgent issue left open, or a lost connection.
 */
@Getter @Setter
@Entity
@Table(name = "incidents")
public class Incident extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentType type;

    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "issue_id")
    private UUID issueId;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    /** When leadership was alerted. */
    @Column(name = "leadership_alerted_at")
    private Instant leadershipAlertedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
