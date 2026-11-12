package qa.sijill.sijill.issues.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.BaseEntity;

/** One step in an issue's history ("issue_events", FR-5.1). Event time is {@link #getCreatedAt()}. */
@Getter @Setter
@Entity
@Table(name = "issue_events")
public class IssueEvent extends BaseEntity {

    @Column(name = "issue_id", nullable = false)
    private UUID issueId;

    /** Who did it; null when Hafez itself did it (a check, a reminder job). */
    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private IssueEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private IssueStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 30)
    private IssueStatus toStatus;

    @Column(length = 1000)
    private String note;
}
