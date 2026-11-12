package qa.hafez.hafez.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import qa.hafez.hafez.academic.model.Enrolment;

public interface EnrolmentRepository extends JpaRepository<Enrolment, UUID> {

    List<Enrolment> findBySectionId(UUID sectionId);
    Optional<Enrolment> findByStudentId(UUID studentId);
    List<Enrolment> findBySiteId(UUID siteId);
}
