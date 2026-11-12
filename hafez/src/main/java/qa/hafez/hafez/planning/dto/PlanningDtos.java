package qa.hafez.hafez.planning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import qa.hafez.hafez.planning.model.PlanningReview;
import qa.hafez.hafez.planning.model.PlanningSubmission;
import qa.hafez.hafez.planning.model.ReviewDecision;
import qa.hafez.hafez.planning.service.PlanningService;

/** Request and response shapes of the planning-review API. */
public final class PlanningDtos {

    private PlanningDtos() {}

    public record SubmissionView(UUID id, UUID teacherId, UUID subjectId, String grade, LocalDate weekStart,
                                 String planType, String driveLink, String status, Instant submittedAt,
                                 int resubmissionCount) {
        public static SubmissionView from(PlanningSubmission p) {
            return new SubmissionView(p.getId(), p.getTeacherId(), p.getSubjectId(), p.getGrade(), p.getWeekStart(),
                    p.getPlanType(), p.getDriveLink(), p.getStatus().name(), p.getSubmittedAt(), p.getResubmissionCount());
        }
    }

    public record ReviewView(UUID id, UUID reviewerId, String decision, String reason, Instant at) {
        public static ReviewView from(PlanningReview r) {
            return new ReviewView(r.getId(), r.getReviewerId(), r.getDecision().name(), r.getReason(), r.getCreatedAt());
        }
    }

    public record TotalsView(long submitted, long returned, long approved) {
        public static TotalsView from(PlanningService.Totals t) {
            return new TotalsView(t.submitted(), t.returned(), t.approved());
        }
    }

    /** A teacher submits a plan: subject, grade, week, type and a drive link (the file is never copied). */
    public record SubmitRequest(@NotNull UUID subjectId, @NotBlank String grade, @NotNull LocalDate weekStart,
                                @NotBlank String planType, @NotBlank String driveLink) {
        public PlanningService.SubmitCommand toCommand() {
            return new PlanningService.SubmitCommand(subjectId, grade, weekStart, planType, driveLink);
        }
    }

    /** The coordinator's decision; a reason is mandatory when returning. */
    public record ReviewRequest(@NotNull ReviewDecision decision, String reason) {}
}
