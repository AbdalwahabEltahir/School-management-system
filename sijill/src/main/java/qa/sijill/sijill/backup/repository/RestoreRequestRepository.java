package qa.sijill.sijill.backup.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.backup.model.RestoreRequest;

public interface RestoreRequestRepository extends JpaRepository<RestoreRequest, UUID> {

    List<RestoreRequest> findBySiteIdOrderByCreatedAtDesc(UUID siteId);
}
