package qa.sijill.sijill.planning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.BaseEntity;

/** A coordinator's decision on a {@link PlanningSubmission} (FR-9.2). A return always carries a reason. */
@Getter @Setter
@Entity
@Table(name = "planning_reviews")
public class PlanningReview extends BaseEntity {

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewDecision decision;

    @Column(length = 1000)
    private String reason;
}
