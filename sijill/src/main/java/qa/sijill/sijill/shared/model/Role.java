package qa.sijill.sijill.shared.model;

/** The five Phase 1 roles (proposal section 4). Each comes from a directory group in the school workspace. */
public enum Role {
    /** Teaching and support staff: sees only their own files and issues. */
    TEACHER,
    /** Subject (e-learning) coordinator: sees and fixes her subject's files, reviews planning files. */
    COORDINATOR,
    /** Principal: read-only oversight of every notification, score and issue of her school. */
    PRINCIPAL,
    /** School IT admin (academic staff): connection, people, settings, school rules. */
    SCHOOL_IT,
    /** Network executive team: totals, incidents and the weekly report only. */
    LEADERSHIP;

    /**
     * Whether this role is offered the weekly report download (principals and Leadership only).
     * The single source of truth: the server enforces it in {@code ReportService}, and {@code /api/me}
     * exposes it so the UI shows the button only to these roles.
     */
    public boolean canDownloadWeeklyReport() {
        return this == PRINCIPAL || this == LEADERSHIP;
    }
}
