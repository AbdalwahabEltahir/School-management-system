package qa.sijill.sijill.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import qa.sijill.sijill.academic.model.TeachingAssignment;

public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, UUID> {

    List<TeachingAssignment> findBySectionId(UUID sectionId);
    List<TeachingAssignment> findByTeacherId(UUID teacherId);
    List<TeachingAssignment> findBySiteId(UUID siteId);
    Optional<TeachingAssignment> findBySectionIdAndSubjectId(UUID sectionId, UUID subjectId);
    boolean existsByTeacherIdAndSectionId(UUID teacherId, UUID sectionId);
    boolean existsByTeacherIdAndSectionIdAndSubjectId(UUID teacherId, UUID sectionId, UUID subjectId);
}
