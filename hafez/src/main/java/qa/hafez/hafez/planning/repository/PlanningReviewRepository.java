package qa.hafez.hafez.planning.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.hafez.hafez.planning.model.PlanningReview;

public interface PlanningReviewRepository extends JpaRepository<PlanningReview, UUID> {

    /** A submission's review history, oldest first; resubmissions keep their history (FR-9.2). */
    List<PlanningReview> findBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);
}
