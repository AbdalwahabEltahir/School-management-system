package qa.hafez.hafez.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import qa.hafez.hafez.academic.model.Assessment;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    List<Assessment> findBySiteIdOrderByTermAscNameAsc(UUID siteId);
    List<Assessment> findBySectionIdOrderByTermAscNameAsc(UUID sectionId);
    List<Assessment> findBySectionIdAndTerm(UUID sectionId, int term);
}
