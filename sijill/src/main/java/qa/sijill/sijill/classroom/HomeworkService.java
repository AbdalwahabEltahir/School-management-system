package qa.sijill.sijill.classroom;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.academic.model.PointSource;
import qa.sijill.sijill.academic.service.SchoolworkPoints;
import qa.sijill.sijill.classroom.ClassroomDtos.HomeworkRequest;
import qa.sijill.sijill.classroom.ClassroomDtos.HomeworkView;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Homework: a teacher sets it for a section and subject they teach, with a due date. Students see their section's,
 * parents see their children's, teachers see what they set, all as a day-by-day schedule built by the screen.
 */
@Service
@RequiredArgsConstructor
public class HomeworkService {

    private static final int MAX_DAYS = 62;

    private final JdbcTemplate jdbc;
    private final ClassroomPeople people;
    private final SchoolworkPoints points;

    private record Row(UUID id, UUID sectionId, UUID subjectId, UUID teacherId, String title, String details, LocalDate dueOn,
                       Instant createdAt) {}

    @Transactional
    public HomeworkView add(CurrentUser user, HomeworkRequest r) {
        if (!user.isAny(Role.TEACHER, Role.COORDINATOR) || !people.teaches(user.personId(), r.sectionId(), r.subjectId())) {
            throw new ForbiddenException("Only the teacher of this subject for this class can set its homework.");
        }
        String title = r.title().trim();
        if (title.length() > 200) {
            throw new BadRequestException("Please keep the title under 200 characters.");
        }
        String details = r.details() == null ? "" : r.details().trim();
        if (details.length() > 4000) {
            throw new BadRequestException("Please keep the details under 4000 characters.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO homework (id, org_id, site_id, section_id, subject_id, teacher_id, title, details, due_on) VALUES (?,?,?,?,?,?,?,?,?)",
                id, user.orgId(), user.siteId(), r.sectionId(), r.subjectId(), user.personId(), title, details, Date.valueOf(r.dueOn()));
        String subject = people.subjectNames(user.siteId()).getOrDefault(r.subjectId(), "");
        people.tell(people.familiesOf(r.sectionId()), user.personId(), NotificationKind.CLASSWORK,
                "واجب جديد: " + subject + " — " + title, "New homework: " + subject + " — " + title, "Due " + r.dueOn() + ". " + details,
                "موعد التسليم " + r.dueOn() + ". " + details);
        return views(user, rows("WHERE id = ?", id)).get(0);
    }

    /** Homework due between {@code from} and {@code to} (inclusive) for this person. */
    @Transactional(readOnly = true)
    public List<HomeworkView> list(CurrentUser user, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new BadRequestException("Choose a range of at most " + MAX_DAYS + " days.");
        }
        Date f = Date.valueOf(from), t = Date.valueOf(to);
        if (user.isAny(Role.TEACHER, Role.COORDINATOR)) {
            return views(user, rows("WHERE teacher_id = ? AND due_on BETWEEN ? AND ?", user.personId(), f, t));
        }
        if (user.is(Role.STUDENT)) {
            UUID section = people.sectionOf(user.personId()).orElse(null);
            return section == null ? List.of() : views(user, rows("WHERE section_id = ? AND due_on BETWEEN ? AND ?", section, f, t));
        }
        if (user.is(Role.PARENT)) {
            Set<UUID> sections = new HashSet<>();
            for (UUID child : people.childrenOf(user.personId())) {
                people.sectionOf(child).ifPresent(sections::add);
            }
            List<Row> out = new ArrayList<>();
            for (UUID s : sections) {
                out.addAll(rows("WHERE section_id = ? AND due_on BETWEEN ? AND ?", s, f, t));
            }
            return views(user, out);
        }
        throw new ForbiddenException("Homework is for teachers, students and parents.");
    }

    @Transactional
    public void remove(CurrentUser user, UUID id) {
        Row row = rows("WHERE id = ? AND site_id = ?", id, user.siteId()).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("The homework"));
        if (!row.teacherId().equals(user.personId())) {
            throw new ForbiddenException("Only the teacher who set this homework can remove it.");
        }
        jdbc.update("DELETE FROM homework WHERE id = ?", id);
        points.revokeAll(PointSource.HOMEWORK, id);
    }

    /** A student marks their homework as done (and earns its points), or un-ticks it (and the points go). */
    @Transactional
    public HomeworkView setDone(CurrentUser user, UUID id, boolean done) {
        if (!user.is(Role.STUDENT)) {
            throw new ForbiddenException("Only the student marks their homework as done.");
        }
        Row row = rows("WHERE id = ? AND site_id = ?", id, user.siteId()).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("The homework"));
        if (!people.sectionOf(user.personId()).map(s -> s.equals(row.sectionId())).orElse(false)) {
            throw new NotFoundException("The homework");
        }
        if (done) {
            jdbc.update("INSERT INTO homework_done (homework_id, student_id) VALUES (?, ?) ON CONFLICT DO NOTHING", id, user.personId());
            String subject = people.subjectNames(user.siteId()).getOrDefault(row.subjectId(), "");
            points.award(user.orgId(), user.siteId(), user.personId(), PointSource.HOMEWORK, id, SchoolworkPoints.HOMEWORK,
                    "Homework: " + subject + " — " + row.title());
        } else {
            jdbc.update("DELETE FROM homework_done WHERE homework_id = ? AND student_id = ?", id, user.personId());
            points.revoke(user.personId(), PointSource.HOMEWORK, id);
        }
        return views(user, List.of(row)).get(0);
    }

    private Set<UUID> doneBy(UUID studentId) {
        return new HashSet<>(jdbc.queryForList("SELECT homework_id FROM homework_done WHERE student_id = ?", UUID.class, studentId));
    }

    private int doneCount(UUID homeworkId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM homework_done WHERE homework_id = ?", Integer.class, homeworkId);
        return n == null ? 0 : n;
    }

    private List<Row> rows(String where, Object... args) {
        return jdbc.query("SELECT id, section_id, subject_id, teacher_id, title, details, due_on, created_at FROM homework " + where
                        + " ORDER BY due_on, created_at",
                (rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getDate(7).toLocalDate(),
                        rs.getTimestamp(8).toInstant()), args);
    }

    private List<HomeworkView> views(CurrentUser user, List<Row> rows) {
        Map<UUID, String> sections = people.sectionLabels(user.siteId());
        Map<UUID, String> subjects = people.subjectNames(user.siteId());
        Map<UUID, String> teachers = people.names(rows.stream().map(Row::teacherId).toList());
        Map<UUID, List<String>> kidsBySection = new HashMap<>();
        Map<UUID, List<UUID>> kidIdsBySection = new HashMap<>();
        if (user.is(Role.PARENT)) {
            Map<UUID, String> kidNames = people.names(people.childrenOf(user.personId()));
            kidNames.forEach((kid, name) -> people.sectionOf(kid).ifPresent(s -> {
                kidsBySection.computeIfAbsent(s, k -> new ArrayList<>()).add(name);
                kidIdsBySection.computeIfAbsent(s, k -> new ArrayList<>()).add(kid);
            }));
        }
        Set<UUID> mineDone = user.is(Role.STUDENT) ? doneBy(user.personId()) : Set.of();
        Map<UUID, Set<UUID>> kidDone = new HashMap<>();
        kidIdsBySection.values().forEach(kids -> kids.forEach(k -> kidDone.put(k, doneBy(k))));
        boolean teacher = user.isAny(Role.TEACHER, Role.COORDINATOR);
        Map<UUID, Integer> classSize = new HashMap<>();
        return rows.stream().sorted((a, b) -> a.dueOn().compareTo(b.dueOn()))
                .map(r -> new HomeworkView(r.id(), r.sectionId(), sections.getOrDefault(r.sectionId(), ""), r.subjectId(),
                        subjects.getOrDefault(r.subjectId(), ""), teachers.getOrDefault(r.teacherId(), ""), r.title(), r.details(),
                        r.dueOn(), r.createdAt(), r.teacherId().equals(user.personId()), kidsBySection.getOrDefault(r.sectionId(), List.of()),
                        user.is(Role.STUDENT) ? mineDone.contains(r.id())
                                : user.is(Role.PARENT) && kidIdsBySection.getOrDefault(r.sectionId(), List.of()).stream().allMatch(k -> kidDone.get(k).contains(r.id()))
                                        && !kidIdsBySection.getOrDefault(r.sectionId(), List.of()).isEmpty(),
                        teacher ? doneCount(r.id()) : 0,
                        teacher ? classSize.computeIfAbsent(r.sectionId(), s -> people.studentsOf(s).size()) : 0))
                .toList();
    }
}
