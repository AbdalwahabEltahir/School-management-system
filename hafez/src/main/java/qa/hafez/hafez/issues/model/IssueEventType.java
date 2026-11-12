package qa.hafez.hafez.issues.model;

/** What happened to an issue. Every change is stored as an event so the file history is complete (FR-5.1). */
public enum IssueEventType {
    OPENED,
    STATUS_CHANGED,
    REMINDER_SENT,
    FOLLOW_UP_RECORDED,
    /** The owner pressed "I've fixed it" but the re-check still found the problem (FR-2.6). */
    STILL_BROKEN,
    NOTE,
    /** The coordinator kept a rule after the owner disagreed; saved as feedback for the checker. */
    FEEDBACK_SAVED,
    REASSIGNED,
    CLOSED
}
