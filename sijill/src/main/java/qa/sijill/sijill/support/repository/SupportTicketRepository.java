package qa.sijill.sijill.support.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.support.model.SupportTicket;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    List<SupportTicket> findTop500ByOrgIdOrderByUpdatedAtDesc(UUID orgId);

    List<SupportTicket> findTop200BySiteIdOrderByUpdatedAtDesc(UUID siteId);

    List<SupportTicket> findTop200ByCreatedByOrderByUpdatedAtDesc(UUID createdBy);
}
