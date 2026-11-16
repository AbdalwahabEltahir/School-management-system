package qa.sijill.sijill.insights;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.insights.InsightsDtos.AchievementView;
import qa.sijill.sijill.insights.InsightsDtos.MonthPoint;
import qa.sijill.sijill.insights.InsightsDtos.NetworkSummary;
import qa.sijill.sijill.insights.InsightsDtos.SchoolMetrics;
import qa.sijill.sijill.insights.InsightsDtos.SchoolReport;
import qa.sijill.sijill.insights.InsightsDtos.TopStudent;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Key percentages for each school, read straight from the school's own records:
 * <ul>
 *   <li>attendance: days present out of days recorded, last 30 days;</li>
 *   <li>grades: the average of every published mark, as a percentage;</li>
 *   <li>homework: pieces marked done out of pieces set (times class size), due in the last 30 days;</li>
 *   <li>quizzes: the average quiz score;</li>
 *   <li>weekly plans: teachers' weekly plans submitted out of those expected, last four weeks;</li>
 *   <li>file health: the latest score out of 100.</li>
 * </ul>
 * Leadership sees every school; a principal or coordinator their own. EAA IT does not see grades.
 */
@Service
@RequiredArgsConstructor
public class MetricsService {

    private static final String VISIBLE_MARK = "a.submitted_at IS NOT NULL AND NOT m.absent AND m.mark_value IS NOT NULL "
            + "AND (a.published_at IS NOT NULL OR EXISTS (SELECT 1 FROM result_publications rp WHERE rp.section_id = a.section_id AND rp.term = a.term))";

    private final JdbcTemplate jdbc;
    private final AchievementService achievements;

    @Transactional(readOnly = true)
    public NetworkSummary network(CurrentUser user) {
        if (!user.is(Role.LEADERSHIP)) {
            throw new ForbiddenException("The view across schools is for Leadership.");
        }
        List<SchoolMetrics> schools = schoolIds(user.orgId()).stream().map(this::metrics)
                .sorted(Comparator.comparing((SchoolMetrics m) -> m.overall() == null ? -1 : m.overall()).reversed()).toList();
        List<AchievementView> all = achievements.list("WHERE a.org_id = ?", user.orgId());
        return new NetworkSummary(schools, all.size(), all.stream().mapToInt(AchievementView::points).sum(),
                AchievementService.countByCategory(all), avg(schools.stream().map(SchoolMetrics::attendancePct).toList()),
                avg(schools.stream().map(SchoolMetrics::gradePct).toList()), avg(schools.stream().map(SchoolMetrics::homeworkPct).toList()),
                avg(schools.stream().map(SchoolMetrics::weeklyPlansPct).toList()));
    }

    /** One school's report: its percentages, a year of monthly trends, achievements and outstanding students. */
    @Transactional(readOnly = true)
    public SchoolReport report(CurrentUser user, UUID siteId) {
        UUID site = siteId == null ? user.siteId() : siteId;
        requireView(user, site);
        if (jdbc.queryForList("SELECT id FROM sites WHERE id = ? AND org_id = ?", UUID.class, site, user.orgId()).isEmpty()) {
            throw new NotFoundException("The school");
        }
        List<AchievementView> list = achievements.list("WHERE a.site_id = ?", site);
        return new SchoolReport(metrics(site), months(site), list, topStudents(site, 5), AchievementService.countByCategory(list));
    }

    @Transactional(readOnly = true)
    public SchoolMetrics mine(CurrentUser user) {
        requireView(user, user.siteId());
        return metrics(user.siteId());
    }

    private void requireView(CurrentUser user, UUID siteId) {
        boolean ok = user.is(Role.LEADERSHIP) || (user.isAny(Role.PRINCIPAL, Role.PRINCIPAL_ASSISTANT, Role.COORDINATOR) && siteId.equals(user.siteId()));
        if (!ok) {
            throw new ForbiddenException("School metrics are for the principal, coordinators and Leadership.");
        }
    }

    private List<UUID> schoolIds(UUID orgId) {
        return jdbc.queryForList("SELECT id FROM sites WHERE org_id = ? AND active AND NOT head_office ORDER BY name", UUID.class, orgId);
    }

    SchoolMetrics metrics(UUID site) {
        String name = jdbc.queryForObject("SELECT name FROM sites WHERE id = ?", String.class, site);
        int students = count("SELECT count(*) FROM enrolments WHERE site_id = ?", site);
        int teachers = count("SELECT count(*) FROM people WHERE site_id = ? AND role IN ('TEACHER', 'COORDINATOR') AND status <> 'LEFT'", site);
        Integer attendance = pct("SELECT count(*) FILTER (WHERE status = 'PRESENT'), count(*) FROM attendance_records WHERE site_id = ? AND attendance_date > current_date - 30", site);
        Integer grade = round(jdbc.queryForObject("SELECT avg(100.0 * m.mark_value / a.max_mark) FROM marks m JOIN assessments a ON a.id = m.assessment_id WHERE a.site_id = ? AND "
                + VISIBLE_MARK, Double.class, site));
        Integer homework = pct("SELECT coalesce(sum((SELECT count(*) FROM homework_done d WHERE d.homework_id = h.id)), 0), "
                + "coalesce(sum((SELECT count(*) FROM enrolments e WHERE e.section_id = h.section_id)), 0) "
                + "FROM homework h WHERE h.site_id = ? AND h.due_on BETWEEN current_date - 30 AND current_date", site);
        Integer quiz = round(jdbc.queryForObject("SELECT avg(100.0 * qa.score / nullif(qa.total, 0)) FROM quiz_attempts qa JOIN quizzes q ON q.id = qa.quiz_id WHERE q.site_id = ?",
                Double.class, site));
        int planTeachers = count("SELECT count(*) FROM people WHERE site_id = ? AND role = 'TEACHER' AND status <> 'LEFT'", site);
        int plans = count("SELECT count(DISTINCT (teacher_id, week_start)) FROM planning_submissions WHERE site_id = ? AND week_start > current_date - 35", site);
        Integer weekly = planTeachers == 0 ? null : Math.min(100, Math.round(plans * 100f / (planTeachers * 4)));
        Integer health = round(jdbc.queryForList("SELECT score FROM health_scores WHERE site_id = ? ORDER BY score_date DESC LIMIT 1", Double.class, site)
                .stream().findFirst().orElse(null));
        int points = count("SELECT coalesce(sum(amount), 0) FROM point_entries WHERE site_id = ? AND source <> 'ATTENDANCE'", site);
        int achieved = count("SELECT count(*) FROM school_achievements WHERE site_id = ?", site);
        int achievedPoints = count("SELECT coalesce(sum(points), 0) FROM school_achievements WHERE site_id = ?", site);
        int investigations = count("SELECT count(*) FROM investigations WHERE site_id = ? AND status = 'OPEN'", site);
        return new SchoolMetrics(site, name, students, teachers, attendance, grade, homework, quiz, weekly, health, points, achieved,
                achievedPoints, investigations, avg(java.util.Arrays.asList(attendance, grade, homework, quiz, weekly, health)));
    }

    /** The last twelve months, oldest first. A month with no records has no value. */
    private List<MonthPoint> months(UUID site) {
        Map<String, Integer> att = monthly("SELECT to_char(attendance_date, 'YYYY-MM'), round(100.0 * count(*) FILTER (WHERE status = 'PRESENT') / count(*)) "
                + "FROM attendance_records WHERE site_id = ? AND attendance_date > current_date - 370 GROUP BY 1", site);
        Map<String, Integer> grades = monthly("SELECT to_char(coalesce(a.published_at, a.submitted_at), 'YYYY-MM'), round(avg(100.0 * m.mark_value / a.max_mark)) "
                + "FROM marks m JOIN assessments a ON a.id = m.assessment_id WHERE a.site_id = ? AND " + VISIBLE_MARK
                + " AND coalesce(a.published_at, a.submitted_at) > now() - interval '370 days' GROUP BY 1", site);
        Map<String, Integer> hw = monthly("SELECT to_char(h.due_on, 'YYYY-MM'), round(100.0 * sum((SELECT count(*) FROM homework_done d WHERE d.homework_id = h.id)) "
                + "/ nullif(sum((SELECT count(*) FROM enrolments e WHERE e.section_id = h.section_id)), 0)) FROM homework h "
                + "WHERE h.site_id = ? AND h.due_on > current_date - 370 AND h.due_on <= current_date GROUP BY 1", site);
        List<MonthPoint> out = new ArrayList<>();
        YearMonth now = YearMonth.from(LocalDate.now());
        for (int i = 11; i >= 0; i--) {
            String m = now.minusMonths(i).toString();
            out.add(new MonthPoint(m, att.get(m), grades.get(m), hw.get(m)));
        }
        return out;
    }

    private List<TopStudent> topStudents(UUID site, int n) {
        return jdbc.query("SELECT p.display_name, coalesce(s.grade || s.name, ''), sum(pe.amount) AS total FROM point_entries pe "
                        + "JOIN people p ON p.id = pe.student_id LEFT JOIN enrolments e ON e.student_id = p.id LEFT JOIN sections s ON s.id = e.section_id "
                        + "WHERE pe.site_id = ? AND pe.source <> 'ATTENDANCE' GROUP BY p.display_name, s.grade, s.name ORDER BY total DESC LIMIT ?",
                (rs, i) -> new TopStudent(rs.getString(1), rs.getString(2), rs.getInt(3)), site, n);
    }

    private Map<String, Integer> monthly(String sql, UUID site) {
        Map<String, Integer> out = new HashMap<>();
        jdbc.query(sql, rs -> { Object v = rs.getObject(2); if (v != null) out.put(rs.getString(1), ((Number) v).intValue()); }, site);
        return out;
    }

    private int count(String sql, UUID site) {
        Number n = jdbc.queryForObject(sql, Number.class, site);
        return n == null ? 0 : n.intValue();
    }

    private Integer pct(String sql, UUID site) {
        return jdbc.queryForObject(sql, (rs, i) -> {
            long part = rs.getLong(1), whole = rs.getLong(2);
            return whole == 0 ? null : (int) Math.round(part * 100.0 / whole);
        }, site);
    }

    private static Integer round(Double v) {
        return v == null ? null : (int) Math.round(v);
    }

    private static Integer avg(List<Integer> values) {
        List<Integer> have = values.stream().filter(Objects::nonNull).toList();
        return have.isEmpty() ? null : (int) Math.round(have.stream().mapToInt(Integer::intValue).average().orElse(0));
    }
}
