package qa.hafez.hafez.organisation.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.hafez.hafez.organisation.model.Subject;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    List<Subject> findBySiteIdOrderByName(UUID siteId);
}
