package qa.sijill.sijill.issues.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import qa.sijill.sijill.issues.model.ExceptionGrant;
import qa.sijill.sijill.issues.model.Incident;
import qa.sijill.sijill.issues.model.Issue;
import qa.sijill.sijill.issues.model.IssueEvent;
import qa.sijill.sijill.issues.service.IssueService;
import qa.sijill.sijill.issues.service.IssueWorkflowService;

/**
 * Request and response shapes of the issues API. Responses are built from entities with the {@code from}
 * methods, so no entity ever leaves the service layer and lazy-loading cannot leak into JSON.
 */
public final class IssueDtos {

    private IssueDtos() {}

    // ------------------------------------------------------------------ responses

    /** One issue. File name and rule text are fetched separately (file details, the rules list). */
    public record IssueView(UUID id, UUID fileId, UUID ruleId, UUID ownerId, UUID subjectId, String status,
                            String severity, String detailEn, String detailAr, Instant openedAt, Instant closedAt,
                            String ownerNote) {
        public static IssueView from(Issue i) {
            return new IssueView(i.getId(), i.getFileId(), i.getRuleId(), i.getOwnerId(), i.getSubjectId(),
                    i.getStatus().name(), i.getSeverity().name(), i.getDetailEn(), i.getDetailAr(), i.getOpenedAt(),
                    i.getClosedAt(), i.getOwnerNote());
        }
    }

    /** One step of an issue's history. */
    public record EventView(UUID id, String type, String fromStatus, String toStatus, UUID actorId, String note,
                            Instant at) {
        public static EventView from(IssueEvent e) {
            return new EventView(e.getId(), e.getEventType().name(),
                    e.getFromStatus() == null ? null : e.getFromStatus().name(),
                    e.getToStatus() == null ? null : e.getToStatus().name(), e.getActorId(), e.getNote(), e.getCreatedAt());
        }
    }

    /** Result of "I've fixed it": closed, or still broken with what is still wrong. */
    public record FixView(boolean fixed, String stillWrongEn, String stillWrongAr) {
        public static FixView from(IssueWorkflowService.FixResult r) {
            return new FixView(r.fixed(), r.stillWrongEn(), r.stillWrongAr());
        }
    }

    public record ExceptionView(UUID id, UUID fileId, UUID ruleId, UUID issueId, String reason, UUID grantedBy,
                                LocalDate endsOn, Instant endedAt) {
        public static ExceptionView from(ExceptionGrant g) {
            return new ExceptionView(g.getId(), g.getFileId(), g.getRuleId(), g.getIssueId(), g.getReason(),
                    g.getGrantedBy(), g.getEndsOn(), g.getEndedAt());
        }
    }

    public record CountsView(long open, long urgent, long needFollowUp, long fixed) {
        public static CountsView from(IssueService.Counts c) {
            return new CountsView(c.open(), c.urgent(), c.needFollowUp(), c.fixed());
        }
    }

    public record IncidentView(UUID id, String type, UUID fileId, UUID issueId, String description, Instant detectedAt,
                               Instant resolvedAt) {
        public static IncidentView from(Incident i) {
            return new IncidentView(i.getId(), i.getType().name(), i.getFileId(), i.getIssueId(), i.getDescription(),
                    i.getDetectedAt(), i.getResolvedAt());
        }
    }

    // ------------------------------------------------------------------ requests

    /** "This looks wrong": the note is mandatory (FR-11.4). */
    public record DisagreeRequest(@NotBlank String note) {}

    /** A coordinator's follow-up note (optional). */
    public record FollowUpRequest(String note) {}

    /** Allow an exception: a reason and an end date are mandatory (FR-2.5). */
    public record ExceptionRequest(@NotBlank String reason, @NotNull LocalDate endsOn) {}
}
