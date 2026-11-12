package qa.hafez.hafez.dashboard.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.hafez.hafez.dashboard.model.HealthScoreSnapshot;

public interface HealthScoreRepository extends JpaRepository<HealthScoreSnapshot, UUID> {

    Optional<HealthScoreSnapshot> findBySiteIdAndScoreDate(UUID siteId, LocalDate scoreDate);

    /** Daily trend bars: snapshots after a date, oldest first. */
    List<HealthScoreSnapshot> findBySiteIdAndScoreDateAfterOrderByScoreDateAsc(UUID siteId, LocalDate after);
}
