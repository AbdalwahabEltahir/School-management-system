package qa.sijill.sijill.organisation.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.organisation.model.Subject;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    List<Subject> findBySiteIdOrderByName(UUID siteId);
}
