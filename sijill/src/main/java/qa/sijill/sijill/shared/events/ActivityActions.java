package qa.sijill.sijill.shared.events;

/**
 * Action codes written to the activity log (FR-5.2). One constant per kind of event, so the log can be
 * filtered and every place that writes to it is easy to find: search for the constant.
 */
public final class ActivityActions {

    private ActivityActions() {}

    // ---- issues ----
    public static final String ISSUE_OPENED = "ISSUE_OPENED";
    public static final String ISSUE_STATUS_CHANGED = "ISSUE_STATUS_CHANGED";
    public static final String ISSUE_REASSIGNED = "ISSUE_REASSIGNED";
    public static final String EXCEPTION_GRANTED = "EXCEPTION_GRANTED";
    public static final String EXCEPTION_ENDED = "EXCEPTION_ENDED";
    public static final String INCIDENT_RAISED = "INCIDENT_RAISED";
    public static final String INCIDENT_RESOLVED = "INCIDENT_RESOLVED";

    // ---- files ----
    public static final String FILE_CLASSIFIED = "FILE_CLASSIFIED";
    public static final String CHECK_RUN = "CHECK_RUN";

    // ---- rules and settings (FR-5.3: every rule and setting change is logged) ----
    public static final String RULE_CREATED = "RULE_CREATED";
    public static final String RULE_UPDATED = "RULE_UPDATED";
    public static final String RULE_APPROVED = "RULE_APPROVED";
    public static final String RULE_PAUSED = "RULE_PAUSED";
    public static final String RULE_RESUMED = "RULE_RESUMED";
    public static final String RULE_DELETED = "RULE_DELETED";
    public static final String SETTING_CHANGED = "SETTING_CHANGED";
    public static final String SITE_CREATED = "SITE_CREATED";
    public static final String SITE_UPDATED = "SITE_UPDATED";
    public static final String SITE_REMOVED = "SITE_REMOVED";
    public static final String SUBJECT_CHANGED = "SUBJECT_CHANGED";
    public static final String DOC_TYPE_CHANGED = "DOC_TYPE_CHANGED";
    public static final String ROLE_GROUP_CHANGED = "ROLE_GROUP_CHANGED";

    // ---- people ----
    public static final String PERSON_UPDATED = "PERSON_UPDATED";
    public static final String PERSON_FOLLOWED_UP = "PERSON_FOLLOWED_UP";
    public static final String DIRECTORY_SYNCED = "DIRECTORY_SYNCED";
    public static final String WELCOME_SENT = "WELCOME_SENT";

    // ---- planning, restore, export ----
    public static final String PLANNING_SUBMITTED = "PLANNING_SUBMITTED";
    public static final String PLANNING_REVIEWED = "PLANNING_REVIEWED";
    public static final String RESTORE_REQUESTED = "RESTORE_REQUESTED";
    public static final String RESTORE_APPROVED = "RESTORE_APPROVED";
    public static final String RESTORE_REJECTED = "RESTORE_REJECTED";
    public static final String RESTORE_COMPLETED = "RESTORE_COMPLETED";
    public static final String EXPORT_CREATED = "EXPORT_CREATED";
}
