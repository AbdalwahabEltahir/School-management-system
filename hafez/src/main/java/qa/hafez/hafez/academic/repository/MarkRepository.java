package qa.hafez.hafez.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import qa.hafez.hafez.academic.model.Mark;

public interface MarkRepository extends JpaRepository<Mark, UUID> {

    List<Mark> findByAssessmentId(UUID assessmentId);
    List<Mark> findByStudentId(UUID studentId);
}
