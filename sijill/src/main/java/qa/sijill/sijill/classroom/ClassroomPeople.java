package qa.sijill.sijill.classroom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import qa.sijill.sijill.shared.events.PersonNotice;
import qa.sijill.sijill.shared.model.NotificationKind;

/**
 * Who is in which class, read straight from the school tables: a student's section, a parent's children, the students
 * of a section and their parents, and which section and subject a teacher teaches. Shared by homework, quizzes and chat.
 */
@Component
@RequiredArgsConstructor
public class ClassroomPeople {

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    /** One section and subject a teacher teaches. */
    public record Pair(UUID sectionId, UUID subjectId) {}

    public Optional<UUID> sectionOf(UUID studentId) {
        return jdbc.queryForList("SELECT section_id FROM enrolments WHERE student_id = ?", UUID.class, studentId).stream().findFirst();
    }

    public List<UUID> childrenOf(UUID parentId) {
        return jdbc.queryForList("SELECT student_id FROM parent_links WHERE parent_id = ? ORDER BY created_at", UUID.class, parentId);
    }

    public List<UUID> studentsOf(UUID sectionId) {
        return jdbc.queryForList("SELECT student_id FROM enrolments WHERE section_id = ?", UUID.class, sectionId);
    }

    public List<UUID> parentsOf(Collection<UUID> students) {
        List<UUID> out = new ArrayList<>();
        for (UUID s : students) {
            out.addAll(jdbc.queryForList("SELECT parent_id FROM parent_links WHERE student_id = ?", UUID.class, s));
        }
        return out;
    }

    /** The students of a section and their parents, once each. */
    public Set<UUID> familiesOf(UUID sectionId) {
        List<UUID> students = studentsOf(sectionId);
        Set<UUID> out = new LinkedHashSet<>(students);
        out.addAll(parentsOf(students));
        return out;
    }

    public List<Pair> pairsOf(UUID teacherId) {
        return jdbc.query("SELECT section_id, subject_id FROM teaching_assignments WHERE teacher_id = ?",
                (rs, i) -> new Pair(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), teacherId);
    }

    public boolean teaches(UUID teacherId, UUID sectionId, UUID subjectId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM teaching_assignments WHERE teacher_id = ? AND section_id = ? AND subject_id = ?",
                Integer.class, teacherId, sectionId, subjectId);
        return n != null && n > 0;
    }

    /** Teacher id and subject name for each subject taught in a section. */
    public List<Map<String, Object>> teachersOf(UUID sectionId) {
        return jdbc.queryForList("SELECT ta.teacher_id, s.name AS subject FROM teaching_assignments ta JOIN subjects s ON s.id = ta.subject_id "
                + "WHERE ta.section_id = ? ORDER BY s.name", sectionId);
    }

    public Map<UUID, String> sectionLabels(UUID siteId) {
        Map<UUID, String> out = new HashMap<>();
        jdbc.query("SELECT id, grade, name FROM sections WHERE site_id = ?",
                rs -> { out.put(rs.getObject(1, UUID.class), rs.getInt(2) + rs.getString(3)); }, siteId);
        return out;
    }

    public Map<UUID, String> subjectNames(UUID siteId) {
        Map<UUID, String> out = new HashMap<>();
        jdbc.query("SELECT id, name FROM subjects WHERE site_id = ?", rs -> { out.put(rs.getObject(1, UUID.class), rs.getString(2)); }, siteId);
        return out;
    }

    public Map<UUID, String> names(Collection<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            jdbc.query("SELECT display_name FROM people WHERE id = ?", rs -> { out.put(id, rs.getString(1)); }, id);
        }
        return out;
    }

    public String name(UUID id) {
        return names(List.of(id)).getOrDefault(id, "");
    }

    public String roleOf(UUID id) {
        return jdbc.queryForList("SELECT role FROM people WHERE id = ?", String.class, id).stream().findFirst().orElse("");
    }

    /** A bell entry for each person (not the author). */
    public void tell(Collection<UUID> people, UUID author, NotificationKind kind, String titleAr, String titleEn, String body) {
        tell(people, author, kind, titleAr, titleEn, body, null);
    }

    /** The same, with the text in Arabic as well (shown when the screen is in Arabic). */
    public void tell(Collection<UUID> people, UUID author, NotificationKind kind, String titleAr, String titleEn, String body, String bodyAr) {
        for (UUID id : new LinkedHashSet<>(people)) {
            if (!id.equals(author)) {
                events.publishEvent(new PersonNotice(id, kind, titleAr, titleEn, body, bodyAr));
            }
        }
    }
}
