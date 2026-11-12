package qa.hafez.hafez.rules.model;

/** State of a rule (FR-8.1, FR-2.5). */
public enum RuleStatus {
    /** Saved but not switched on yet. */
    DRAFT,
    ACTIVE,
    /** Issues are held without messages. */
    PAUSED,
    /** Soft-deleted; its open issues are closed as "rule removed". */
    DELETED
}
