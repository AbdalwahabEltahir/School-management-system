package qa.sijill.sijill.support.model;

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

/** A request from a member of staff to their School IT, or from a principal to Leadership. {@code siteId} is the school that opened it. */
@Getter @Setter
@Entity
@Table(name = "support_tickets")
public class SupportTicket extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TicketUrgency urgency;

    /** Who the ticket is written to. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TicketAudience audience = TicketAudience.SCHOOL_IT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TicketStatus status = TicketStatus.OPEN;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_by_name", nullable = false, length = 200)
    private String createdByName;

    /** The EAA administrator handling it, if one was assigned. */
    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "assigned_to_name", length = 200)
    private String assignedToName;

    /** Name of the optional attachment; its bytes live in {@link TicketAttachment}. */
    @Column(name = "attachment_name", length = 200)
    private String attachmentName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;
}
