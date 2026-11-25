package qa.sijill.sijill.family;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import qa.sijill.sijill.classroom.ClassroomPeople;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.shared.util.SchoolCalendar;

/** Who may see and do what in the family features, and the small lookups they share. */
@Component
@RequiredArgsConstructor
public class FamilyAccess {

    private final JdbcTemplate jdbc;
    private final ClassroomPeople people;
    private final Clock clock;

    public ClassroomPeople people() {
        return people;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(SchoolCalendar.ZONE));
    }

    public Instant now() {
        return Instant.now(clock);
    }

    /** A parent and one of their linked children; anything else reads as "not found", so ids are not guessable. */
    public void requireParentOf(CurrentUser user, UUID studentId) {
        if (!user.is(Role.PARENT) || !people.childrenOf(user.personId()).contains(studentId)) {
            throw new NotFoundException("The child");
        }
    }

    public void requireParent(CurrentUser user) {
        if (!user.is(Role.PARENT)) {
            throw new ForbiddenException("This is for parents.");
        }
    }

    public Optional<UUID> sectionOf(UUID studentId) {
        return people.sectionOf(studentId);
    }

    public String sectionLabel(UUID sectionId) {
        if (sectionId == null) {
            return "";
        }
        return jdbc.query("SELECT grade, name FROM sections WHERE id = ?", rs -> rs.next() ? rs.getInt(1) + rs.getString(2) : "", sectionId);
    }

    /** Staff of the school with one of these roles who can sign in. */
    public List<UUID> staff(UUID siteId, Role... roles) {
        List<UUID> out = new ArrayList<>();
        for (Role r : roles) {
            out.addAll(jdbc.queryForList("SELECT id FROM people WHERE site_id = ? AND role = ? AND status = 'ACTIVE'", UUID.class, siteId, r.name()));
        }
        return out;
    }

    /** The teachers of a class (every subject). */
    public Set<UUID> classTeachers(UUID sectionId) {
        return new LinkedHashSet<>(jdbc.queryForList("SELECT DISTINCT teacher_id FROM teaching_assignments WHERE section_id = ?", UUID.class, sectionId));
    }

    /** The sections a teacher teaches in. */
    public Set<UUID> sectionsTaughtBy(UUID teacherId) {
        return new LinkedHashSet<>(jdbc.queryForList("SELECT DISTINCT section_id FROM teaching_assignments WHERE teacher_id = ?", UUID.class, teacherId));
    }

    public boolean sectionInSite(UUID sectionId, UUID siteId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM sections WHERE id = ? AND site_id = ?", Integer.class, sectionId, siteId);
        return n != null && n > 0;
    }

    public Map<UUID, String> names(Collection<UUID> ids) {
        return people.names(ids);
    }

    public String name(UUID id) {
        return id == null ? "" : people.name(id);
    }

    public void tell(Collection<UUID> to, UUID author, NotificationKind kind, String titleAr, String titleEn, String body, String bodyAr) {
        people.tell(to, author, kind, titleAr, titleEn, body, bodyAr);
    }

    public static String trim(String s, int max) {
        String t = s == null ? "" : s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
