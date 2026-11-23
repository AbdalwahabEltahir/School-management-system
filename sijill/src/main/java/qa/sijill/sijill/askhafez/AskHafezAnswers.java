package qa.sijill.sijill.askhafez;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import qa.sijill.sijill.academic.dto.AcademicDtos.AttendanceSummary;
import qa.sijill.sijill.academic.dto.AcademicDtos.LessonView;
import qa.sijill.sijill.academic.dto.AcademicDtos.PointsView;
import qa.sijill.sijill.academic.dto.AcademicDtos.ResultRow;
import qa.sijill.sijill.academic.dto.AcademicDtos.StudentDashboard;
import qa.sijill.sijill.academic.service.AcademicAccess;
import qa.sijill.sijill.academic.service.AcademicSetupService;
import qa.sijill.sijill.academic.service.ParentPortalService;
import qa.sijill.sijill.academic.service.PointsService;
import qa.sijill.sijill.academic.service.PortalService;
import qa.sijill.sijill.askhafez.AskHafezDtos.Link;
import qa.sijill.sijill.classroom.ClassroomDtos.HomeworkView;
import qa.sijill.sijill.classroom.ClassroomDtos.QuizSummary;
import qa.sijill.sijill.classroom.ClassroomPeople;
import qa.sijill.sijill.classroom.HomeworkService;
import qa.sijill.sijill.classroom.QuizService;
import qa.sijill.sijill.shared.exception.ApiException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.shared.util.SchoolCalendar;

/**
 * The brain of Ask Hafez. It turns a question into one of a fixed set of read-only lookups (homework and quizzes due,
 * published marks, attendance, timetable, points and levels, pick-up steps, the user guide) and writes a short answer in
 * the language of the question, with links to the Hafez pages it came from. Every lookup goes through the same services
 * the screens use, called as the signed-in person, so a parent only ever sees their own children, a student only
 * themselves and a teacher only their own classes. Nothing here can write. What it cannot answer, it says so.
 */
@Component
@RequiredArgsConstructor
class AskHafezAnswers {

    private static final int LEVEL_POINTS = 50;
    private static final Locale AR_LOCALE = Locale.forLanguageTag("ar-QA-u-nu-latn");
    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final JdbcTemplate jdbc;
    private final ClassroomPeople people;
    private final HomeworkService homework;
    private final QuizService quizzes;
    private final PortalService portal;
    private final ParentPortalService parents;
    private final PointsService points;
    private final AcademicSetupService setup;
    private final AcademicAccess access;

    /** What was understood, the answer, where it came from, and follow-up questions. */
    record Answer(String intent, String text, List<Link> links, List<String> suggestions, boolean arabic) {}

    /** The question and who asked, prepared once. */
    private record Ctx(CurrentUser user, String question, String norm, Set<String> tokens, boolean ar, LocalDate today) {
        String x(String en, String arText) {
            return ar ? arText : en;
        }

        boolean parent() {
            return user.is(Role.PARENT);
        }

        boolean student() {
            return user.is(Role.STUDENT);
        }

        boolean teacher() {
            return user.is(Role.TEACHER);
        }

        /** Whole-word match for English keys, substring match for Arabic keys (Arabic words carry prefixes), phrase match for keys with a space. */
        boolean has(String... keys) {
            for (String k : keys) {
                if (k.indexOf(' ') >= 0 || k.chars().anyMatch(c -> c >= 0x0600 && c <= 0x06FF) ? norm.contains(k) : tokens.contains(k)) {
                    return true;
                }
            }
            return false;
        }

        DateTimeFormatter dayFormat() {
            return DateTimeFormatter.ofPattern("EEEE d MMM", ar ? AR_LOCALE : Locale.ENGLISH);
        }

        String day(LocalDate d) {
            return d.format(dayFormat());
        }
    }

    private record Guide(Predicate<Ctx> when, Set<Role> roles, String en, String ar, List<Link> links) {}

    // ============================================================================== entry point

    Answer answer(CurrentUser user, String question) {
        String n = norm(question);
        Ctx c = new Ctx(user, question, n, new HashSet<>(Arrays.asList(SPLIT.split(n))), hasArabic(question), access.today());

        if (c.has("bully", "bullying", "abuse", "hurt", "sick", "ill", "health", "medical", "doctor", "allergy", "allergic", "behaviour", "behavior",
                "discipline", "punish", "punished", "divorce", "custody", "depressed", "suicide", "anxiety", "mental", "harass",
                "تنمر", "ضرب", "مريض", "مرض", "صحه", "طبي", "سلوك", "انضباط", "عقاب", "طلاق", "حضانه", "اكتئاب", "قلق", "نفسي", "تحرش")) {
            return sensitive(c);
        }
        if (c.teacher() && c.has("handed", "hand", "missing", "outstanding", "havent", "didnt", "not done", "not submitted", "late",
                "لم يسلم", "لم يسلموا", "ما سلم", "لم يحل", "لم ينجز") && c.has("homework", "assignment", "assignments", "واجب", "واجبات")) {
            return notHandedIn(c);
        }
        if (c.has("pick up", "pickup", "pick-up", "collect", "collecting", "dismissal", "استلام", "استلم", "اخذ ابني", "اخذ ابنتي", "اخذ طفلي", "انصراف",
                "اوصل", "خروج مبكر")) {
            return pickup(c);
        }
        Answer g = guide(c);
        if (g != null) {
            return g;
        }
        if (c.has("point", "points", "level", "levels", "xp", "نقاط", "نقطه", "نقطتي", "مستوي")) {
            return pointsAnswer(c);
        }
        if (c.has("attendance", "absent", "absence", "present", "حضور", "غياب", "غايب", "حاضر", "تغيب")) {
            return attendanceAnswer(c);
        }
        if (c.has("homework", "assignment", "assignments", "quiz", "quizzes", "due", "واجب", "واجبات", "اختبار", "اختبارات", "كويز", "تسليم", "مهام")) {
            return due(c);
        }
        if (c.has("timetable", "schedule", "lesson", "lessons", "period", "periods", "what do i have", "what have i got", "whats on",
                "what is on", "جدول", "حصه", "حصص", "درس", "دروس", "عندي", "لدي", "عندنا")) {
            return timetable(c);
        }
        if (c.has("mark", "marks", "grade", "grades", "result", "results", "score", "scores", "doing", "performance", "progress", "term",
                "درجه", "درجات", "علامات", "علامه", "نتيجه", "نتائج", "اداء", "تقدم", "مستواه", "حال")) {
            return marks(c);
        }
        if (c.norm().length() <= 25 && c.has("hi", "hello", "hey", "help", "salam", "مرحبا", "اهلا", "السلام", "مساعده", "ساعدني", "هلا")) {
            return help(c, true);
        }
        return unknown(c);
    }

    // ============================================================================== lookups

    /** Homework and quizzes due: one day, or the next seven days. */
    private Answer due(Ctx c) {
        LocalDate day = dateIn(c);
        LocalDate from = day != null ? day : c.today(), to = day != null ? day : c.today().plusDays(7);
        String when = day != null ? c.x("on " + c.day(day), "يوم " + c.day(day)) : c.x("in the next 7 days", "خلال الأيام السبعة القادمة");
        List<Link> links = List.of(new Link(c.x("Homework", "الواجبات"), "#/homework"));
        StringBuilder sb = new StringBuilder();
        List<HomeworkView> list = homework.list(c.user(), from, to);
        if (c.parent()) {
            Map<UUID, String> kids = pickedChildren(c);
            for (String name : kids.values()) {
                List<HomeworkView> mine = list.stream().filter(h -> h.children().contains(name)).toList();
                sb.append(name).append(": ");
                if (mine.isEmpty()) {
                    sb.append(c.x("no homework due " + when + ".", "لا يوجد واجب مستحق " + when + ".")).append('\n');
                } else {
                    sb.append(c.x(mine.size() + " due " + when + ":", mine.size() + " مستحق " + when + ":")).append('\n');
                    mine.forEach(h -> sb.append(homeworkLine(c, h, false)));
                }
                sb.append(quizLinesForChild(c, UUIDof(kids, name))).append('\n');
            }
            links = List.of(links.get(0), new Link(c.x("My children", "أبنائي"), "#/children"));
        } else if (c.student()) {
            List<HomeworkView> todo = list.stream().filter(h -> !h.done()).toList();
            if (list.isEmpty()) {
                sb.append(c.x("You have no homework due " + when + ".", "ليس لديك واجبات مستحقة " + when + "."));
            } else {
                sb.append(c.x("Homework due " + when + ":", "الواجبات المستحقة " + when + ":")).append('\n');
                list.forEach(h -> sb.append(homeworkLine(c, h, true)));
                if (todo.isEmpty()) {
                    sb.append(c.x("You have marked all of it as done. Well done!", "أنجزت كل شيء. أحسنت!")).append('\n');
                }
            }
            List<QuizSummary> open = quizzes.list(c.user()).stream().filter(q -> !q.taken()).toList();
            if (!open.isEmpty()) {
                sb.append('\n').append(c.x("Quizzes you have not taken yet:", "اختبارات لم تؤدّها بعد:")).append('\n');
                open.stream().limit(5).forEach(q -> sb.append("• ").append(q.subject()).append(" — ").append(q.title()).append('\n'));
                links = List.of(links.get(0), new Link(c.x("Assessments", "التقييمات"), "#/quizzes"));
            }
        } else {
            if (list.isEmpty()) {
                sb.append(c.x("You have no homework due " + when + ".", "ليس لديك واجبات مستحقة " + when + "."));
            } else {
                sb.append(c.x("Homework you set that is due " + when + ":", "الواجبات التي أضفتها والمستحقة " + when + ":")).append('\n');
                for (HomeworkView h : list) {
                    sb.append("• ").append(h.subject()).append(" · ").append(h.section()).append(" — ").append(h.title()).append(" (")
                            .append(c.day(h.dueOn())).append(", ").append(h.doneCount()).append('/').append(h.classSize())
                            .append(c.x(" done", " أنجزوا")).append(")\n");
                }
            }
        }
        return new Answer("HOMEWORK", sb.toString().trim(), links, followUps(c), c.ar());
    }

    private String homeworkLine(Ctx c, HomeworkView h, boolean showDone) {
        return "• " + h.subject() + " — " + h.title() + " (" + c.x("due ", "التسليم ") + c.day(h.dueOn()) + ")"
                + (showDone && h.done() ? " ✓" : "") + "\n";
    }

    private String quizLinesForChild(Ctx c, UUID child) {
        Optional<UUID> section = people.sectionOf(child);
        if (section.isEmpty()) {
            return "";
        }
        Map<UUID, String> subjects = people.subjectNames(c.user().siteId());
        List<String> rows = jdbc.query("SELECT subject_id, title FROM quizzes q WHERE q.section_id = ? AND NOT EXISTS "
                        + "(SELECT 1 FROM quiz_attempts a WHERE a.quiz_id = q.id AND a.student_id = ?) ORDER BY q.created_at DESC LIMIT 5",
                (rs, i) -> "• " + subjects.getOrDefault(rs.getObject(1, UUID.class), "") + " — " + rs.getString(2), section.get(), child);
        if (rows.isEmpty()) {
            return "";
        }
        return c.x("Quizzes not taken yet:", "اختبارات لم تؤدَّ بعد:") + "\n" + String.join("\n", rows);
    }

    /** A teacher asks who has not handed in homework: the students of their own classes who have not marked it done. */
    private Answer notHandedIn(Ctx c) {
        LocalDate from = c.today().minusDays(7);
        List<HomeworkView> due = homework.list(c.user(), from, c.today()).stream().filter(HomeworkView::mine).toList();
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (HomeworkView h : due) {
            if (h.doneCount() >= h.classSize()) {
                continue;
            }
            List<UUID> missing = new ArrayList<>(people.studentsOf(h.sectionId()));
            missing.removeAll(jdbc.queryForList("SELECT student_id FROM homework_done WHERE homework_id = ?", UUID.class, h.id()));
            if (missing.isEmpty()) {
                continue;
            }
            Map<UUID, String> names = people.names(missing);
            List<String> sorted = missing.stream().map(id -> names.getOrDefault(id, "")).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            sb.append(h.subject()).append(" · ").append(h.section()).append(" — ").append(h.title()).append(" (").append(c.day(h.dueOn())).append("):\n");
            sb.append(String.join(c.ar() ? "، " : ", ", sorted.stream().limit(12).toList()));
            if (sorted.size() > 12) {
                sb.append(c.x(" and " + (sorted.size() - 12) + " more", " و" + (sorted.size() - 12) + " آخرين"));
            }
            sb.append("\n\n");
            shown++;
        }
        String text = shown == 0
                ? (due.isEmpty() ? c.x("You have not set any homework that fell due in the last 7 days.", "لم تضف واجبات حلّ موعدها في آخر 7 أيام.")
                        : c.x("Everyone has handed in the homework that fell due in the last 7 days.", "سلّم الجميع الواجبات التي حلّ موعدها في آخر 7 أيام."))
                : c.x("Students in your classes who have not marked these as done (last 7 days):", "طلاب صفوفك الذين لم يسلّموا هذه الواجبات (آخر 7 أيام):") + "\n\n" + sb.toString().trim();
        return new Answer("HOMEWORK_MISSING", text, List.of(new Link(c.x("Homework", "الواجبات"), "#/homework")), followUps(c), c.ar());
    }

    /** "How is Bader doing?": published marks, attendance and homework, per child (or the student themselves). */
    private Answer marks(Ctx c) {
        if (c.teacher()) {
            return pointer(c, "MARKS", c.x("Ask Hafez shows marks to parents and students. For your classes, open Marks, or the Matrix for a whole class.",
                    "يعرض حافظ الدرجات لأولياء الأمور والطلاب. لصفوفك افتح صفحة الدرجات، أو المصفوفة لعرض الصف كاملاً."),
                    new Link(c.x("Marks", "الدرجات"), "#/marks"), new Link(c.x("Matrix", "المصفوفة"), "#/matrix"));
        }
        StringBuilder sb = new StringBuilder();
        Map<UUID, String> who = c.parent() ? pickedChildren(c) : Map.of(c.user().personId(), c.user().name());
        List<HomeworkView> week = homework.list(c.user(), c.today(), c.today().plusDays(7));
        for (Map.Entry<UUID, String> e : who.entrySet()) {
            sb.append(c.parent() ? e.getValue() + ":\n" : "");
            StudentDashboard d = dashboard(c, e.getKey());
            if (d == null) {
                sb.append(c.x("Not in a class yet, so there is nothing to show.", "غير مسجّل في صف بعد، لذا لا يوجد ما يُعرض.")).append("\n\n");
                continue;
            }
            sb.append(resultsText(c, d.results())).append('\n').append(attendanceText(c, d.attendance())).append('\n');
            long hw = week.stream().filter(h -> c.parent() ? h.children().contains(e.getValue()) : !h.done()).count();
            sb.append(c.x("Homework due in the next 7 days: " + hw + ".", "الواجبات المستحقة خلال 7 أيام: " + hw + ".")).append("\n\n");
        }
        List<Link> links = c.parent()
                ? List.of(new Link(c.x("My children", "أبنائي"), "#/children"), new Link(c.x("Homework", "الواجبات"), "#/homework"))
                : List.of(new Link(c.x("My results", "نتائجي"), "#/results"), new Link(c.x("Homework", "الواجبات"), "#/homework"));
        return new Answer("MARKS", sb.toString().trim(), links, followUps(c), c.ar());
    }

    private String resultsText(Ctx c, List<ResultRow> results) {
        List<ResultRow> real = results.stream().filter(r -> !r.absent() && r.value() != null && r.maxMark() != null && r.maxMark().signum() > 0).toList();
        if (real.isEmpty()) {
            return c.x("No marks have been published yet.", "لم تُنشر أي درجات بعد.");
        }
        Map<String, double[]> bySubject = new TreeMap<>();
        double total = 0;
        for (ResultRow r : real) {
            double pct = r.value().doubleValue() * 100 / r.maxMark().doubleValue();
            double[] a = bySubject.computeIfAbsent(r.subject(), k -> new double[2]);
            a[0] += pct;
            a[1]++;
            total += pct;
        }
        StringBuilder sb = new StringBuilder(c.x("Published marks: average " + Math.round(total / real.size()) + "% over " + real.size() + " assessments.",
                "الدرجات المنشورة: المعدل " + Math.round(total / real.size()) + "% في " + real.size() + " تقييمات."));
        bySubject.forEach((s, a) -> sb.append("\n• ").append(s).append(": ").append(Math.round(a[0] / a[1])).append('%'));
        return sb.toString();
    }

    private Answer attendanceAnswer(Ctx c) {
        if (c.teacher()) {
            return pointer(c, "ATTENDANCE", c.x("Attendance is recorded by the school's Attendance Clerk. You can see your class's attendance on the Attendance page.",
                    "يسجّل الحضور موظف الحضور في المدرسة. يمكنك رؤية حضور صفك في صفحة الحضور."), new Link(c.x("Attendance", "الحضور"), "#/attendance"));
        }
        StringBuilder sb = new StringBuilder();
        Map<UUID, String> who = c.parent() ? pickedChildren(c) : Map.of(c.user().personId(), c.user().name());
        for (Map.Entry<UUID, String> e : who.entrySet()) {
            StudentDashboard d = dashboard(c, e.getKey());
            sb.append(c.parent() ? e.getValue() + ": " : "").append(d == null ? c.x("not in a class yet.", "غير مسجّل في صف بعد.") : attendanceText(c, d.attendance())).append("\n\n");
        }
        Link link = c.parent() ? new Link(c.x("My children", "أبنائي"), "#/children") : new Link(c.x("My day", "يومي"), "#/mydash");
        return new Answer("ATTENDANCE", sb.toString().trim(), List.of(link), followUps(c), c.ar());
    }

    private String attendanceText(Ctx c, AttendanceSummary a) {
        if (a.present() + a.absent() + a.leave() == 0) {
            return c.x("No attendance has been recorded yet.", "لم يُسجَّل أي حضور بعد.");
        }
        String s = c.x("Attendance: " + a.present() + " present, " + a.absent() + " absent, " + a.leave() + " on leave.",
                "الحضور: " + a.present() + " حاضر، " + a.absent() + " غائب، " + a.leave() + " إجازة.");
        return a.recent().stream().filter(d -> d.status().name().equals("ABSENT")).findFirst()
                .map(d -> s + " " + c.x("Last absence: " + c.day(d.date()) + ".", "آخر غياب: " + c.day(d.date()) + ".")).orElse(s);
    }

    /** The lessons of one day (from the timetable) and, for students and parents, the homework due that day. */
    private Answer timetable(Ctx c) {
        LocalDate day = dateIn(c);
        if (day == null) {
            day = c.today();
            while (!SchoolCalendar.isSchoolDay(day)) {
                day = day.plusDays(1);
            }
        }
        final LocalDate d = day;
        if (!SchoolCalendar.isSchoolDay(d)) {
            return new Answer("TIMETABLE", c.x(c.day(d) + " is not a school day.", c.day(d) + " ليس يوم دراسة."), List.of(), followUps(c), c.ar());
        }
        int index = AcademicSetupService.dayIndex(d);
        StringBuilder sb = new StringBuilder(c.x("Lessons on " + c.day(d) + ":", "حصص يوم " + c.day(d) + ":")).append('\n');
        List<Link> links = new ArrayList<>();
        if (c.teacher()) {
            lessons(c, sb, setup.timetable(c.user(), null), index, true);
            links.add(new Link(c.x("Today", "اليوم"), "#/today"));
        } else {
            Map<UUID, String> who = c.parent() ? pickedChildren(c) : Map.of(c.user().personId(), c.user().name());
            for (Map.Entry<UUID, String> e : who.entrySet()) {
                if (c.parent()) {
                    sb.append(e.getValue()).append(":\n");
                }
                StudentDashboard dash = dashboard(c, e.getKey());
                if (dash == null) {
                    sb.append(c.x("Not in a class yet.", "غير مسجّل في صف بعد.")).append('\n');
                } else {
                    lessons(c, sb, dash.week(), index, false);
                }
            }
            List<HomeworkView> hw = homework.list(c.user(), d, d);
            if (!hw.isEmpty()) {
                sb.append('\n').append(c.x("Homework due that day:", "الواجبات المستحقة في ذلك اليوم:")).append('\n');
                hw.forEach(h -> sb.append("• ").append(h.subject()).append(" — ").append(h.title())
                        .append(c.parent() && !h.children().isEmpty() ? " (" + String.join(", ", h.children()) + ")" : "").append('\n'));
            }
            links.add(c.parent() ? new Link(c.x("My children", "أبنائي"), "#/children") : new Link(c.x("My day", "يومي"), "#/mydash"));
            links.add(new Link(c.x("Homework", "الواجبات"), "#/homework"));
        }
        return new Answer("TIMETABLE", sb.toString().trim(), links, followUps(c), c.ar());
    }

    private void lessons(Ctx c, StringBuilder sb, List<LessonView> all, int dayIndex, boolean withSection) {
        List<LessonView> today = all.stream().filter(l -> l.day() == dayIndex).sorted((a, b) -> Integer.compare(a.period(), b.period())).toList();
        if (today.isEmpty()) {
            sb.append(c.x("No lessons in the timetable.", "لا توجد حصص في الجدول.")).append('\n');
            return;
        }
        for (LessonView l : today) {
            sb.append(c.x("Period ", "الحصة ")).append(l.period()).append(": ").append(l.subject());
            if (withSection) {
                sb.append(" · ").append(l.section());
            } else if (l.teacher() != null && !l.teacher().isBlank()) {
                sb.append(" (").append(l.teacher()).append(')');
            }
            if (l.room() != null && !l.room().isBlank()) {
                sb.append(" — ").append(c.x("room ", "قاعة ")).append(l.room());
            }
            sb.append('\n');
        }
    }

    private Answer pointsAnswer(Ctx c) {
        if (c.teacher()) {
            return pointer(c, "POINTS", c.x("You can give recognition points to students you teach on the Points page.", "يمكنك منح نقاط تقدير للطلاب الذين تدرّسهم من صفحة النقاط."),
                    new Link(c.x("Points", "النقاط"), "#/points"));
        }
        StringBuilder sb = new StringBuilder();
        Map<UUID, String> who = c.parent() ? pickedChildren(c) : Map.of(c.user().personId(), c.user().name());
        for (Map.Entry<UUID, String> e : who.entrySet()) {
            PointsView p = c.parent() ? points.ofChild(c.user(), e.getKey()) : points.mine(c.user());
            int total = Math.max(0, p.total()), level = total / LEVEL_POINTS + 1, left = level * LEVEL_POINTS - total;
            String name = c.parent() ? e.getValue() : null;
            sb.append(c.x(name == null ? "You have " : name + " has ", name == null ? "لديك " : "لدى " + name + " ")).append(total)
                    .append(c.x(" points: Level " + level + ". ", " نقطة: المستوى " + level + ". "))
                    .append(c.x(left + " more points to reach Level " + (level + 1) + ".", "تلزم " + left + " نقطة للوصول إلى المستوى " + (level + 1) + ".")).append('\n');
            if (!p.entries().isEmpty()) {
                sb.append(c.x("Latest: ", "الأحدث: ")).append(p.entries().get(0).reason()).append(" (+").append(p.entries().get(0).amount()).append(")\n");
            }
            sb.append('\n');
        }
        sb.append(c.x("Points come from homework marked done, quizzes, published marks and teacher recognition.", "تأتي النقاط من الواجبات المنجزة والاختبارات والدرجات المنشورة وتقدير المعلمين."));
        Link link = c.parent() ? new Link(c.x("My children", "أبنائي"), "#/children") : new Link(c.x("My day", "يومي"), "#/mydash");
        return new Answer("POINTS", sb.toString().trim(), List.of(link), followUps(c), c.ar());
    }

    // ============================================================================== pick-up, guide, handoff

    private Answer pickup(Ctx c) {
        Link link = new Link(c.x("Pick-up", "الاستلام"), "#/pickup");
        if (c.parent()) {
            return new Answer("PICKUP", c.x("""
                    To collect your child, early or at the end of the day:
                    1. Open Pick-up.
                    2. Under your child, choose who is collecting: you, or an approved collector.
                    3. Press Pick up and accept responsibility. It is confirmed at once and the school sees it.
                    To let someone else collect, add them first under "Add a collector" on the same page.""", """
                    لاستلام ابنك، مبكراً أو في نهاية اليوم:
                    1. افتح صفحة الاستلام.
                    2. اختر تحت اسم ابنك من سيستلمه: أنت أو شخص مفوَّض.
                    3. اضغط «استلام» ووافق على تحمّل المسؤولية. يتم التأكيد فوراً وترى المدرسة ذلك.
                    ولكي يستلمه شخص آخر، أضفه أولاً من «إضافة مفوَّض» في الصفحة نفسها."""), List.of(link), followUps(c), c.ar());
        }
        if (c.student()) {
            return new Answer("PICKUP", c.x("Your parent or an approved collector picks you up using Hafez. Ask your parent to open Pick-up and press Pick up when they arrive.",
                    "يستلمك وليّ أمرك أو شخص مفوَّض عبر حافظ. اطلب من وليّ أمرك فتح صفحة الاستلام والضغط على «استلام» عند وصوله."), List.of(), followUps(c), c.ar());
        }
        return new Answer("PICKUP", c.x("The Pick-up board shows parents who have arrived and the children they have collected, live. Parents press Pick up and the child is released at once.",
                "تعرض لوحة الاستلام أولياء الأمور الذين وصلوا والأطفال الذين استلموهم مباشرة. يضغط وليّ الأمر «استلام» فيُسلَّم الطفل فوراً."), List.of(link), followUps(c), c.ar());
    }

    private static final Link PREFS_EN = new Link("Preferences", "#/prefs"), PREFS_AR = new Link("التفضيلات", "#/prefs");

    private Answer guide(Ctx c) {
        List<Guide> guides = List.of(
                new Guide(x -> x.parent() && x.has("turn off", "switch off", "disable", "stop", "off", "اطفاء", "ايقاف", "تعطيل", "الغاء")
                        && x.has("ask hafez", "chatbot", "bot", "assistant", "helper", "child", "children", "my son", "my daughter", "المساعد", "اسال حافظ", "ابني", "ابنتي", "الاطفال"),
                        Set.of(Role.PARENT), "You can turn Ask Hafez off for each of your children: open Preferences, find the Ask Hafez card, and switch off the child. Their Ask Hafez button disappears at once. You can switch it back on at any time.",
                        "يمكنك إيقاف «اسأل حافظ» لكل طفل: افتح التفضيلات، ثم بطاقة «اسأل حافظ»، وأوقف الخيار لطفلك. يختفي زر «اسأل حافظ» عنده فوراً، ويمكنك إعادته متى شئت.", List.of()),
                new Guide(x -> x.has("language", "لغه", "اللغه") && x.has("change", "switch", "set", "how", "اغير", "تغيير", "غير", "كيف"), Set.of(), "To change the language, press the language button at the top of the page, or open Preferences and choose the language of messages.",
                        "لتغيير اللغة اضغط زر اللغة أعلى الصفحة، أو افتح التفضيلات واختر لغة الرسائل.", List.of()),
                new Guide(x -> x.has("mute", "notification settings", "alerts", "الاشعارات", "التنبيهات"), Set.of(), "Open Preferences, then Messages and alerts, to choose which alerts you receive and whether you get one message per item or a daily summary.",
                        "افتح التفضيلات ثم «الرسائل والتنبيهات» لاختيار التنبيهات التي تصلك وهل تصلك رسالة لكل أمر أو ملخص يومي.", List.of()),
                new Guide(x -> x.has("photo", "picture", "phone number", "profile", "صوره", "هاتف", "الملف الشخصي"), Set.of(), "Open Preferences to change your name, phone number or profile photo.",
                        "افتح التفضيلات لتغيير اسمك أو رقم هاتفك أو صورتك.", List.of()),
                new Guide(x -> x.has("password", "forgot", "sign in", "login", "log in", "كلمه المرور", "نسيت", "تسجيل الدخول"), Set.of(), "If you cannot sign in, contact your school office or School IT. They can fix account problems.",
                        "إذا لم تتمكن من تسجيل الدخول فتواصل مع مكتب المدرسة أو قسم تقنية المعلومات في المدرسة.", List.of()),
                new Guide(x -> x.has("teacher", "teachers", "معلم", "المعلم", "المعلمه", "معلمه") && x.has("message", "chat", "contact", "talk", "write", "email", "reach", "تواصل", "راسل", "محادثه", "رساله", "اكلم"),
                        Set.of(Role.PARENT, Role.STUDENT), "Open Chat with teachers, choose a teacher of your class (or your child's class) and write your message. They are notified straight away.",
                        "افتح «الدردشة مع المعلمين» واختر أحد معلمي الصف واكتب رسالتك. يصل إشعار للمعلم فوراً.", List.of(new Link("Chat with teachers", "#/chat"))),
                new Guide(x -> x.student() && x.has("done", "finished", "finish", "complete", "completed", "tick", "submit", "انجزت", "خلصت", "سلمت", "اكملت")
                        && x.has("homework", "assignment", "واجب", "واجبات"), Set.of(Role.STUDENT),
                        "Open Homework, find the homework and tick it as done. You earn 5 points for each one. Untick it and the points are taken back.",
                        "افتح الواجبات وضع علامة «تم» على الواجب لتحصل على 5 نقاط. وإذا ألغيت العلامة تُسحب النقاط.", List.of(new Link("Homework", "#/homework"))),
                new Guide(x -> x.student() && x.has("quiz", "quizzes", "اختبار", "اختبارات", "كويز") && x.has("take", "start", "open", "find", "where", "solve", "ابدا", "اين", "اخذ", "حل"),
                        Set.of(Role.STUDENT), "Open Assessments, choose a quiz and answer its questions. You see your score right away and earn points.",
                        "افتح التقييمات واختر اختباراً وأجب عن أسئلته. تظهر لك النتيجة فوراً وتحصل على نقاط.", List.of(new Link("Assessments", "#/quizzes"))),
                new Guide(x -> x.has("notice", "notices", "announcement", "announcements", "اعلان", "اعلانات", "تعميم"), Set.of(), "School notices are on the Notices page. A notice from a class teacher also shows at the top of the home page.",
                        "إعلانات المدرسة في صفحة الإعلانات، وإعلان معلم الصف يظهر أيضاً أعلى الصفحة الرئيسية.", List.of(new Link("Notices", "#/notices"))),
                new Guide(x -> x.student() && x.has("material", "materials", "worksheet", "worksheets", "مواد", "ملفات", "ورقه عمل"), Set.of(Role.STUDENT), "Your teachers' study materials are on the Study materials page.",
                        "مواد المعلمين الدراسية في صفحة المواد الدراسية.", List.of(new Link("Study materials", "#/mymaterials"))),
                new Guide(x -> x.teacher() && x.has("set", "add", "create", "give", "post", "new", "اضف", "انشئ") && x.has("homework", "واجب", "واجبات"), Set.of(Role.TEACHER),
                        "Open Homework, press Set homework, choose the class and subject, write the title and the due date. The class and their parents are told at once.",
                        "افتح الواجبات واضغط «إضافة واجب» واختر الصف والمادة واكتب العنوان وموعد التسليم. يصل الإشعار للصف وأولياء الأمور فوراً.", List.of(new Link("Homework", "#/homework"))));
        for (Guide g : guides) {
            if (g.roles().isEmpty() || g.roles().contains(c.user().role())) {
                if (g.when().test(c)) {
                    List<Link> links = new ArrayList<>(g.links().stream().map(l -> localised(c, l)).toList());
                    if (links.isEmpty() || g.en().contains("Preferences")) {
                        links.add(c.ar() ? PREFS_AR : PREFS_EN);
                    }
                    return new Answer("GUIDE", c.x(g.en(), g.ar()), links, followUps(c), c.ar());
                }
            }
        }
        return null;
    }

    private Link localised(Ctx c, Link l) {
        if (!c.ar()) {
            return l;
        }
        return new Link(switch (l.href()) {
            case "#/chat" -> "الدردشة مع المعلمين";
            case "#/homework" -> "الواجبات";
            case "#/quizzes" -> "التقييمات";
            case "#/notices" -> "الإعلانات";
            case "#/mymaterials" -> "المواد الدراسية";
            default -> l.label();
        }, l.href());
    }

    private Answer sensitive(Ctx c) {
        String en = "This is something a person should help with, so I will not answer it here. ", ar = "هذا أمر يحسن أن يتولاه شخص مختص، لذلك لن أجيب عنه هنا. ";
        if (c.teacher()) {
            return new Answer("SENSITIVE", c.x(en + "Please speak to your principal or open a support ticket.", ar + "تحدّث إلى مدير المدرسة أو افتح تذكرة دعم."),
                    List.of(new Link(c.x("Support tickets", "تذاكر الدعم"), "#/support")), List.of(), c.ar());
        }
        return new Answer("SENSITIVE", c.x(en + "Please write to your child's teacher in Chat, or contact the school office.", ar + "يرجى مراسلة معلم طفلك عبر الدردشة أو التواصل مع مكتب المدرسة."),
                List.of(new Link(c.x("Chat with teachers", "الدردشة مع المعلمين"), "#/chat")), List.of(), c.ar());
    }

    private Answer unknown(Ctx c) {
        Answer help = help(c, false);
        String more = c.teacher()
                ? c.x("\n\nIf you need more, open a support ticket.", "\n\nإذا احتجت المزيد فافتح تذكرة دعم.")
                : c.x("\n\nIf you need a person, write to a teacher in Chat or contact the school office.", "\n\nإذا احتجت إلى شخص فراسل أحد المعلمين عبر الدردشة أو تواصل مع مكتب المدرسة.");
        List<Link> links = c.teacher() ? List.of(new Link(c.x("Support tickets", "تذاكر الدعم"), "#/support")) : List.of(new Link(c.x("Chat with teachers", "الدردشة مع المعلمين"), "#/chat"));
        return new Answer("UNKNOWN", c.x("I don't know the answer to that. ", "لا أعرف إجابة هذا السؤال. ") + help.text() + more, links, help.suggestions(), c.ar());
    }

    private Answer help(Ctx c, boolean greeting) {
        String what = c.teacher()
                ? c.x("I can tell you which homework is due, who has not handed it in, your timetable, and how to do things in Hafez.", "يمكنني إخبارك بالواجبات المستحقة ومن لم يسلّمها وجدولك وكيفية استخدام حافظ.")
                : c.parent()
                ? c.x("I can tell you about your children's marks, attendance, homework and quizzes, timetable, points, and how to pick them up.", "يمكنني إخبارك بدرجات أبنائك وحضورهم وواجباتهم واختباراتهم وجدولهم ونقاطهم وكيفية استلامهم.")
                : c.x("I can tell you about your homework and quizzes, timetable, marks, attendance and points.", "يمكنني إخبارك بواجباتك واختباراتك وجدولك ودرجاتك وحضورك ونقاطك.");
        String hello = greeting ? c.x("Hello! I am Ask Hafez. ", "مرحباً! أنا «اسأل حافظ». ") : "";
        return new Answer("HELP", hello + what, List.of(), followUps(c), c.ar());
    }

    private Answer pointer(Ctx c, String intent, String text, Link... links) {
        return new Answer(intent, text, List.of(links), followUps(c), c.ar());
    }

    // ============================================================================== helpers

    private List<String> followUps(Ctx c) {
        if (c.teacher()) {
            return c.ar() ? List.of("من لم يسلّم الواجب هذا الأسبوع؟", "ما جدولي غداً؟", "ما الواجبات المستحقة هذا الأسبوع؟")
                    : List.of("Who has not handed in homework this week?", "What is my timetable tomorrow?", "What homework is due this week?");
        }
        if (c.parent()) {
            String kid = people.names(people.childrenOf(c.user().personId())).values().stream().findFirst().orElse(null);
            return c.ar() ? List.of(kid == null ? "كيف حال أبنائي هذا الفصل؟" : "كيف حال " + kid + " هذا الفصل؟", "هل يوجد واجب مستحق غداً؟", "كيف أستلم ابني مبكراً؟")
                    : List.of(kid == null ? "How are my children doing this term?" : "How is " + kid + " doing this term?", "Is there homework due tomorrow?", "How do I pick up my child early?");
        }
        return c.ar() ? List.of("ماذا لدي يوم الأحد؟", "كم نقطة أحتاج للمستوى التالي؟", "هل يوجد واجب مستحق غداً؟")
                : List.of("What do I have on Sunday?", "How many points to the next level?", "Is there homework due tomorrow?");
    }

    /** The parent's children the question is about: those named in it, else all of them. */
    private Map<UUID, String> pickedChildren(Ctx c) {
        List<UUID> ids = people.childrenOf(c.user().personId());
        Map<UUID, String> names = people.names(ids);
        Map<UUID, String> all = new LinkedHashMap<>(), named = new LinkedHashMap<>();
        for (UUID id : ids) {
            String name = names.getOrDefault(id, "");
            all.put(id, name);
            for (String part : SPLIT.split(norm(name))) {
                if (part.length() >= 3 && c.norm().contains(part)) {
                    named.put(id, name);
                }
            }
        }
        return named.isEmpty() ? all : named;
    }

    private static UUID UUIDof(Map<UUID, String> map, String name) {
        return map.entrySet().stream().filter(e -> e.getValue().equals(name)).map(Map.Entry::getKey).findFirst().orElseThrow();
    }

    /** One student's home data, read as the parent (or the student). Null when the child is not in a class yet. */
    private StudentDashboard dashboard(Ctx c, UUID studentId) {
        try {
            return c.parent() ? parents.child(c.user(), studentId) : portal.studentDashboard(c.user());
        } catch (ApiException e) {
            return null;
        }
    }

    /** A day named in the question: today, tomorrow, or a weekday (the next one counting today). */
    private LocalDate dateIn(Ctx c) {
        if (c.has("tomorrow", "غدا", "بكره", "بكرا")) {
            return c.today().plusDays(1);
        }
        if (c.has("today", "اليوم")) {
            return c.today();
        }
        Map<DayOfWeek, String[]> names = new LinkedHashMap<>();
        names.put(DayOfWeek.SUNDAY, new String[] {"sunday", "sun", "الاحد", "احد"});
        names.put(DayOfWeek.MONDAY, new String[] {"monday", "mon", "الاثنين", "الاثنين"});
        names.put(DayOfWeek.TUESDAY, new String[] {"tuesday", "tue", "tues", "الثلاثاء"});
        names.put(DayOfWeek.WEDNESDAY, new String[] {"wednesday", "wed", "الاربعاء"});
        names.put(DayOfWeek.THURSDAY, new String[] {"thursday", "thu", "thurs", "الخميس"});
        names.put(DayOfWeek.FRIDAY, new String[] {"friday", "fri", "الجمعه"});
        names.put(DayOfWeek.SATURDAY, new String[] {"saturday", "sat", "السبت"});
        for (Map.Entry<DayOfWeek, String[]> e : names.entrySet()) {
            if (c.has(e.getValue())) {
                int ahead = (e.getKey().getValue() - c.today().getDayOfWeek().getValue() + 7) % 7;
                return c.today().plusDays(ahead);
            }
        }
        return null;
    }

    private static boolean hasArabic(String s) {
        return s.chars().anyMatch(ch -> ch >= 0x0600 && ch <= 0x06FF);
    }

    /** Lower case, no Arabic vowel marks, and the common Arabic letter variants folded together. */
    static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "").replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي').replace('ة', 'ه').replace("'", "").replace("’", "");
    }
}
