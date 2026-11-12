package qa.hafez.hafez.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import qa.hafez.hafez.academic.model.ResultPublication;

public interface ResultPublicationRepository extends JpaRepository<ResultPublication, UUID> {

    Optional<ResultPublication> findBySectionIdAndTerm(UUID sectionId, int term);
    List<ResultPublication> findBySectionId(UUID sectionId);
}
