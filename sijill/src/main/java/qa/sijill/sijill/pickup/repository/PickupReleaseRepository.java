package qa.sijill.sijill.pickup.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.pickup.model.PickupRelease;

public interface PickupReleaseRepository extends JpaRepository<PickupRelease, UUID> {

    List<PickupRelease> findTop500BySiteIdAndReleasedAtBetweenOrderByReleasedAtDesc(UUID siteId, Instant from, Instant to);

    List<PickupRelease> findTop200ByStudentIdOrderByReleasedAtDesc(UUID studentId);
}
