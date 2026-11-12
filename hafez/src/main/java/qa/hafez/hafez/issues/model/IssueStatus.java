package qa.hafez.hafez.issues.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Issue statuses (FR-2.1 open, FR-2.2 closed).
 *
 * <p>Only a re-check moves an issue to {@link #FIXED} (design principle 5).
 */
public enum IssueStatus {
    // ---- open ----
    OWNER_NOTIFIED,
    REMINDER_SENT,
    NEEDS_FOLLOW_UP,
    /** Uncertain file: goes to the coordinator, never to the owner as a guess (principle 4). */
    NEEDS_REVIEW,
    OWNER_DISAGREES,
    OWNER_ASKED_FOR_HELP,
    // ---- closed ----
    FIXED,
    ALLOWED_EXCEPTION,
    RULE_PAUSED,
    CLOSED_RULE_REMOVED;

    /** All open statuses; pass to repository {@code StatusIn} queries. */
    public static final Set<IssueStatus> OPEN = EnumSet.of(
            OWNER_NOTIFIED, REMINDER_SENT, NEEDS_FOLLOW_UP, NEEDS_REVIEW, OWNER_DISAGREES, OWNER_ASKED_FOR_HELP);

    /** Open statuses that wait for the coordinator to act (the "Needs me" filter, FR-11.4). */
    public static final Set<IssueStatus> NEEDS_COORDINATOR = EnumSet.of(
            NEEDS_REVIEW, OWNER_ASKED_FOR_HELP, OWNER_DISAGREES, NEEDS_FOLLOW_UP);

    /** Closed statuses (everything that is not open). */
    public static final Set<IssueStatus> CLOSED = EnumSet.complementOf(EnumSet.copyOf(OPEN));

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
