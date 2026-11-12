package qa.hafez.hafez.planning.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.hafez.hafez.planning.model.PlanningSubmission;

public interface PlanningSubmissionRepository extends JpaRepository<PlanningSubmission, UUID> {

    /** Teacher: their own submissions. */
    List<PlanningSubmission> findByTeacherIdOrderByWeekStartDesc(UUID teacherId);

    /** Coordinator: her subject's submissions. */
    List<PlanningSubmission> findBySiteIdAndSubjectIdOrderByWeekStartDesc(UUID siteId, UUID subjectId);

    /** Principal: the whole school (shown as totals; read-only). */
    List<PlanningSubmission> findBySiteIdOrderByWeekStartDesc(UUID siteId);

    /** Finds an existing submission for the same teacher/subject/grade/week so a resubmission updates it. */
    Optional<PlanningSubmission> findByTeacherIdAndSubjectIdAndGradeAndWeekStart(UUID teacherId, UUID subjectId,
                                                                                String grade, LocalDate weekStart);

    List<PlanningSubmission> findBySiteIdAndWeekStart(UUID siteId, LocalDate weekStart);
}
