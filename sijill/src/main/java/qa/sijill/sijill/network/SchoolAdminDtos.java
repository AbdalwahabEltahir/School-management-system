package qa.sijill.sijill.network;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What School IT sees when tracking the school: who is in which class, who teaches what, attendance, marks and results. */
public final class SchoolAdminDtos {

    private SchoolAdminDtos() {
    }

    public record EnrolmentRow(UUID studentId, String name, String email, String studentCode, UUID sectionId, int grade, String section, Instant since) {}

    /** One subject of the school and who teaches it in a section; {@code teacherId} is null when nobody does yet. */
    public record AssignmentRow(UUID subjectId, String subject, String code, UUID teacherId, String teacher, String teacherRole) {}

    public record AttendanceRow(UUID sectionId, int grade, String section, int students, long present, long absent, long leave) {}

    public record DailyRow(LocalDate date, int grade, String section, long present, long absent, long leave) {}

    public record AttendanceView(LocalDate from, LocalDate to, List<AttendanceRow> sections, List<DailyRow> daily) {}

    /** SUBMITTED (locked), DRAFT (entered, not submitted), NOT_ENTERED. */
    public record MarkCell(UUID subjectId, String status) {}

    public record MarkSectionRow(UUID sectionId, int grade, String section, List<MarkCell> cells, boolean ready, boolean published) {}

    public record SubjectColumn(UUID id, String name) {}

    public record AssessmentRow(UUID id, UUID sectionId, String section, String subject, String name, int term, BigDecimal maxMark, long entered, int students, boolean submitted, boolean published) {}

    public record MarksView(int term, List<SubjectColumn> subjects, List<MarkSectionRow> rows, List<AssessmentRow> assessments, int submitted, int draft, int notEntered, int ready) {}

    /** PUBLISHED, READY (every mark is locked) or INCOMPLETE. */
    public record ResultRow(UUID sectionId, int grade, String section, int students, int subjects, int submitted, String status, Instant publishedAt) {}

    public record ResultsView(int term, List<ResultRow> rows, int published, int ready, int incomplete) {}
}
