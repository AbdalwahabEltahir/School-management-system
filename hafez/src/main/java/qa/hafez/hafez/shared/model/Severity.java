package qa.hafez.hafez.shared.model;

/** Issue severity; drives reminder timing and who is told (proposal 5.3 and 5.4). */
public enum Severity {
    NORMAL,
    /** Urgent issues are told at once, copy the coordinator and count 3x in the health score. */
    URGENT
}
