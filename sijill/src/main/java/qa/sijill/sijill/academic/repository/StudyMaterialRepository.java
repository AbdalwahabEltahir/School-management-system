package qa.sijill.sijill.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import qa.sijill.sijill.academic.model.StudyMaterial;

public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, UUID> {

    List<StudyMaterial> findBySectionIdOrderByCreatedAtDesc(UUID sectionId);
    List<StudyMaterial> findBySiteIdOrderByCreatedAtDesc(UUID siteId);
}
