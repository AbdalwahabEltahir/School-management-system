package qa.hafez.hafez.notifications.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.hafez.hafez.notifications.model.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Messages that are due but not sent yet (the dispatcher's work queue). */
    List<Message> findBySentAtIsNullAndDeliverAtLessThanEqual(Instant now);

    /** Messages about an issue, oldest first (the message screen, FR-11.3). */
    List<Message> findByIssueIdOrderByCreatedAtAsc(UUID issueId);
}
