package qa.sijill.sijill.academic.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.academic.model.PointEntry;
import qa.sijill.sijill.academic.model.PointSource;

public interface PointEntryRepository extends JpaRepository<PointEntry, UUID> {

    List<PointEntry> findByStudentIdOrderByAwardedOnDescCreatedAtDesc(UUID studentId);

    List<PointEntry> findBySiteId(UUID siteId);

    Optional<PointEntry> findByStudentIdAndAwardedOnAndSource(UUID studentId, LocalDate awardedOn, PointSource source);
}
