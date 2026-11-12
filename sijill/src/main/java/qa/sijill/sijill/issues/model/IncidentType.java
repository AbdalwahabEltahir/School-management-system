package qa.sijill.sijill.issues.model;

/** Major incidents, the only events that reach leadership (proposal sections 2 and 5.4). */
public enum IncidentType {
    /** Rule R6: an exam was shared before its date. */
    EXAM_LEAK,
    /** Rule R10: an important file was deleted. */
    IMPORTANT_DELETION,
    /** 20 or more deletions within an hour. */
    BULK_DELETION,
    /** An urgent issue is still open after one school day. */
    URGENT_UNRESOLVED,
    /** The workspace connection has been down for over an hour. */
    CONNECTION_LOST
}
