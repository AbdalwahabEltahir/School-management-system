package qa.sijill.sijill.support.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** The bytes of a ticket's attachment, kept apart so listing tickets never loads them. */
@Getter @Setter
@Entity
@Table(name = "support_ticket_attachments")
public class TicketAttachment {

    @Id
    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "data", nullable = false)
    private byte[] data;
}
