package qa.sijill.sijill.network;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.network.SchoolAdminDtos.AssessmentRow;
import qa.sijill.sijill.network.SchoolAdminDtos.AssignmentRow;
import qa.sijill.sijill.network.SchoolAdminDtos.AttendanceRow;
import qa.sijill.sijill.network.SchoolAdminDtos.AttendanceView;
import qa.sijill.sijill.network.SchoolAdminDtos.DailyRow;
import qa.sijill.sijill.network.SchoolAdminDtos.EnrolmentRow;
import qa.sijill.sijill.network.SchoolAdminDtos.MarkCell;
import qa.sijill.sijill.network.SchoolAdminDtos.MarkSectionRow;
import qa.sijill.sijill.network.SchoolAdminDtos.MarksView;
import qa.sijill.sijill.network.SchoolAdminDtos.ResultRow;
import qa.sijill.sijill.network.SchoolAdminDtos.ResultsView;
import qa.sijill.sijill.network.SchoolAdminDtos.SubjectColumn;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Read-only tracking for School IT, scoped to the user's own school: enrolments, who teaches what, attendance, mark
 * submission and result readiness. Changing anything still goes through the academic services.
 */
@Service
@RequiredArgsConstructor
public class SchoolAdminService {

    private static final ZoneId QATAR = ZoneId.of("Asia/Qatar");

    private final JdbcTemplate jdbc;

    private static void requireIt(CurrentUser user) {
        if (!user.isAny(Role.SCHOOL_IT)) {
            throw new ForbiddenException("Only School IT can do this.");
        }
    }

    // ------------------------------------------------------------------ enrolment and teachers

    @Transactional(readOnly = true)
    public List<EnrolmentRow> enrolments(CurrentUser user) {
        requireIt(user);
        return jdbc.query("""
                SELECT p.id, p.display_name, p.email, p.student_code, s.id AS sec_id, s.grade, s.name, e.created_at
                FROM enrolments e JOIN people p ON p.id = e.student_id JOIN sections s ON s.id = e.section_id
                WHERE e.site_id = ? ORDER BY s.grade, s.name, p.display_name""",
                (rs, i) -> new EnrolmentRow(rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("email"), rs.getString("student_code"),
                        rs.getObject("sec_id", UUID.class), rs.getInt("grade"), rs.getString("name"), rs.getTimestamp("created_at").toInstant()), user.siteId());
    }

    @Transactional(readOnly = true)
    public List<AssignmentRow> assignments(CurrentUser user, UUID sectionId) {
        requireIt(user);
        Integer own = jdbc.queryForObject("SELECT COUNT(*) FROM sections WHERE id = ? AND site_id = ?", Integer.class, sectionId, user.siteId());
        if (own == null || own == 0) {
            throw new NotFoundException("The section");
        }
        return jdbc.query("""
                SELECT sub.id, sub.name, sub.code, ta.teacher_id, p.display_name, p.role
                FROM subjects sub LEFT JOIN teaching_assignments ta ON ta.subject_id = sub.id AND ta.section_id = ?
                LEFT JOIN people p ON p.id = ta.teacher_id WHERE sub.site_id = ? ORDER BY sub.name""",
                (rs, i) -> new AssignmentRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("code"), rs.getObject("teacher_id", UUID.class),
                        rs.getString("display_name"), rs.getString("role")), sectionId, user.siteId());
    }

    // ------------------------------------------------------------------ attendance

    @Transactional(readOnly = true)
    public AttendanceView attendance(CurrentUser user, LocalDate from, LocalDate to, Integer grade) {
        requireIt(user);
        LocalDate end = to == null ? LocalDate.now(QATAR) : to, start = from == null ? end.minusDays(29) : from;
        List<Object> args = new ArrayList<>(List.of(start, end, user.siteId()));
        String gradeSql = "";
        if (grade != null) {
            gradeSql = " AND s.grade = ?";
            args.add(grade);
        }
        List<AttendanceRow> sections = jdbc.query("""
                SELECT s.id, s.grade, s.name, (SELECT COUNT(*) FROM enrolments e WHERE e.section_id = s.id) AS students,
                       COUNT(a.id) FILTER (WHERE a.status = 'PRESENT') AS present, COUNT(a.id) FILTER (WHERE a.status = 'ABSENT') AS absent,
                       COUNT(a.id) FILTER (WHERE a.status = 'LEAVE') AS leave
                FROM sections s LEFT JOIN attendance_records a ON a.section_id = s.id AND a.attendance_date BETWEEN ? AND ?
                WHERE s.site_id = ?""" + gradeSql + " GROUP BY s.id ORDER BY s.grade, s.name",
                (rs, i) -> new AttendanceRow(rs.getObject("id", UUID.class), rs.getInt("grade"), rs.getString("name"), rs.getInt("students"),
                        rs.getLong("present"), rs.getLong("absent"), rs.getLong("leave")), args.toArray());
        List<DailyRow> daily = jdbc.query("""
                SELECT a.attendance_date, s.grade, s.name, COUNT(*) FILTER (WHERE a.status = 'PRESENT') AS present,
                       COUNT(*) FILTER (WHERE a.status = 'ABSENT') AS absent, COUNT(*) FILTER (WHERE a.status = 'LEAVE') AS leave
                FROM attendance_records a JOIN sections s ON s.id = a.section_id
                WHERE a.attendance_date BETWEEN ? AND ? AND s.site_id = ?""" + gradeSql + " GROUP BY a.attendance_date, s.id ORDER BY a.attendance_date DESC, s.grade, s.name LIMIT 400",
                (rs, i) -> new DailyRow(rs.getDate("attendance_date").toLocalDate(), rs.getInt("grade"), rs.getString("name"), rs.getLong("present"), rs.getLong("absent"), rs.getLong("leave")),
                args.toArray());
        return new AttendanceView(start, end, sections, daily);
    }

    // ------------------------------------------------------------------ marks and results

    private int termOrCurrent(UUID siteId, Integer term) {
        if (term != null && term >= 1 && term <= 3) {
            return term;
        }
        List<Integer> now = jdbc.queryForList("SELECT number FROM terms WHERE site_id = ? AND ? BETWEEN starts_on AND ends_on ORDER BY starts_on DESC LIMIT 1", Integer.class, siteId, LocalDate.now(QATAR));
        return now.isEmpty() ? 1 : now.get(0);
    }

    private record Cell(UUID sectionId, int grade, String section, UUID subjectId, String subject, long assessments, long submitted, long entered) {}

    private List<Cell> cells(UUID siteId, int term, Integer grade) {
        List<Object> args = new ArrayList<>(List.of(term, siteId));
        String gradeSql = "";
        if (grade != null) {
            gradeSql = " AND s.grade = ?";
            args.add(grade);
        }
        return jdbc.query("""
                SELECT s.id AS sec_id, s.grade, s.name AS sec, sub.id AS sub_id, sub.name AS sub, COUNT(DISTINCT a.id) AS assessments,
                       COUNT(DISTINCT a.id) FILTER (WHERE a.submitted_at IS NOT NULL) AS submitted,
                       COUNT(m.id) FILTER (WHERE m.mark_value IS NOT NULL OR m.absent) AS entered
                FROM teaching_assignments ta JOIN sections s ON s.id = ta.section_id JOIN subjects sub ON sub.id = ta.subject_id
                LEFT JOIN assessments a ON a.section_id = s.id AND a.subject_id = sub.id AND a.term = ?
                LEFT JOIN marks m ON m.assessment_id = a.id
                WHERE ta.site_id = ?""" + gradeSql + " GROUP BY s.id, sub.id ORDER BY s.grade, s.name, sub.name",
                (rs, i) -> new Cell(rs.getObject("sec_id", UUID.class), rs.getInt("grade"), rs.getString("sec"), rs.getObject("sub_id", UUID.class), rs.getString("sub"),
                        rs.getLong("assessments"), rs.getLong("submitted"), rs.getLong("entered")), args.toArray());
    }

    private static String status(Cell c) {
        if (c.assessments() > 0 && c.submitted() == c.assessments()) {
            return "SUBMITTED";
        }
        return c.entered() > 0 ? "DRAFT" : "NOT_ENTERED";
    }

    private Map<UUID, Instant> published(UUID siteId, int term) {
        Map<UUID, Instant> out = new LinkedHashMap<>();
        jdbc.query("SELECT section_id, published_at FROM result_publications WHERE site_id = ? AND term = ?", rs -> {
            out.put(rs.getObject("section_id", UUID.class), rs.getTimestamp("published_at").toInstant());
        }, siteId, term);
        return out;
    }

    @Transactional(readOnly = true)
    public MarksView marks(CurrentUser user, Integer termIn, Integer grade) {
        requireIt(user);
        int term = termOrCurrent(user.siteId(), termIn);
        List<Cell> cells = cells(user.siteId(), term, grade);
        Map<UUID, Instant> done = published(user.siteId(), term);
        List<SubjectColumn> subjects = new ArrayList<>();
        Map<UUID, List<Cell>> bySection = new LinkedHashMap<>();
        for (Cell c : cells) {
            if (subjects.stream().noneMatch(s -> s.id().equals(c.subjectId()))) {
                subjects.add(new SubjectColumn(c.subjectId(), c.subject()));
            }
            bySection.computeIfAbsent(c.sectionId(), k -> new ArrayList<>()).add(c);
        }
        int submitted = 0, draft = 0, notEntered = 0, ready = 0;
        List<MarkSectionRow> rows = new ArrayList<>();
        for (List<Cell> list : bySection.values()) {
            Cell first = list.get(0);
            boolean all = list.stream().allMatch(c -> "SUBMITTED".equals(status(c)));
            boolean isPublished = done.containsKey(first.sectionId());
            if (all && !isPublished) {
                ready++;
            }
            List<MarkCell> out = new ArrayList<>();
            for (SubjectColumn s : subjects) {
                list.stream().filter(c -> c.subjectId().equals(s.id())).findFirst().ifPresentOrElse(c -> out.add(new MarkCell(s.id(), status(c))), () -> out.add(new MarkCell(s.id(), null)));
            }
            for (Cell c : list) {
                switch (status(c)) {
                    case "SUBMITTED" -> submitted++;
                    case "DRAFT" -> draft++;
                    default -> notEntered++;
                }
            }
            rows.add(new MarkSectionRow(first.sectionId(), first.grade(), first.section(), out, all, isPublished));
        }
        List<Object> args = new ArrayList<>(List.of(term, user.siteId()));
        String gradeSql = "";
        if (grade != null) {
            gradeSql = " AND s.grade = ?";
            args.add(grade);
        }
        List<AssessmentRow> assessments = jdbc.query("""
                SELECT a.id, a.section_id, s.grade || s.name AS section, sub.name AS subject, a.name, a.term, a.max_mark, a.submitted_at, a.published_at,
                       (SELECT COUNT(*) FROM marks m WHERE m.assessment_id = a.id AND (m.mark_value IS NOT NULL OR m.absent)) AS entered,
                       (SELECT COUNT(*) FROM enrolments e WHERE e.section_id = a.section_id) AS students
                FROM assessments a JOIN sections s ON s.id = a.section_id JOIN subjects sub ON sub.id = a.subject_id
                WHERE a.term = ? AND a.site_id = ?""" + gradeSql + " ORDER BY s.grade, s.name, sub.name, a.name",
                (rs, i) -> new AssessmentRow(rs.getObject("id", UUID.class), rs.getObject("section_id", UUID.class), rs.getString("section"), rs.getString("subject"), rs.getString("name"),
                        rs.getInt("term"), rs.getBigDecimal("max_mark"), rs.getLong("entered"), rs.getInt("students"), rs.getTimestamp("submitted_at") != null, rs.getTimestamp("published_at") != null),
                args.toArray());
        return new MarksView(term, subjects, rows, assessments, submitted, draft, notEntered, ready);
    }

    @Transactional(readOnly = true)
    public ResultsView results(CurrentUser user, Integer termIn, Integer grade) {
        requireIt(user);
        int term = termOrCurrent(user.siteId(), termIn);
        List<Cell> cells = cells(user.siteId(), term, grade);
        Map<UUID, Instant> done = published(user.siteId(), term);
        Map<UUID, List<Cell>> bySection = new LinkedHashMap<>();
        cells.forEach(c -> bySection.computeIfAbsent(c.sectionId(), k -> new ArrayList<>()).add(c));
        List<Object> args = new ArrayList<>(List.of(user.siteId()));
        String gradeSql = "";
        if (grade != null) {
            gradeSql = " AND s.grade = ?";
            args.add(grade);
        }
        List<ResultRow> rows = new ArrayList<>();
        int published = 0, ready = 0, incomplete = 0;
        for (Map<String, Object> s : jdbc.queryForList("SELECT s.id, s.grade, s.name, (SELECT COUNT(*) FROM enrolments e WHERE e.section_id = s.id) AS students FROM sections s WHERE s.site_id = ?" + gradeSql + " ORDER BY s.grade, s.name", args.toArray())) {
            UUID id = (UUID) s.get("id");
            List<Cell> list = bySection.getOrDefault(id, List.of());
            int ok = (int) list.stream().filter(c -> "SUBMITTED".equals(status(c))).count();
            Instant at = done.get(id);
            String st = at != null ? "PUBLISHED" : (!list.isEmpty() && ok == list.size() ? "READY" : "INCOMPLETE");
            switch (st) {
                case "PUBLISHED" -> published++;
                case "READY" -> ready++;
                default -> incomplete++;
            }
            rows.add(new ResultRow(id, ((Number) s.get("grade")).intValue(), (String) s.get("name"), ((Number) s.get("students")).intValue(), list.size(), ok, st, at));
        }
        return new ResultsView(term, rows, published, ready, incomplete);
    }
}
