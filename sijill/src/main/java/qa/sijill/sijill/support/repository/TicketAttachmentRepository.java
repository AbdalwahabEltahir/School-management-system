package qa.sijill.sijill.support.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.support.model.TicketAttachment;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {
}
