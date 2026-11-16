package qa.sijill.sijill.classroom;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.academic.model.PointSource;
import qa.sijill.sijill.academic.service.SchoolworkPoints;
import qa.sijill.sijill.classroom.ClassroomDtos.AttemptRow;
import qa.sijill.sijill.classroom.ClassroomDtos.QuestionInput;
import qa.sijill.sijill.classroom.ClassroomDtos.QuestionView;
import qa.sijill.sijill.classroom.ClassroomDtos.QuizDetail;
import qa.sijill.sijill.classroom.ClassroomDtos.QuizRequest;
import qa.sijill.sijill.classroom.ClassroomDtos.QuizSummary;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Quizzes (the "Assessments" tab): a teacher writes multiple-choice questions for a class, with the source material,
 * the right answer and an explanation for each. A student takes the quiz once; it is marked as soon as it is sent,
 * and the student then sees the grade and the explanation of every answer. The right answers never reach a student
 * before they have submitted.
 */
@Service
@RequiredArgsConstructor
public class QuizService {

    private static final int MAX_QUESTIONS = 50;
    private static final int MAX_OPTIONS = 6;

    private final JdbcTemplate jdbc;
    private final ClassroomPeople people;
    private final SchoolworkPoints points;

    private record Quiz(UUID id, UUID siteId, UUID sectionId, UUID subjectId, UUID teacherId, String title, String material,
                        String link, Instant createdAt) {}

    private record Question(int position, String kind, String prompt, List<String> options, int correct, String accepted, String explanation) {

        /** Is this answer right? A written answer matches one of the accepted ones, ignoring capitals, spaces and end punctuation. */
        boolean right(int chosen, String written) {
            if ("SHORT".equals(kind)) {
                String w = norm(written);
                return !w.isEmpty() && Arrays.stream(accepted.split("\n")).map(QuizService::norm).anyMatch(w::equals);
            }
            return chosen == correct;
        }
    }

    private record Attempt(UUID studentId, List<Integer> answers, List<String> written, int score, int total, Instant at) {}

    static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").replaceAll("[.!?،؟]+$", "").trim();
    }

    @Transactional
    public QuizDetail create(CurrentUser user, QuizRequest r) {
        if (!user.isAny(Role.TEACHER, Role.COORDINATOR) || !people.teaches(user.personId(), r.sectionId(), r.subjectId())) {
            throw new ForbiddenException("Only the teacher of this subject for this class can set its quizzes.");
        }
        List<QuestionInput> qs = r.questions() == null ? List.of() : r.questions();
        if (qs.isEmpty()) {
            throw new BadRequestException("Add at least one question.");
        }
        if (qs.size() > MAX_QUESTIONS) {
            throw new BadRequestException("A quiz can have at most " + MAX_QUESTIONS + " questions.");
        }
        String link = r.sourceLink() == null || r.sourceLink().isBlank() ? null : r.sourceLink().trim();
        if (link != null && !link.startsWith("https://")) {
            throw new BadRequestException("The source link must start with https://");
        }
        List<Question> clean = new ArrayList<>();
        for (int i = 0; i < qs.size(); i++) {
            QuestionInput q = qs.get(i);
            String kind = q.kind() == null || q.kind().isBlank() ? "CHOICE" : q.kind();
            if (!List.of("CHOICE", "TRUEFALSE", "SHORT").contains(kind)) {
                throw new BadRequestException("Question " + (i + 1) + " has an unknown type.");
            }
            if (q.prompt() == null || q.prompt().isBlank()) {
                throw new BadRequestException("Question " + (i + 1) + " needs its text.");
            }
            String explanation = q.explanation() == null ? "" : q.explanation().trim();
            if ("SHORT".equals(kind)) {
                String accepted = q.accepted() == null ? "" : Arrays.stream(q.accepted().split("\n")).map(String::trim).filter(x -> !x.isEmpty())
                        .collect(Collectors.joining("\n"));
                if (accepted.isEmpty() || accepted.length() > 1000) {
                    throw new BadRequestException("Write the accepted answer(s) of question " + (i + 1) + ", one per line.");
                }
                clean.add(new Question(i + 1, kind, q.prompt().trim(), List.of(), -1, accepted, explanation));
                continue;
            }
            List<String> options = "TRUEFALSE".equals(kind) ? List.of("True", "False") : q.options() == null ? List.of()
                    : q.options().stream().map(o -> o == null ? "" : o.replace('\n', ' ').trim()).filter(o -> !o.isEmpty()).toList();
            if (options.size() < 2 || options.size() > MAX_OPTIONS) {
                throw new BadRequestException("Question " + (i + 1) + " needs between 2 and " + MAX_OPTIONS + " answers to choose from.");
            }
            if (q.correctIndex() < 0 || q.correctIndex() >= options.size()) {
                throw new BadRequestException("Choose the correct answer of question " + (i + 1) + ".");
            }
            clean.add(new Question(i + 1, kind, q.prompt().trim(), options, q.correctIndex(), "", explanation));
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO quizzes (id, org_id, site_id, section_id, subject_id, teacher_id, title, source_material, source_link) VALUES (?,?,?,?,?,?,?,?,?)",
                id, user.orgId(), user.siteId(), r.sectionId(), r.subjectId(), user.personId(), r.title().trim(),
                r.sourceMaterial() == null ? "" : r.sourceMaterial().trim(), link);
        for (Question q : clean) {
            jdbc.update("INSERT INTO quiz_questions (quiz_id, position, kind, prompt, options, correct_index, accepted, explanation) VALUES (?,?,?,?,?,?,?,?)",
                    id, q.position(), q.kind(), q.prompt(), String.join("\n", q.options()), q.correct(), q.accepted(), q.explanation());
        }
        String subject = people.subjectNames(user.siteId()).getOrDefault(r.subjectId(), "");
        people.tell(people.studentsOf(r.sectionId()), user.personId(), NotificationKind.CLASSWORK,
                "اختبار جديد: " + subject + " — " + r.title().trim(), "New quiz: " + subject + " — " + r.title().trim(),
                clean.size() + " questions. Open Assessments to take it.", "عدد الأسئلة: " + clean.size() + ". افتح التقييمات لأدائه.");
        return detail(user, id);
    }

    @Transactional(readOnly = true)
    public List<QuizSummary> list(CurrentUser user) {
        List<Quiz> quizzes;
        if (user.isAny(Role.TEACHER, Role.COORDINATOR)) {
            quizzes = quizzes("WHERE teacher_id = ?", user.personId());
        } else if (user.is(Role.STUDENT)) {
            UUID section = people.sectionOf(user.personId()).orElse(null);
            quizzes = section == null ? List.of() : quizzes("WHERE section_id = ?", section);
        } else {
            throw new ForbiddenException("Quizzes are for teachers and students.");
        }
        Map<UUID, String> sections = people.sectionLabels(user.siteId());
        Map<UUID, String> subjects = people.subjectNames(user.siteId());
        Map<UUID, String> teachers = people.names(quizzes.stream().map(Quiz::teacherId).toList());
        return quizzes.stream().map(q -> summary(user, q, sections, subjects, teachers)).toList();
    }

    @Transactional(readOnly = true)
    public QuizDetail detail(CurrentUser user, UUID id) {
        Quiz q = visible(user, id);
        List<Question> questions = questions(id);
        boolean teacher = q.teacherId().equals(user.personId());
        Attempt mine = user.is(Role.STUDENT) ? attempts(id).stream().filter(a -> a.studentId().equals(user.personId())).findFirst().orElse(null) : null;
        boolean reveal = teacher || mine != null;
        List<QuestionView> views = questions.stream().map(x -> {
            int k = x.position() - 1;
            Integer chosen = mine != null && k < mine.answers().size() ? mine.answers().get(k) : null;
            String written = mine != null && k < mine.written().size() ? mine.written().get(k) : null;
            return new QuestionView(x.position(), x.kind(), x.prompt(), x.options(), reveal && !"SHORT".equals(x.kind()) ? x.correct() : null,
                    reveal ? x.accepted() : null, reveal ? x.explanation() : null, chosen, written,
                    mine == null ? null : x.right(chosen == null ? -1 : chosen, written));
        }).toList();
        List<AttemptRow> attemptRows = List.of();
        if (teacher) {
            List<Attempt> all = attempts(id);
            Map<UUID, String> names = people.names(all.stream().map(Attempt::studentId).toList());
            attemptRows = all.stream().map(a -> new AttemptRow(a.studentId(), names.getOrDefault(a.studentId(), ""), a.score(), a.total(), a.at()))
                    .sorted((a, b) -> a.student().compareToIgnoreCase(b.student())).toList();
        }
        Map<UUID, String> sections = people.sectionLabels(user.siteId());
        Map<UUID, String> subjects = people.subjectNames(user.siteId());
        Map<UUID, String> teachers = people.names(List.of(q.teacherId()));
        return new QuizDetail(summary(user, q, sections, subjects, teachers), q.material(), q.link(), views, attemptRows);
    }

    /** Marks the student's answers at once and returns the quiz with the grade and every explanation. */
    @Transactional
    public QuizDetail submit(CurrentUser user, UUID id, List<Integer> answers, List<String> written) {
        if (!user.is(Role.STUDENT)) {
            throw new ForbiddenException("Only students take quizzes.");
        }
        visible(user, id);
        if (attempts(id).stream().anyMatch(a -> a.studentId().equals(user.personId()))) {
            throw new ConflictException("You have already taken this quiz.");
        }
        List<Question> questions = questions(id);
        List<Integer> given = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        int score = 0;
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            Integer a = answers != null && i < answers.size() ? answers.get(i) : null;
            int chosen = a == null || a < 0 || a >= q.options().size() ? -1 : a;
            String text = "SHORT".equals(q.kind()) && written != null && i < written.size() && written.get(i) != null
                    ? written.get(i).replace('\n', ' ').trim() : "";
            if (text.length() > 300) {
                text = text.substring(0, 300);
            }
            given.add(chosen);
            texts.add(text);
            if (q.right(chosen, text)) {
                score++;
            }
        }
        jdbc.update("INSERT INTO quiz_attempts (quiz_id, student_id, answers, written, score, total) VALUES (?,?,?,?,?,?)", id, user.personId(),
                given.stream().map(String::valueOf).collect(Collectors.joining(",")), String.join("\n", texts), score, questions.size());
        Quiz q = quizzes("WHERE id = ?", id).get(0);
        points.award(user.orgId(), user.siteId(), user.personId(), PointSource.QUIZ, id, SchoolworkPoints.quizPoints(score, questions.size()),
                "Quiz: " + q.title() + " (" + score + "/" + questions.size() + ")");
        return detail(user, id);
    }

    @Transactional
    public void remove(CurrentUser user, UUID id) {
        Quiz q = quizzes("WHERE id = ? AND site_id = ?", id, user.siteId()).stream().findFirst().orElseThrow(() -> new NotFoundException("The quiz"));
        if (!q.teacherId().equals(user.personId())) {
            throw new ForbiddenException("Only the teacher who wrote this quiz can remove it.");
        }
        jdbc.update("DELETE FROM quizzes WHERE id = ?", id);
        points.revokeAll(PointSource.QUIZ, id);
    }

    // ---- helpers

    /** The quiz, if this person may open it: its teacher, or a student of its class. */
    private Quiz visible(CurrentUser user, UUID id) {
        Quiz q = quizzes("WHERE id = ? AND site_id = ?", id, user.siteId()).stream().findFirst().orElseThrow(() -> new NotFoundException("The quiz"));
        boolean ok = q.teacherId().equals(user.personId())
                || (user.is(Role.STUDENT) && people.sectionOf(user.personId()).map(s -> s.equals(q.sectionId())).orElse(false));
        if (!ok) {
            throw new NotFoundException("The quiz");
        }
        return q;
    }

    private QuizSummary summary(CurrentUser user, Quiz q, Map<UUID, String> sections, Map<UUID, String> subjects, Map<UUID, String> teachers) {
        List<Attempt> all = attempts(q.id());
        int n = questionCount(q.id());
        Attempt mine = all.stream().filter(a -> a.studentId().equals(user.personId())).findFirst().orElse(null);
        Integer avg = all.isEmpty() ? null : (int) Math.round(all.stream().mapToDouble(a -> a.total() == 0 ? 0 : 100.0 * a.score() / a.total()).average().orElse(0));
        boolean teacher = q.teacherId().equals(user.personId());
        return new QuizSummary(q.id(), q.sectionId(), sections.getOrDefault(q.sectionId(), ""), q.subjectId(),
                subjects.getOrDefault(q.subjectId(), ""), teachers.getOrDefault(q.teacherId(), ""), q.title(), n, q.createdAt(),
                mine != null, mine == null ? null : mine.score(), mine == null ? null : mine.total(),
                teacher ? all.size() : 0, teacher ? avg : null, teacher);
    }

    private List<Quiz> quizzes(String where, Object... args) {
        return jdbc.query("SELECT id, site_id, section_id, subject_id, teacher_id, title, source_material, source_link, created_at FROM quizzes "
                        + where + " ORDER BY created_at DESC",
                (rs, i) -> new Quiz(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getTimestamp(9).toInstant()), args);
    }

    private List<Question> questions(UUID quizId) {
        return jdbc.query("SELECT position, kind, prompt, options, correct_index, accepted, explanation FROM quiz_questions WHERE quiz_id = ? ORDER BY position",
                (rs, i) -> new Question(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getString(4).isEmpty() ? List.of() : Arrays.asList(rs.getString(4).split("\n")), rs.getInt(5), rs.getString(6), rs.getString(7)), quizId);
    }

    private int questionCount(UUID quizId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM quiz_questions WHERE quiz_id = ?", Integer.class, quizId);
        return n == null ? 0 : n;
    }

    private List<Attempt> attempts(UUID quizId) {
        return jdbc.query("SELECT student_id, answers, written, score, total, submitted_at FROM quiz_attempts WHERE quiz_id = ?",
                (rs, i) -> new Attempt(rs.getObject(1, UUID.class),
                        Arrays.stream(rs.getString(2).split(",")).filter(s -> !s.isBlank()).map(Integer::valueOf).toList(),
                        Arrays.asList(rs.getString(3).split("\n", -1)), rs.getInt(4), rs.getInt(5), rs.getTimestamp(6).toInstant()), quizId);
    }
}
