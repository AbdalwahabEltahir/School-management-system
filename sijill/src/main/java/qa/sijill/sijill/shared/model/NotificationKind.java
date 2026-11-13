package qa.sijill.sijill.shared.model;

/** Category of an in-app notification (the header bell, FR-11.2). */
public enum NotificationKind {
    ISSUE, REMINDER, FOLLOW_UP, INCIDENT, PLANNING, WEEKLY_REPORT, SYSTEM,
    /** Weekly grade deadlines. */
    GRADES,
    /** School notices. */
    NOTICE,
    /** Support ticket updates. */
    TICKET,
    /** Student pick-up: a parent has arrived, a child was released or not released. */
    COLLECTION,
    /** Security and account events. Always delivered: a person cannot switch these off. */
    SECURITY;

    /** Whether a person may switch this kind of alert off in their settings. */
    public boolean mutable() {
        return this != SECURITY;
    }
}
