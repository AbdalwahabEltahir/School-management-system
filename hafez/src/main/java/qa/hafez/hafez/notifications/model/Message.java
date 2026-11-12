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
import qa.hafez.hafez.shared.model.Language;

/**
 * A message to a person about an issue, logged before and after sending (proposal 5.4 and FR-11.8:
 * "messages are real logged e-mails"). Every message says what is wrong, why, how to fix it, and that
 * Hafez changed nothing (wording rules, 10.3).
 *
 * <p>{@link #deliverAt} implements quiet hours: non-urgent messages created at night or on the weekend
 * are held until the next school morning. {@code MessageDispatchService} sends due messages.
 */
@Getter @Setter
@Entity
@Table(name = "messages")
public class Message extends BaseEntity {

    @Column(name = "issue_id")
    private UUID issueId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    /** The coordinator copied on urgent issues; null otherwise. */
    @Column(name = "copy_to_id")
    private UUID copyToId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Language language = Language.AR;

    @Column(nullable = false, length = 300)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String body;

    /** Earliest time to send (quiet hours already applied). */
    @Column(name = "deliver_at", nullable = false)
    private Instant deliverAt = Instant.now();

    /** Null until sent. */
    @Column(name = "sent_at")
    private Instant sentAt;
}
