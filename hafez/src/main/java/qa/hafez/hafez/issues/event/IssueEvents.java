package qa.hafez.hafez.issues.event;

import java.time.LocalDate;
import java.util.UUID;

import qa.hafez.hafez.issues.model.IncidentType;

/**
 * Domain events published by the {@code issues} module. Listeners decide what to do about them, so the
 * issue state machine never calls notifications or other modules directly:
 * <ul>
 *   <li>{@code notifications}: writes the message / bell entry for every event here;</li>
 *   <li>{@code issues} ({@code IncidentService}): raises a major incident for {@link Opened} with a hint;</li>
 *   <li>{@code audit}: does <i>not</i> listen here; auditing uses the separate {@code AuditEvent}.</li>
 * </ul>
 * Events are delivered synchronously inside the publisher's transaction, so a notification and the change
 * that caused it succeed or fail together. Events carry ids, not entities, so listeners load fresh data.
 */
public final class IssueEvents {

    private IssueEvents() {}

    /**
     * An issue was opened, or an uncertain one became certain.
     *
     * @param needsReview  true when only the coordinator is told (uncertain file or no owner)
     * @param incidentHint a major-incident type this issue amounts to (exam leak, important deletion); may be null
     */
    public record Opened(UUID issueId, boolean needsReview, IncidentType incidentHint) {}

    /** The owner's reminder is due (FR-1.5). */
    public record ReminderDue(UUID issueId) {}

    /** The issue is still open a school day after the reminder: the coordinator should follow up. */
    public record NeedsFollowUp(UUID issueId) {}

    /** "I've fixed it" was pressed but the re-check still finds the problem (FR-2.6). */
    public record StillBroken(UUID issueId) {}

    /** The owner disagreed or asked for help: tell the coordinator. */
    public record OwnerResponded(UUID issueId, String titleAr, String titleEn) {}

    /** The coordinator decided something: tell the owner. */
    public record DecisionMade(UUID issueId, String titleAr, String titleEn) {}

    /** An exception was allowed for the owner's file until a date. */
    public record ExceptionGranted(UUID issueId, LocalDate endsOn) {}

    /** The owner left; the issue moved to the coordinator. */
    public record Reassigned(UUID issueId) {}

    /** A major incident was raised: owner, coordinator, principal and leadership are alerted. */
    public record IncidentRaised(UUID incidentId) {}
}
