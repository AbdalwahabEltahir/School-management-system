package qa.sijill.sijill.support.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.BaseEntity;

/** One message on a ticket, from the school or from head office. */
@Getter @Setter
@Entity
@Table(name = "support_ticket_replies")
public class TicketReply extends BaseEntity {

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "author_name", nullable = false, length = 200)
    private String authorName;

    @Column(name = "author_role", nullable = false, length = 20)
    private String authorRole;

    @Column(nullable = false, length = 4000)
    private String body;
}
