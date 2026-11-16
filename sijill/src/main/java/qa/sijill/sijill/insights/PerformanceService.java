package qa.sijill.sijill.insights;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.insights.InsightsDtos.IndicatorOutcome;
import qa.sijill.sijill.insights.InsightsDtos.IndicatorView;
import qa.sijill.sijill.insights.InsightsDtos.InvestigationDetail;
import qa.sijill.sijill.insights.InsightsDtos.InvestigationSummary;
import qa.sijill.sijill.insights.InsightsDtos.Member;
import qa.sijill.sijill.insights.InsightsDtos.Note;
import qa.sijill.sijill.insights.InsightsDtos.TeacherRecord;
import qa.sijill.sijill.insights.InsightsDtos.TeacherStanding;
import qa.sijill.sijill.insights.InsightsDtos.WarningView;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.events.PersonNotice;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Teacher performance. The head teacher records an indicator when a teacher is not delivering as expected.
 * {@value #PER_WARNING} indicators make one warning, and {@value #PER_INVESTIGATION} warnings open an investigation
 * automatically. The investigation involves the head teacher and the coordinators of the teacher's subject; the head
 * teacher can add anyone else who needs to be involved, and closes it with an outcome. Everything is logged.
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {

    public static final int PER_WARNING = 3;
    public static final int PER_INVESTIGATION = 3;

    /** What an indicator can be about. */
    public static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    static {
        CATEGORIES.put("PLANNING", "Weekly plans late or missing");
        CATEGORIES.put("MARKING", "Marks or grades late");
        CATEGORIES.put("DELIVERY", "Lesson delivery");
        CATEGORIES.put("PUNCTUALITY", "Attendance or punctuality");
        CATEGORIES.put("CONDUCT", "Professional conduct");
        CATEGORIES.put("OTHER", "Other");
    }

    private final JdbcTemplate jdbc;
    private final AuditPublisher audit;
    private final ApplicationEventPublisher events;

    // =================================================================== recording

    @Transactional
    public IndicatorOutcome record(CurrentUser user, UUID teacherId, String category, String note) {
        requirePrincipal(user);
        if (!CATEGORIES.containsKey(category)) {
            throw new BadRequestException("Choose what the indicator is about.");
        }
        String text = note == null ? "" : note.strip();
        if (text.isEmpty() || text.length() > 2000) {
            throw new BadRequestException("Describe what happened (up to 2000 characters).");
        }
        requireTeacherOfSchool(user, teacherId);
        jdbc.update("INSERT INTO teacher_indicators (org_id, site_id, teacher_id, recorded_by, category, note) VALUES (?,?,?,?,?,?)",
                user.orgId(), user.siteId(), teacherId, user.personId(), category, text);
        audit.log(user.orgId(), user.siteId(), ActivityActions.TEACHER_INDICATOR).by(user).on("Person", teacherId)
                .change(null, CATEGORIES.get(category)).save();

        boolean warned = false;
        UUID investigation = null;
        List<UUID> pending = pendingIndicators(teacherId);
        if (pending.size() >= PER_WARNING) {
            int number = count("SELECT count(*) FROM teacher_warnings WHERE teacher_id = ?", teacherId) + 1;
            UUID warning = UUID.randomUUID();
            jdbc.update("INSERT INTO teacher_warnings (id, org_id, site_id, teacher_id, number) VALUES (?,?,?,?,?)",
                    warning, user.orgId(), user.siteId(), teacherId, number);
            for (UUID i : pending.subList(0, PER_WARNING)) {
                jdbc.update("UPDATE teacher_indicators SET warning_id = ? WHERE id = ?", warning, i);
            }
            warned = true;
            audit.log(user.orgId(), user.siteId(), ActivityActions.TEACHER_WARNING).by(user).on("Person", teacherId)
                    .change(null, "warning " + number).save();
            tell(teacherId, "تم تسجيل إنذار", "A warning was recorded",
                    "Warning " + number + ": " + PER_WARNING + " indicators were recorded by your head teacher. Please speak with them.");

            List<UUID> loose = jdbc.queryForList("SELECT id FROM teacher_warnings WHERE teacher_id = ? AND investigation_id IS NULL ORDER BY created_at",
                    UUID.class, teacherId);
            if (loose.size() >= PER_INVESTIGATION && openInvestigation(teacherId) == null) {
                investigation = open(user, teacherId, loose.subList(0, PER_INVESTIGATION));
            }
        }
        return new IndicatorOutcome(pendingIndicators(teacherId).size(),
                count("SELECT count(*) FROM teacher_warnings WHERE teacher_id = ?", teacherId), warned, investigation);
    }

    /** Opens an investigation: the head teacher and the coordinators of the teacher's subject are involved. */
    private UUID open(CurrentUser user, UUID teacherId, List<UUID> warnings) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO investigations (id, org_id, site_id, teacher_id) VALUES (?,?,?,?)", id, user.orgId(), user.siteId(), teacherId);
        for (UUID w : warnings) {
            jdbc.update("UPDATE teacher_warnings SET investigation_id = ? WHERE id = ?", id, w);
        }
        Set<UUID> members = new LinkedHashSet<>(jdbc.queryForList(
                "SELECT id FROM people WHERE site_id = ? AND role IN ('PRINCIPAL', 'PRINCIPAL_ASSISTANT') AND status <> 'LEFT'", UUID.class, user.siteId()));
        members.add(user.personId());
        members.addAll(jdbc.queryForList("SELECT c.id FROM people c JOIN people t ON t.subject_id = c.subject_id "
                + "WHERE t.id = ? AND c.site_id = t.site_id AND c.role = 'COORDINATOR' AND c.status <> 'LEFT' AND c.id <> t.id", UUID.class, teacherId));
        members.remove(teacherId);
        String teacher = name(teacherId);
        for (UUID m : members) {
            jdbc.update("INSERT INTO investigation_members (investigation_id, person_id) VALUES (?, ?) ON CONFLICT DO NOTHING", id, m);
            if (!m.equals(user.personId())) {
                tell(m, "فُتح تحقيق: " + teacher, "Investigation opened: " + teacher,
                        teacher + " has reached " + PER_INVESTIGATION + " warnings. You are involved in the investigation.");
            }
        }
        jdbc.update("INSERT INTO investigation_notes (investigation_id, author_id, body) VALUES (?, ?, ?)", id, user.personId(),
                "Opened automatically: " + teacher + " reached " + PER_INVESTIGATION + " warnings.");
        tell(teacherId, "فُتح تحقيق", "An investigation was opened",
                "You have reached " + PER_INVESTIGATION + " warnings, so an investigation was opened. Your head teacher will contact you.");
        audit.log(user.orgId(), user.siteId(), ActivityActions.INVESTIGATION_OPENED).by(user).on("Person", teacherId)
                .change(null, "investigation opened").save();
        return id;
    }

    // =================================================================== reading

    /** Every teacher of the school with where they stand (head teacher only). */
    @Transactional(readOnly = true)
    public List<TeacherStanding> standings(CurrentUser user) {
        requirePrincipal(user);
        return jdbc.query("SELECT p.id, p.display_name, p.role, coalesce(s.name, '') FROM people p LEFT JOIN subjects s ON s.id = p.subject_id "
                        + "WHERE p.site_id = ? AND p.role IN ('TEACHER', 'COORDINATOR') AND p.status <> 'LEFT' ORDER BY p.display_name",
                (rs, i) -> standing(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)), user.siteId());
    }

    @Transactional(readOnly = true)
    public TeacherRecord teacher(CurrentUser user, UUID teacherId) {
        requirePrincipal(user);
        requireTeacherOfSchool(user, teacherId);
        Map<String, Object> p = jdbc.queryForMap("SELECT p.display_name, p.role, coalesce(s.name, '') AS subject FROM people p LEFT JOIN subjects s ON s.id = p.subject_id WHERE p.id = ?", teacherId);
        return new TeacherRecord(standing(teacherId, (String) p.get("display_name"), (String) p.get("role"), (String) p.get("subject")),
                indicators("WHERE i.teacher_id = ?", teacherId), warnings("WHERE teacher_id = ?", teacherId),
                summaries("WHERE v.teacher_id = ?", teacherId));
    }

    /** Investigations this person may see: all of the school's for the head teacher, otherwise those they are part of. */
    @Transactional(readOnly = true)
    public List<InvestigationSummary> investigations(CurrentUser user) {
        if (user.is(Role.PRINCIPAL)) {
            return summaries("WHERE v.site_id = ?", user.siteId());
        }
        return summaries("WHERE v.id IN (SELECT investigation_id FROM investigation_members WHERE person_id = ?)", user.personId());
    }

    @Transactional(readOnly = true)
    public InvestigationDetail investigation(CurrentUser user, UUID id) {
        InvestigationSummary s = visible(user, id);
        String outcome = jdbc.queryForObject("SELECT outcome FROM investigations WHERE id = ?", String.class, id);
        List<Member> members = jdbc.query("SELECT p.id, p.display_name, p.role FROM investigation_members m JOIN people p ON p.id = m.person_id "
                + "WHERE m.investigation_id = ? ORDER BY m.added_at", (rs, i) -> new Member(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), id);
        List<Note> notes = jdbc.query("SELECT p.display_name, n.body, n.created_at FROM investigation_notes n JOIN people p ON p.id = n.author_id "
                + "WHERE n.investigation_id = ? ORDER BY n.created_at", (rs, i) -> new Note(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()), id);
        boolean manage = user.is(Role.PRINCIPAL);
        List<Member> canAdd = manage ? jdbc.query("SELECT p.id, p.display_name, p.role FROM people p WHERE p.org_id = ? AND p.status <> 'LEFT' "
                        + "AND ((p.site_id = ? AND p.role IN ('COORDINATOR', 'SCHOOL_IT', 'PRINCIPAL', 'PRINCIPAL_ASSISTANT', 'TEACHER')) OR p.role = 'LEADERSHIP') "
                        + "AND p.id <> ? AND p.id NOT IN (SELECT person_id FROM investigation_members WHERE investigation_id = ?) ORDER BY p.display_name",
                (rs, i) -> new Member(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), user.orgId(), user.siteId(), s.teacherId(), id) : List.of();
        return new InvestigationDetail(s, outcome, members, notes, warnings("WHERE investigation_id = ?", id),
                indicators("WHERE i.warning_id IN (SELECT id FROM teacher_warnings WHERE investigation_id = ?)", id), manage && "OPEN".equals(s.status()), canAdd);
    }

    // =================================================================== working on an investigation

    @Transactional
    public InvestigationDetail addNote(CurrentUser user, UUID id, String body) {
        InvestigationSummary s = visible(user, id);
        if (!"OPEN".equals(s.status())) {
            throw new ConflictException("This investigation is closed.");
        }
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.length() > 4000) {
            throw new BadRequestException("Write a note (up to 4000 characters).");
        }
        jdbc.update("INSERT INTO investigation_notes (investigation_id, author_id, body) VALUES (?, ?, ?)", id, user.personId(), text);
        return investigation(user, id);
    }

    @Transactional
    public InvestigationDetail addMember(CurrentUser user, UUID id, UUID personId) {
        requirePrincipal(user);
        InvestigationSummary s = visible(user, id);
        if (!"OPEN".equals(s.status())) {
            throw new ConflictException("This investigation is closed.");
        }
        InvestigationDetail d = investigation(user, id);
        if (d.canAdd().stream().noneMatch(m -> m.personId().equals(personId))) {
            throw new BadRequestException("Choose someone from the list.");
        }
        jdbc.update("INSERT INTO investigation_members (investigation_id, person_id) VALUES (?, ?) ON CONFLICT DO NOTHING", id, personId);
        tell(personId, "أُضفت إلى تحقيق", "You were added to an investigation", "Investigation about " + s.teacher() + ".");
        return investigation(user, id);
    }

    @Transactional
    public InvestigationDetail close(CurrentUser user, UUID id, String outcome) {
        requirePrincipal(user);
        InvestigationSummary s = visible(user, id);
        if (!"OPEN".equals(s.status())) {
            throw new ConflictException("This investigation is already closed.");
        }
        String text = outcome == null ? "" : outcome.strip();
        if (text.isEmpty()) {
            throw new BadRequestException("Write the outcome before closing.");
        }
        jdbc.update("UPDATE investigations SET status = 'CLOSED', closed_at = now(), outcome = ? WHERE id = ?", text.length() > 4000 ? text.substring(0, 4000) : text, id);
        audit.log(user.orgId(), user.siteId(), ActivityActions.INVESTIGATION_CLOSED).by(user).on("Person", s.teacherId()).change("open", "closed").save();
        tell(s.teacherId(), "أُغلق التحقيق", "The investigation was closed", "Your head teacher closed the investigation.");
        return investigation(user, id);
    }

    // =================================================================== helpers

    private TeacherStanding standing(UUID id, String name, String role, String subject) {
        Timestamp last = jdbc.queryForObject("SELECT max(created_at) FROM teacher_indicators WHERE teacher_id = ?", Timestamp.class, id);
        return new TeacherStanding(id, name, role, subject, pendingIndicators(id).size(),
                count("SELECT count(*) FROM teacher_warnings WHERE teacher_id = ?", id),
                count("SELECT count(*) FROM teacher_indicators WHERE teacher_id = ?", id), openInvestigation(id), last == null ? null : last.toInstant());
    }

    private List<UUID> pendingIndicators(UUID teacherId) {
        return jdbc.queryForList("SELECT id FROM teacher_indicators WHERE teacher_id = ? AND warning_id IS NULL ORDER BY created_at", UUID.class, teacherId);
    }

    private UUID openInvestigation(UUID teacherId) {
        return jdbc.queryForList("SELECT id FROM investigations WHERE teacher_id = ? AND status = 'OPEN'", UUID.class, teacherId).stream().findFirst().orElse(null);
    }

    private List<IndicatorView> indicators(String where, Object... args) {
        return jdbc.query("SELECT i.id, i.category, i.note, p.display_name, i.created_at, w.number FROM teacher_indicators i "
                        + "LEFT JOIN people p ON p.id = i.recorded_by LEFT JOIN teacher_warnings w ON w.id = i.warning_id " + where + " ORDER BY i.created_at DESC",
                (rs, n) -> new IndicatorView(rs.getObject(1, UUID.class), rs.getString(2), CATEGORIES.getOrDefault(rs.getString(2), rs.getString(2)),
                        rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant(), (Integer) rs.getObject(6)), args);
    }

    private List<WarningView> warnings(String where, Object... args) {
        return jdbc.query("SELECT id, number, created_at, investigation_id FROM teacher_warnings " + where + " ORDER BY number",
                (rs, n) -> new WarningView(rs.getObject(1, UUID.class), rs.getInt(2), rs.getTimestamp(3).toInstant(), rs.getObject(4) != null), args);
    }

    private List<InvestigationSummary> summaries(String where, Object... args) {
        return jdbc.query("SELECT v.id, v.teacher_id, p.display_name, v.status, v.created_at, v.closed_at, "
                        + "(SELECT count(*) FROM investigation_members m WHERE m.investigation_id = v.id), (SELECT count(*) FROM investigation_notes n WHERE n.investigation_id = v.id) "
                        + "FROM investigations v JOIN people p ON p.id = v.teacher_id " + where + " ORDER BY v.status DESC, v.created_at DESC",
                (rs, n) -> new InvestigationSummary(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant(), rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant(), rs.getInt(7), rs.getInt(8)), args);
    }

    private InvestigationSummary visible(CurrentUser user, UUID id) {
        List<InvestigationSummary> s = summaries("WHERE v.id = ? AND v.org_id = ?", id, user.orgId());
        if (s.isEmpty()) {
            throw new NotFoundException("The investigation");
        }
        boolean principal = user.is(Role.PRINCIPAL) && count("SELECT count(*) FROM investigations WHERE id = ? AND site_id = ?", id, user.siteId()) > 0;
        boolean member = count("SELECT count(*) FROM investigation_members WHERE investigation_id = ? AND person_id = ?", id, user.personId()) > 0;
        if (!principal && !member) {
            throw new NotFoundException("The investigation");
        }
        return s.get(0);
    }

    private void requirePrincipal(CurrentUser user) {
        if (!user.is(Role.PRINCIPAL)) {
            throw new ForbiddenException("Teacher performance is managed by the head teacher.");
        }
    }

    private void requireTeacherOfSchool(CurrentUser user, UUID teacherId) {
        if (count("SELECT count(*) FROM people WHERE id = ? AND site_id = ? AND role IN ('TEACHER', 'COORDINATOR')", teacherId, user.siteId()) == 0) {
            throw new NotFoundException("The teacher");
        }
    }

    private String name(UUID id) {
        return jdbc.queryForList("SELECT display_name FROM people WHERE id = ?", String.class, id).stream().findFirst().orElse("");
    }

    private int count(String sql, Object... args) {
        Number n = jdbc.queryForObject(sql, Number.class, args);
        return n == null ? 0 : n.intValue();
    }

    private void tell(UUID person, String titleAr, String titleEn, String body) {
        events.publishEvent(new PersonNotice(person, NotificationKind.SECURITY, titleAr, titleEn, body));
    }
}
