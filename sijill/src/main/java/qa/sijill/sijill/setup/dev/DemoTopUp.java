package qa.sijill.sijill.setup.dev;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.dashboard.service.ScoreService;

/**
 * <b>Demo only.</b> Tops up every school of the demo network with what the newer screens need, so they never look
 * empty: a year of file-health history (longer trend graphs), teachers' weekly plans, school achievements, homework
 * with completions, quizzes with attempts, and one teacher with performance indicators. Each part runs only when the
 * school has none of it yet, so it never doubles up and never touches a school's own records.
 */
@Component
@RequiredArgsConstructor
public class DemoTopUp {

    private static final Logger log = LoggerFactory.getLogger(DemoTopUp.class);
    private static final int HISTORY_DAYS = 365;

    private final JdbcTemplate jdbc;

    @Transactional
    public void topUp(UUID orgId) {
        for (Map<String, Object> s : jdbc.queryForList("SELECT id, name, domain FROM sites WHERE org_id = ? AND active AND NOT head_office", orgId)) {
            UUID site = (UUID) s.get("id");
            String domain = (String) s.get("domain");
            double strength = DemoSchools.ALL.stream().filter(p -> p.domain().equalsIgnoreCase(domain)).findFirst()
                    .map(p -> 0.75 + p.base() * 1.5).orElse(0.8);   // strong schools ~0.87, struggling ~0.54
            Random rnd = new Random(domain.hashCode());
            scores(orgId, site, rnd);
            plans(orgId, site, strength, rnd);
            achievements(orgId, site, strength, rnd);
            homework(orgId, site, strength, rnd);
            quizzes(orgId, site, strength, rnd);
            indicators(orgId, site, strength, rnd);
            assistant(orgId, site, domain);
            materials(orgId, site, rnd);
            classNotice(orgId, site);
            log.info("Demo top-up checked for {}", s.get("name"));
        }
    }

    /** A year of daily file-health scores, older days with a few more open issues, so the trend line climbs. */
    private void scores(UUID org, UUID site, Random rnd) {
        Map<String, Object> oldest = jdbc.queryForList("SELECT score_date, files_checked, open_normal, open_urgent FROM health_scores WHERE site_id = ? ORDER BY score_date LIMIT 1", site)
                .stream().findFirst().orElse(null);
        if (oldest == null) {
            return;
        }
        LocalDate first = ((Date) oldest.get("score_date")).toLocalDate();
        LocalDate stop = LocalDate.now().minusDays(HISTORY_DAYS);
        int files = (Integer) oldest.get("files_checked"), normal = (Integer) oldest.get("open_normal"), urgent = (Integer) oldest.get("open_urgent");
        int span = (int) (first.toEpochDay() - stop.toEpochDay());
        for (int d = 1; d <= span; d++) {
            double t = (double) d / Math.max(1, span);                      // 0 = next to the oldest day we had, 1 = a year ago
            int n = Math.max(0, (int) Math.round(normal + 6 * t * (0.7 + rnd.nextDouble() * 0.6)));
            int u = urgent + (t > .5 ? 1 : 0) + (t > .85 && rnd.nextBoolean() ? 1 : 0);
            int f = Math.max(5, (int) Math.round(files * (0.75 + 0.25 * (1 - t))));
            jdbc.update("INSERT INTO health_scores (id, created_at, org_id, site_id, score_date, score, files_checked, open_normal, open_urgent) "
                            + "VALUES (?, now(), ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (site_id, score_date) DO NOTHING",
                    UUID.randomUUID(), org, site, Date.valueOf(first.minusDays(d)), ScoreService.calculate(n, u, f), f, n, u);
        }
    }

    /** Every teacher's weekly plan for the last eight weeks: most submitted on time, most approved. */
    private void plans(UUID org, UUID site, double strength, Random rnd) {
        if (count("SELECT count(*) FROM planning_submissions WHERE site_id = ?", site) > 0) {
            return;
        }
        List<Map<String, Object>> teachers = jdbc.queryForList(
                "SELECT p.id, p.subject_id, coalesce((SELECT s.grade || s.name FROM teaching_assignments ta JOIN sections s ON s.id = ta.section_id "
                        + "WHERE ta.teacher_id = p.id LIMIT 1), '7A') AS grade FROM people p WHERE p.site_id = ? AND p.role = 'TEACHER' AND p.status <> 'LEFT' AND p.subject_id IS NOT NULL", site);
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String[] types = {"Weekly plan", "Weekly plan", "Weekly plan", "Lesson plan"};
        for (Map<String, Object> t : teachers) {
            for (int w = 0; w < 8; w++) {
                if (rnd.nextDouble() > Math.min(0.98, strength + 0.08)) {
                    continue;   // this week's plan was not submitted
                }
                LocalDate week = monday.minusWeeks(w);
                String status = w == 0 ? (rnd.nextDouble() < 0.6 ? "SUBMITTED" : "APPROVED") : rnd.nextDouble() < 0.1 ? "RETURNED" : "APPROVED";
                Instant at = week.minusDays(rnd.nextInt(3) + 1).atTime(8 + rnd.nextInt(10), rnd.nextInt(60)).atZone(java.time.ZoneId.of("Asia/Qatar")).toInstant();
                jdbc.update("INSERT INTO planning_submissions (id, created_at, org_id, site_id, teacher_id, subject_id, grade, week_start, plan_type, drive_link, status, submitted_at, resubmission_count) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)", UUID.randomUUID(), Timestamp.from(at), org, site, t.get("id"), t.get("subject_id"),
                        t.get("grade"), Date.valueOf(week), types[rnd.nextInt(types.length)],
                        "https://drive.google.com/drive/folders/demo-plan-" + Math.abs(rnd.nextInt()), status, Timestamp.from(at));
            }
        }
    }

    private record Ach(String category, String title, String description, String impact, int points) {}

    private static final List<Ach> ACHIEVEMENTS = List.of(
            new Ach("FACILITIES", "Opened a virtual reality (VR) room", "A VR room with 20 headsets for science and geography lessons.", "All grade 7–9 students use it weekly.", 20),
            new Ach("COMPETITION", "First place in the national robotics challenge", "Our grade 9 team won the national robotics challenge.", "6 students; invited to the regional final.", 15),
            new Ach("ACADEMIC", "Mathematics results up 12%", "The average mathematics mark rose from 68% to 80% over the year.", "Every grade 7 and 8 class improved.", 15),
            new Ach("COMMUNITY", "Reading partnership with the public library", "Weekly reading sessions with volunteers from the library.", "120 students took part.", 10),
            new Ach("STAFF", "Eight teachers certified in digital teaching", "Teachers completed the EAA digital teaching certificate.", "8 teachers, 3 subjects.", 10),
            new Ach("WELLBEING", "Attendance champions programme", "Students mentor classmates who miss school.", "Absence fell by a third in two classes.", 10),
            new Ach("FACILITIES", "New science laboratory", "A fully equipped laboratory for practical science.", "Practical lessons every week for grades 7–9.", 20),
            new Ach("COMPETITION", "Gold medal at the Arabic poetry festival", "A grade 8 student won gold at the national festival.", "1 student, gold medal.", 15));

    /** A few achievements per school; stronger schools have more. */
    private void achievements(UUID org, UUID site, double strength, Random rnd) {
        if (count("SELECT count(*) FROM school_achievements WHERE site_id = ?", site) > 0) {
            return;
        }
        UUID by = jdbc.queryForList("SELECT id FROM people WHERE site_id = ? AND role IN ('PRINCIPAL', 'COORDINATOR') ORDER BY role DESC LIMIT 1", UUID.class, site)
                .stream().findFirst().orElse(null);
        if (by == null) {
            return;
        }
        int n = Math.max(2, (int) Math.round(strength * 7));
        int start = rnd.nextInt(ACHIEVEMENTS.size());
        for (int i = 0; i < n; i++) {
            Ach a = ACHIEVEMENTS.get((start + i) % ACHIEVEMENTS.size());
            jdbc.update("INSERT INTO school_achievements (org_id, site_id, category, title, description, impact, achieved_on, points, created_by) VALUES (?,?,?,?,?,?,?,?,?)",
                    org, site, a.category(), a.title(), a.description(), a.impact(), Date.valueOf(LocalDate.now().minusDays(10 + rnd.nextInt(300))), a.points(), by);
        }
    }

    /** Each class and subject that has none yet. */
    private List<Map<String, Object>> assignments(UUID site) {
        return jdbc.queryForList("SELECT ta.section_id, ta.subject_id, ta.teacher_id, s.name AS subject FROM teaching_assignments ta "
                + "JOIN subjects s ON s.id = ta.subject_id WHERE ta.site_id = ? ORDER BY s.name", site);
    }

    /** The test accounts keep something to do: their newest quiz is left for them to take, their upcoming homework open. */
    private java.util.Set<UUID> testStudents(UUID site) {
        return new java.util.HashSet<>(jdbc.queryForList("SELECT id FROM people WHERE site_id = ? AND role = 'STUDENT' AND lower(display_name) = 'student'", UUID.class, site));
    }

    /** Homework for each class and subject over the last three weeks and the coming week: real tasks for the subject. */
    private void homework(UUID org, UUID site, double strength, Random rnd) {
        java.util.Set<UUID> keepOpen = testStudents(site);
        LocalDate today = LocalDate.now();
        for (Map<String, Object> ta : assignments(site)) {
            if (count("SELECT count(*) FROM homework WHERE section_id = ? AND subject_id = ?", ta.get("section_id"), ta.get("subject_id")) > 0) {
                continue;
            }
            List<String[]> bank = DemoContent.homework((String) ta.get("subject"));
            List<UUID> students = jdbc.queryForList("SELECT student_id FROM enrolments WHERE section_id = ?", UUID.class, ta.get("section_id"));
            int k = rnd.nextInt(bank.size());
            for (int d = -21; d <= 7; d += 4 + rnd.nextInt(3)) {
                LocalDate due = today.plusDays(d);
                while (due.getDayOfWeek() == DayOfWeek.FRIDAY || due.getDayOfWeek() == DayOfWeek.SATURDAY) {
                    due = due.plusDays(1);
                }
                String[] task = bank.get(k++ % bank.size());
                UUID id = UUID.randomUUID();
                jdbc.update("INSERT INTO homework (id, created_at, org_id, site_id, section_id, subject_id, teacher_id, title, details, due_on) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        id, Timestamp.valueOf(due.minusDays(3).atTime(9, 0)), org, site, ta.get("section_id"), ta.get("subject_id"), ta.get("teacher_id"),
                        task[0], task[1], Date.valueOf(due));
                if (due.isBefore(today)) {
                    for (UUID st : students) {
                        if (rnd.nextDouble() < strength + 0.05 || keepOpen.contains(st)) {
                            jdbc.update("INSERT INTO homework_done (homework_id, student_id, done_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", id, st, Timestamp.valueOf(due.minusDays(1).atTime(17, 0)));
                            points(org, site, st, "HOMEWORK", id, 5, "Homework: " + ta.get("subject") + " — " + task[0], due);
                        }
                    }
                }
            }
        }
    }

    /** Two quizzes per class and subject with real questions; the older one taken by most students, the newer one by some. */
    private void quizzes(UUID org, UUID site, double strength, Random rnd) {
        java.util.Set<UUID> keepOpen = testStudents(site);
        for (Map<String, Object> ta : assignments(site)) {
            if (count("SELECT count(*) FROM quizzes WHERE section_id = ? AND subject_id = ?", ta.get("section_id"), ta.get("subject_id")) > 0) {
                continue;
            }
            List<UUID> students = jdbc.queryForList("SELECT student_id FROM enrolments WHERE section_id = ?", UUID.class, ta.get("section_id"));
            List<DemoContent.Quiz> bank = DemoContent.quizzes((String) ta.get("subject"));
            for (int k = 0; k < bank.size(); k++) {
                DemoContent.Quiz q = bank.get(k);
                boolean newest = k == bank.size() - 1;
                UUID quiz = UUID.randomUUID();
                LocalDate on = LocalDate.now().minusDays(newest ? 2 : 20);
                jdbc.update("INSERT INTO quizzes (id, created_at, org_id, site_id, section_id, subject_id, teacher_id, title, source_material) VALUES (?,?,?,?,?,?,?,?,?)",
                        quiz, Timestamp.valueOf(on.atTime(10, 0)), org, site, ta.get("section_id"), ta.get("subject_id"), ta.get("teacher_id"), q.title(), q.material());
                for (int i = 0; i < q.questions().size(); i++) {
                    DemoContent.Question x = q.questions().get(i);
                    jdbc.update("INSERT INTO quiz_questions (quiz_id, position, kind, prompt, options, correct_index, accepted, explanation) VALUES (?,?,'CHOICE',?,?,?,'',?)",
                            quiz, i + 1, x.prompt(), String.join("\n", x.options()), x.correct(), x.explanation());
                }
                int n = q.questions().size();
                for (UUID st : students) {
                    if (keepOpen.contains(st) && newest) {
                        continue;                                   // left for the test student to take
                    }
                    if (rnd.nextDouble() < (newest ? 0.6 : strength + 0.1)) {
                        StringBuilder answers = new StringBuilder();
                        int score = 0;
                        for (int i = 0; i < n; i++) {
                            DemoContent.Question x = q.questions().get(i);
                            int pick = rnd.nextDouble() < strength + 0.05 ? x.correct() : (x.correct() + 1 + rnd.nextInt(x.options().size() - 1)) % x.options().size();
                            score += pick == x.correct() ? 1 : 0;
                            answers.append(i == 0 ? "" : ",").append(pick);
                        }
                        jdbc.update("INSERT INTO quiz_attempts (quiz_id, student_id, answers, score, total, submitted_at) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                                quiz, st, answers.toString(), score, n, Timestamp.valueOf(on.plusDays(1).atTime(18, 0)));
                        points(org, site, st, "QUIZ", quiz, 2 + Math.round(8f * score / n), "Quiz: " + q.title() + " (" + score + "/" + n + ")", on.plusDays(1));
                    }
                }
            }
        }
    }

    /** In a struggling school, one teacher already has a warning and one more indicator, to show how it works. */
    private void indicators(UUID org, UUID site, double strength, Random rnd) {
        if (strength > 0.7 || count("SELECT count(*) FROM teacher_indicators WHERE site_id = ?", site) > 0) {
            return;
        }
        UUID teacher = jdbc.queryForList("SELECT id FROM people WHERE site_id = ? AND role = 'TEACHER' AND status <> 'LEFT' ORDER BY display_name LIMIT 1", UUID.class, site)
                .stream().findFirst().orElse(null);
        UUID principal = jdbc.queryForList("SELECT id FROM people WHERE site_id = ? AND role = 'PRINCIPAL' LIMIT 1", UUID.class, site).stream().findFirst().orElse(null);
        if (teacher == null || principal == null) {
            return;
        }
        UUID warning = UUID.randomUUID();
        jdbc.update("INSERT INTO teacher_warnings (id, created_at, org_id, site_id, teacher_id, number) VALUES (?, now() - interval '20 days', ?, ?, ?, 1)", warning, org, site, teacher);
        String[][] notes = {{"PLANNING", "Weekly plan for week 3 was not submitted."}, {"MARKING", "Grade 7 quiz marks entered a week late."},
                {"PLANNING", "Weekly plan submitted after the deadline again."}, {"DELIVERY", "Observed lesson started 15 minutes late without materials ready."}};
        for (int i = 0; i < notes.length; i++) {
            jdbc.update("INSERT INTO teacher_indicators (created_at, org_id, site_id, teacher_id, recorded_by, category, note, warning_id) VALUES (now() - make_interval(days => ?), ?, ?, ?, ?, ?, ?, ?)",
                    40 - i * 10, org, site, teacher, principal, notes[i][0], notes[i][1], i < 3 ? warning : null);
        }
    }

    /** A principal assistant for each school: they run the day-to-day management the coordinators used to. */
    private void assistant(UUID org, UUID site, String domain) {
        if (count("SELECT count(*) FROM people WHERE site_id = ? AND role = 'PRINCIPAL_ASSISTANT'", site) > 0) {
            return;
        }
        String[] names = {"Latifa Al-Kaabi", "Mubarak Al-Hajri", "Shaikha Al-Naimi", "Faisal Al-Mannai", "Noor Al-Attiyah"};
        String name = names[Math.abs(domain.hashCode()) % names.length];
        String email = "assistant." + Math.abs(domain.hashCode() % 10000) + "@" + domain;
        if (count("SELECT count(*) FROM people WHERE lower(email) = lower(?)", email) > 0) {
            return;
        }
        jdbc.update("INSERT INTO people (id, created_at, org_id, site_id, email, display_name, role, status) VALUES (?, now(), ?, ?, ?, ?, 'PRINCIPAL_ASSISTANT', 'ACTIVE')",
                UUID.randomUUID(), org, site, email, name);
    }

    /** Two study links per class and subject, as teachers share them. */
    private void materials(UUID org, UUID site, Random rnd) {
        for (Map<String, Object> ta : assignments(site)) {
            if (count("SELECT count(*) FROM study_materials WHERE section_id = ? AND subject_id = ?", ta.get("section_id"), ta.get("subject_id")) > 0) {
                continue;
            }
            for (String[] l : DemoContent.materials((String) ta.get("subject"))) {
                jdbc.update("INSERT INTO study_materials (id, created_at, org_id, site_id, section_id, subject_id, title, link, created_by) VALUES (?, now() - make_interval(days => ?), ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), 3 + rnd.nextInt(25), org, site, ta.get("section_id"), ta.get("subject_id"), l[0], l[1], ta.get("teacher_id"));
            }
        }
    }

    /** One teacher's notice to their class, shown at the top of that class's home page for a week. */
    private void classNotice(UUID org, UUID site) {
        if (count("SELECT count(*) FROM notices WHERE site_id = ? AND section_id IS NOT NULL", site) > 0) {
            return;
        }
        jdbc.queryForList("SELECT section_id, teacher_id FROM teaching_assignments WHERE site_id = ? LIMIT 1", site).forEach(ta -> jdbc.update(
                "INSERT INTO notices (id, created_at, org_id, site_id, title_en, title_ar, body_en, body_ar, starts_at, ends_at, audience, created_by, section_id) "
                        + "VALUES (?, now(), ?, ?, ?, ?, ?, ?, now() - interval '1 day', now() + interval '7 days', 'STUDENTS', ?, ?)",
                UUID.randomUUID(), org, site, "Bring your calculator on Sunday", "أحضر الآلة الحاسبة يوم الأحد",
                "We start the new unit with a short practical activity. Please bring a calculator and your exercise book.",
                "نبدأ الوحدة الجديدة بنشاط عملي قصير. يرجى إحضار الآلة الحاسبة ودفتر التمارين.", ta.get("teacher_id"), ta.get("section_id")));
    }

    private void points(UUID org, UUID site, UUID student, String source, UUID ref, int amount, String reason, LocalDate on) {
        jdbc.update("INSERT INTO point_entries (org_id, site_id, student_id, amount, reason, source, awarded_on, ref_id) VALUES (?,?,?,?,?,?,?,?) "
                + "ON CONFLICT (student_id, source, ref_id) WHERE ref_id IS NOT NULL DO NOTHING", org, site, student, amount, reason, source, Date.valueOf(on), ref);
    }

    private int count(String sql, Object... args) {
        Number n = jdbc.queryForObject(sql, Number.class, args);
        return n == null ? 0 : n.intValue();
    }
}
