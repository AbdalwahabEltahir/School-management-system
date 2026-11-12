package qa.hafez.hafez.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import qa.hafez.hafez.academic.model.StudyMaterial;

public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, UUID> {

    List<StudyMaterial> findBySectionIdOrderByCreatedAtDesc(UUID sectionId);
    List<StudyMaterial> findBySiteIdOrderByCreatedAtDesc(UUID siteId);
}
