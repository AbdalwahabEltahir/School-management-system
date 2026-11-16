package qa.sijill.sijill.insights;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes of school metrics, achievements and teacher performance. */
public final class InsightsDtos {

    private InsightsDtos() {
    }

    // ---- school metrics
    /**
     * Key percentages for one school. A percentage is null when there is nothing to measure yet.
     * {@code overall} is the average of the percentages that exist; it ranks the outstanding schools.
     */
    public record SchoolMetrics(UUID siteId, String school, int students, int teachers,
                                Integer attendancePct, Integer gradePct, Integer homeworkPct, Integer quizPct,
                                Integer weeklyPlansPct, Integer healthScore, int studentPoints, int achievements,
                                int achievementPoints, int openInvestigations, Integer overall) {}

    public record MonthPoint(String month, Integer attendancePct, Integer gradePct, Integer homeworkPct) {}

    public record TopStudent(String name, String section, int points) {}

    public record SchoolReport(SchoolMetrics metrics, List<MonthPoint> months, List<AchievementView> achievements,
                               List<TopStudent> topStudents, List<CategoryCount> byCategory) {}

    public record NetworkSummary(List<SchoolMetrics> schools, int achievements, int achievementPoints,
                                 List<CategoryCount> byCategory, Integer attendancePct, Integer gradePct,
                                 Integer homeworkPct, Integer weeklyPlansPct) {}

    // ---- achievements
    public record CategoryView(String key, String label, int points, String hint) {}

    public record CategoryCount(String key, String label, int count, int points) {}

    public record AchievementRequest(@NotBlank String category, @NotBlank String title, String description, String impact,
                                     @NotNull LocalDate achievedOn, String evidenceLink) {}

    public record AchievementView(UUID id, UUID siteId, String school, String category, String categoryLabel, String title,
                                  String description, String impact, LocalDate achievedOn, String evidenceLink, int points,
                                  String by, Instant createdAt) {}

    // ---- teacher performance
    public record IndicatorRequest(@NotNull UUID teacherId, @NotBlank String category, @NotBlank String note) {}

    /** What recording an indicator led to. */
    public record IndicatorOutcome(int pendingIndicators, int warnings, boolean warningIssued, UUID investigationId) {}

    public record TeacherStanding(UUID teacherId, String name, String role, String subject, int pendingIndicators,
                                  int warnings, int totalIndicators, UUID openInvestigationId, Instant lastIndicatorAt) {}

    public record IndicatorView(UUID id, String category, String categoryLabel, String note, String by, Instant at,
                                Integer warningNumber) {}

    public record WarningView(UUID id, int number, Instant at, boolean ledToInvestigation) {}

    public record TeacherRecord(TeacherStanding standing, List<IndicatorView> indicators, List<WarningView> warnings,
                                List<InvestigationSummary> investigations) {}

    public record InvestigationSummary(UUID id, UUID teacherId, String teacher, String status, Instant openedAt,
                                       Instant closedAt, int members, int notes) {}

    public record Member(UUID personId, String name, String role) {}

    public record Note(String author, String body, Instant at) {}

    public record InvestigationDetail(InvestigationSummary summary, String outcome, List<Member> members, List<Note> notes,
                                      List<WarningView> warnings, List<IndicatorView> indicators, boolean canManage,
                                      List<Member> canAdd) {}

    public record NoteRequest(@NotBlank String body) {}

    public record MemberRequest(@NotNull UUID personId) {}

    public record CloseRequest(@NotBlank String outcome) {}
}
