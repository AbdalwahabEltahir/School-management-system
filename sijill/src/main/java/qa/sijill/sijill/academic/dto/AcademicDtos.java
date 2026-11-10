package qa.sijill.sijill.academic.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import qa.sijill.sijill.academic.model.AttendanceStatus;
import qa.sijill.sijill.academic.model.NoticeAudience;

/** Request and response shapes of the school portal API. Entities never leave the service layer. */
public final class AcademicDtos {

    private AcademicDtos() {}

    // ------------------------------------------------------------------ responses

    public record SectionView(UUID id, int grade, String name, int students) {}

    public record TermView(UUID id, int number, String name, LocalDate startsOn, LocalDate endsOn) {}

    public record StudentView(UUID id, String name, String email) {}

    public record AssignmentView(UUID sectionId, UUID subjectId, String subject, UUID teacherId, String teacher) {}

    /** One lesson of the weekly grid. {@code day}: 0 = Sunday to 4 = Thursday. */
    public record LessonView(UUID id, UUID sectionId, String section, UUID subjectId, String subject, UUID teacherId,
                             String teacher, int day, int period, String room) {}

    public record RosterRow(UUID studentId, String name, AttendanceStatus status) {}

    public record AttendanceDay(LocalDate date, AttendanceStatus status) {}

    public record AttendanceSummary(long present, long absent, long leave, List<AttendanceDay> recent) {}

    /** One student's attendance over a date range (the Admin's summary, FR-027). */
    public record StudentAttendance(UUID studentId, String name, long present, long absent, long leave) {}

    public record AssessmentView(UUID id, UUID sectionId, String section, UUID subjectId, String subject, int term,
                                 String name, BigDecimal maxMark, boolean submitted, boolean published) {}

    public record MarkRow(UUID studentId, String name, BigDecimal value, boolean absent) {}

    /** A published result as the student sees it. */
    public record ResultRow(int term, String subject, String assessment, BigDecimal value, BigDecimal maxMark,
                            boolean absent) {}

    public record NoticeView(UUID id, String titleEn, String titleAr, String bodyEn, String bodyAr, Instant startsAt,
                             Instant endsAt, NoticeAudience audience) {}

    public record MaterialView(UUID id, UUID sectionId, String section, UUID subjectId, String subject, String title,
                               String link, String teacher, Instant createdAt) {}

    /** The teacher's home: today's lessons from the timetable and the sections they teach (FR-024). */
    public record TeacherToday(LocalDate date, boolean schoolDay, List<LessonView> lessons, List<SectionView> sections,
                               List<UUID> attendanceTaken) {}

    /** The student's home (FR-042): today's timetable, notices, own attendance, published results and materials. */
    public record StudentDashboard(SectionView section, LocalDate date, boolean schoolDay, List<LessonView> today,
                                   List<LessonView> week, List<NoticeView> notices, AttendanceSummary attendance,
                                   List<ResultRow> results, List<MaterialView> materials) {}

    // ------------------------------------------------------------------ requests

    public record TermRequest(int number, @NotBlank String name, @NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {}

    public record SectionRequest(int grade, @NotBlank String name) {}

    public record EnrolRequest(@NotBlank String studentEmail) {}

    public record AssignRequest(@NotNull UUID subjectId, @NotNull UUID teacherId) {}

    public record LessonRequest(@NotNull UUID sectionId, @NotNull UUID subjectId, int day, int period, String room) {}

    public record AttendanceRequest(@NotNull LocalDate date, @NotNull Map<UUID, AttendanceStatus> statuses, String reason) {}

    public record AssessmentRequest(@NotNull UUID sectionId, @NotNull UUID subjectId, int term, @NotBlank String name,
                                    @NotNull BigDecimal maxMark) {}

    public record MarkInput(@NotNull UUID studentId, BigDecimal value, boolean absent) {}

    public record MarksRequest(@NotNull List<MarkInput> marks) {}

    public record PublishRequest(int term, String reason) {}

    public record ReasonRequest(String reason) {}

    public record NoticeRequest(@NotBlank String titleEn, String titleAr, @NotBlank String bodyEn, String bodyAr,
                                @NotNull Instant startsAt, @NotNull Instant endsAt, @NotNull NoticeAudience audience) {}

    public record MaterialRequest(@NotNull UUID sectionId, @NotNull UUID subjectId, @NotBlank String title,
                                  @NotBlank String link) {}
}
