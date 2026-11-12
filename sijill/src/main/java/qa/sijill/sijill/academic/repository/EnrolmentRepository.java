package qa.sijill.sijill.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import qa.sijill.sijill.academic.model.Enrolment;

public interface EnrolmentRepository extends JpaRepository<Enrolment, UUID> {

    List<Enrolment> findBySectionId(UUID sectionId);
    Optional<Enrolment> findByStudentId(UUID studentId);
    List<Enrolment> findBySiteId(UUID siteId);
}
