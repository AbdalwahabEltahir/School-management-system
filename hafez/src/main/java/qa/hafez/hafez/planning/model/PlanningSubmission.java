package qa.hafez.hafez.planning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.hafez.hafez.shared.model.BaseEntity;

/**
 * A teacher's weekly planning file submitted for the coordinator's review (section 5.9).
 * The teacher submits subject, grade, week, type and a <b>drive link</b>; Hafez never copies the file.
 * Reviews are kept in {@link PlanningReview}, so resubmissions keep their history.
 */
@Getter @Setter
@Entity
@Table(name = "planning_submissions")
public class PlanningSubmission extends BaseEntity {

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(nullable = false, length = 20)
    private String grade;

    /** First day of the week the plan is for. */
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    /** Kind of plan, e.g. "Weekly plan", "Lesson preparation". */
    @Column(name = "plan_type", nullable = false, length = 60)
    private String planType;

    @Column(name = "drive_link", nullable = false, length = 1000)
    private String driveLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanningStatus status = PlanningStatus.SUBMITTED;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    /** How many times the teacher resubmitted after a return. */
    @Column(name = "resubmission_count", nullable = false)
    private int resubmissionCount;
}
