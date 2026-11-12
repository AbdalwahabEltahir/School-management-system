package qa.hafez.hafez.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import qa.hafez.hafez.academic.model.Section;

public interface SectionRepository extends JpaRepository<Section, UUID> {

    List<Section> findBySiteIdOrderByGradeAscNameAsc(UUID siteId);
    Optional<Section> findBySiteIdAndGradeAndName(UUID siteId, int grade, String name);
}
