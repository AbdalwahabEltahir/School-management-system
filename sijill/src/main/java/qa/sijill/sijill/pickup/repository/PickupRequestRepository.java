package qa.sijill.sijill.pickup.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.pickup.model.PickupRequest;
import qa.sijill.sijill.pickup.model.PickupStatus;

public interface PickupRequestRepository extends JpaRepository<PickupRequest, UUID> {

    List<PickupRequest> findBySiteIdAndStatusInOrderByArrivedAt(UUID siteId, Collection<PickupStatus> statuses);

    List<PickupRequest> findBySiteIdAndStatusInAndUpdatedAtAfterOrderByUpdatedAtDesc(UUID siteId, Collection<PickupStatus> statuses, Instant after);

    Optional<PickupRequest> findFirstByStudentIdAndStatusIn(UUID studentId, Collection<PickupStatus> statuses);

    Optional<PickupRequest> findFirstByStudentIdOrderByArrivedAtDesc(UUID studentId);
}
