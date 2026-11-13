package qa.sijill.sijill.pickup.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.pickup.model.PickupCollector;

public interface PickupCollectorRepository extends JpaRepository<PickupCollector, UUID> {

    List<PickupCollector> findByStudentIdAndActiveTrueOrderByName(UUID studentId);
}
