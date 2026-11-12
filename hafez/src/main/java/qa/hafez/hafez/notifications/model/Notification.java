package qa.hafez.hafez.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.hafez.hafez.shared.model.BaseEntity;
import qa.hafez.hafez.shared.model.NotificationKind;

/** An in-app notification: one entry behind the header bell with its unread count (FR-11.2). */
@Getter @Setter
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationKind kind;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 1000)
    private String body;

    /** Related issue, if any, so the UI can link to it. */
    @Column(name = "issue_id")
    private UUID issueId;

    /** Null while unread. */
    @Column(name = "read_at")
    private Instant readAt;
}
