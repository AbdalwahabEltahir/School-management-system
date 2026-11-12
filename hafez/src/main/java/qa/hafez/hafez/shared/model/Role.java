package qa.hafez.hafez.shared.model;

/**
 * The roles. The first five are the Phase 1 staff roles (proposal section 4), each from a directory group in the
 * school workspace; {@link #STUDENT} belongs to the school portal (Phase 2). School IT acts as the school Admin of the
 * academic setup (sections, timetable, results, notices).
 */
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
    LEADERSHIP,
    /** A student of the school portal: sees only their own timetable, attendance, published results and materials. */
    STUDENT,
    /** A parent or guardian: sees only the children School IT linked to them, read-only. */
    PARENT;

    /**
     * Whether this role is offered the weekly report download (principals and Leadership only).
     * The single source of truth: the server enforces it in {@code ReportService}, and {@code /api/me}
     * exposes it so the UI shows the button only to these roles.
     */
    public boolean canDownloadWeeklyReport() {
        return this == PRINCIPAL || this == LEADERSHIP;
    }
}
