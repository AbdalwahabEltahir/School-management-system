package qa.sijill.sijill.support.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.support.model.TicketReply;

public interface TicketReplyRepository extends JpaRepository<TicketReply, UUID> {

    List<TicketReply> findByTicketIdOrderByCreatedAt(UUID ticketId);
}
