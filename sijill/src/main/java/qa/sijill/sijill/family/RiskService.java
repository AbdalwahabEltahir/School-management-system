package qa.sijill.sijill.family;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import qa.sijill.sijill.family.FamilyDtos.RiskBoard;
import qa.sijill.sijill.family.FamilyDtos.RiskView;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditEvent;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Low-grade warnings. A student is flagged in a subject when their average over the submitted assessments of the
 * current term (the latest term with submitted marks in their class) falls below {@value #PASS_LINE}%, and the flag
 * is {@code HIGH} below {@value #HIGH_LINE}%. Flags are raised and resolved automatically: whenever a teacher submits
 * or unlocks marks, and whenever the list is opened. A new flag alerts the subject's teacher, its coordinator and the
 * principal assistant. Staff can write what they are doing about it and tell the parents.
 */
@Service
@RequiredArgsConstructor
public class RiskService {

    public static final int PASS_LINE = 60;
    public static final int HIGH_LINE = 50;

    private final JdbcTemplate jdbc;
    private final FamilyAccess access;
    private final AuditPublisher audit;

    private record Stat(UUID studentId, UUID sectionId, UUID subjectId, BigDecimal average, BigDecimal lowest, int count) {
        String level() {
            return average.compareTo(BigDecimal.valueOf(HIGH_LINE)) < 0 ? "HIGH" : "WATCH";
        }
    }

    private record Key(UUID studentId, UUID subjectId) {}

    private record OpenFlag(UUID id, String level) {}

    // =============================================================== automatic raising and resolving

    /** Marks were submitted or reopened somewhere in the school: look again, once that change is committed. */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMarks(AuditEvent e) {
        if (e.siteId() != null && (ActivityActions.MARKS_SUBMITTED.equals(e.action()) || ActivityActions.MARKS_UNLOCKED.equals(e.action()))) {
            recompute(e.orgId(), e.siteId());
        }
    }

    /** Brings the school's flags in line with its submitted marks. Returns how many new flags were raised. */
    @Transactional
    public int recompute(UUID orgId, UUID siteId) {
        Map<Key, Stat> below = new LinkedHashMap<>();
        for (Stat s : stats(siteId)) {
            if (s.average().compareTo(BigDecimal.valueOf(PASS_LINE)) < 0) {
                below.put(new Key(s.studentId(), s.subjectId()), s);
            }
        }
        Map<Key, OpenFlag> open = new HashMap<>();
        jdbc.query("SELECT id, student_id, subject_id, level FROM risk_flags WHERE site_id = ? AND status = 'OPEN'",
                rs -> { open.put(new Key(rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)), new OpenFlag(rs.getObject(1, UUID.class), rs.getString(4))); },
                siteId);
        Timestamp now = Timestamp.from(access.now());
        Map<String, List<Stat>> alerts = new LinkedHashMap<>();   // "section|subject" -> students newly flagged (or now HIGH)
        for (Map.Entry<Key, Stat> e : below.entrySet()) {
            Stat s = e.getValue();
            OpenFlag f = open.remove(e.getKey());
            if (f == null) {
                int n = jdbc.update("INSERT INTO risk_flags (id, org_id, site_id, student_id, section_id, subject_id, average_pct, lowest_pct, assessments, level, raised_at, updated_at) "
                                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (student_id, subject_id) WHERE status = 'OPEN' DO NOTHING",
                        UUID.randomUUID(), orgId, siteId, s.studentId(), s.sectionId(), s.subjectId(), s.average(), s.lowest(), s.count(), s.level(), now, now);
                if (n > 0) {
                    alerts.computeIfAbsent(s.sectionId() + "|" + s.subjectId(), k -> new ArrayList<>()).add(s);
                }
            } else {
                jdbc.update("UPDATE risk_flags SET section_id = ?, average_pct = ?, lowest_pct = ?, assessments = ?, level = ?, updated_at = ? WHERE id = ?",
                        s.sectionId(), s.average(), s.lowest(), s.count(), s.level(), now, f.id());
                if ("WATCH".equals(f.level()) && "HIGH".equals(s.level())) {
                    alerts.computeIfAbsent(s.sectionId() + "|" + s.subjectId(), k -> new ArrayList<>()).add(s);
                }
            }
        }
        // whatever is still open is no longer below the line (the average recovered, or the marks were reopened)
        for (OpenFlag f : open.values()) {
            jdbc.update("UPDATE risk_flags SET status = 'RESOLVED', resolved_at = ?, updated_at = ? WHERE id = ?", now, now, f.id());
        }
        int raised = 0;
        for (List<Stat> group : alerts.values()) {
            raised += group.size();
            alert(siteId, group);
        }
        return raised;
    }

    /** One bell entry per person for a class and subject, naming the students. */
    private void alert(UUID siteId, List<Stat> group) {
        Stat first = group.get(0);
        Set<UUID> to = new LinkedHashSet<>(jdbc.queryForList("SELECT teacher_id FROM teaching_assignments WHERE section_id = ? AND subject_id = ?",
                UUID.class, first.sectionId(), first.subjectId()));
        jdbc.queryForList("SELECT coordinator_id FROM subjects WHERE id = ? AND coordinator_id IS NOT NULL", UUID.class, first.subjectId()).forEach(to::add);
        to.addAll(access.staff(siteId, Role.PRINCIPAL_ASSISTANT));
        String subject = access.people().subjectNames(siteId).getOrDefault(first.subjectId(), "");
        String section = access.sectionLabel(first.sectionId());
        Map<UUID, String> names = access.names(group.stream().map(Stat::studentId).toList());
        StringBuilder en = new StringBuilder(), ar = new StringBuilder();
        for (Stat s : group) {
            String who = names.getOrDefault(s.studentId(), "");
            en.append(who).append(": ").append(s.average().setScale(0, RoundingMode.HALF_UP)).append("% average over ").append(s.count())
                    .append(s.count() == 1 ? " assessment" : " assessments").append(". ");
            ar.append(who).append(": المعدل ").append(s.average().setScale(0, RoundingMode.HALF_UP)).append("% في ").append(s.count()).append(" تقييم. ");
        }
        String titleEn = group.size() == 1 ? names.getOrDefault(first.studentId(), "") + " is at risk in " + subject + " (" + section + ")"
                : group.size() + " students are at risk in " + subject + " (" + section + ")";
        String titleAr = group.size() == 1 ? names.getOrDefault(first.studentId(), "") + " متعثر في " + subject + " (" + section + ")"
                : group.size() + " طلاب متعثرون في " + subject + " (" + section + ")";
        access.tell(to, null, NotificationKind.AT_RISK, titleAr, titleEn, en.toString().trim() + " Open Students at risk to follow up.",
                ar.toString().trim() + " افتح صفحة الطلاب المتعثرين للمتابعة.");
    }

    /** Average and lowest percentage per student and subject over the current term's submitted marks. */
    private List<Stat> stats(UUID siteId) {
        return jdbc.query("""
                WITH cur AS (SELECT section_id, max(term) AS term FROM assessments WHERE site_id = ? AND submitted_at IS NOT NULL GROUP BY section_id)
                SELECT m.student_id, a.section_id, a.subject_id,
                       round(avg(m.mark_value * 100 / a.max_mark), 2) AS average, round(min(m.mark_value * 100 / a.max_mark), 2) AS lowest, count(*) AS n
                FROM marks m
                JOIN assessments a ON a.id = m.assessment_id
                JOIN cur ON cur.section_id = a.section_id AND cur.term = a.term
                JOIN enrolments e ON e.student_id = m.student_id AND e.section_id = a.section_id
                WHERE a.site_id = ? AND a.submitted_at IS NOT NULL AND NOT m.absent AND m.mark_value IS NOT NULL
                GROUP BY m.student_id, a.section_id, a.subject_id
                """, (rs, i) -> new Stat(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getInt(6)), siteId, siteId);
    }

    // =============================================================== the list

    @Transactional
    public RiskBoard board(CurrentUser user) {
        requireReader(user);
        recompute(user.orgId(), user.siteId());
        Instant since = access.now().minus(30, ChronoUnit.DAYS);
        List<RiskView> open = views(user, rows("WHERE site_id = ? AND status = 'OPEN'", user.siteId()));
        List<RiskView> improved = views(user, rows("WHERE site_id = ? AND status = 'RESOLVED' AND resolved_at > ?", user.siteId(), Timestamp.from(since)));
        return new RiskBoard(PASS_LINE, HIGH_LINE,
                open.stream().sorted((a, b) -> a.averagePct().compareTo(b.averagePct())).toList(),
                improved.stream().sorted((a, b) -> b.resolvedAt().compareTo(a.resolvedAt())).toList());
    }

    /** What the staff member is doing about it (a support plan, extra lessons, a call home). */
    @Transactional
    public RiskView note(CurrentUser user, UUID id, String note) {
        Row r = actable(user, id);
        String text = FamilyAccess.trim(note, 1000);
        if (text.isEmpty()) {
            throw new BadRequestException("Write what is being done to help.");
        }
        jdbc.update("UPDATE risk_flags SET note = ?, noted_by = ?, noted_at = ? WHERE id = ?", text, user.personId(), Timestamp.from(access.now()), id);
        audit.log(user.orgId(), user.siteId(), ActivityActions.RISK_NOTED).by(user).on("Person", r.studentId()).subject(r.subjectId()).change(r.note(), text).save();
        return views(user, rows("WHERE id = ?", id)).get(0);
    }

    /** Sends the parents a bell message about the subject, with an optional personal line from the teacher. */
    @Transactional
    public RiskView tellParents(CurrentUser user, UUID id, String message) {
        Row r = actable(user, id);
        if (!"OPEN".equals(r.status())) {
            throw new BadRequestException("This student is no longer at risk in this subject.");
        }
        List<UUID> parents = access.people().parentsOf(List.of(r.studentId()));
        if (parents.isEmpty()) {
            throw new BadRequestException("No parent account is linked to this student yet.");
        }
        String extra = FamilyAccess.trim(message, 500);
        String subject = access.people().subjectNames(user.siteId()).getOrDefault(r.subjectId(), "");
        String student = access.name(r.studentId());
        String avg = r.average().setScale(0, RoundingMode.HALF_UP) + "%";
        access.tell(parents, user.personId(), NotificationKind.AT_RISK,
                student + " يحتاج إلى دعم في " + subject, student + " needs support in " + subject,
                student + "'s average in " + subject + " is " + avg + " this term. " + (extra.isEmpty() ? "" : user.name() + ": \"" + extra + "\" ")
                        + "You can chat with the teacher or book a meeting in Hafez.",
                "معدل " + student + " في " + subject + " هو " + avg + " هذا الفصل. " + (extra.isEmpty() ? "" : user.name() + ": \"" + extra + "\" ")
                        + "يمكنك محادثة المعلم أو حجز موعد معه في حافظ.");
        jdbc.update("UPDATE risk_flags SET parents_told_at = ?, parents_told_by = ? WHERE id = ?", Timestamp.from(access.now()), user.personId(), id);
        audit.log(user.orgId(), user.siteId(), ActivityActions.RISK_PARENTS_TOLD).by(user).on("Person", r.studentId()).subject(r.subjectId())
                .change(null, subject + " " + avg).save();
        return views(user, rows("WHERE id = ?", id)).get(0);
    }

    // =============================================================== who sees what

    private static final Role[] READERS = {Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL, Role.PRINCIPAL_ASSISTANT};

    private void requireReader(CurrentUser user) {
        if (!user.isAny(READERS)) {
            throw new ForbiddenException("Students at risk are for teachers, coordinators, the principal and the principal assistant.");
        }
    }

    /** null = the whole school; otherwise the (section|subject) pairs this person follows, or the subjects they coordinate. */
    private Scope scope(CurrentUser user) {
        if (user.isAny(Role.PRINCIPAL, Role.PRINCIPAL_ASSISTANT)) {
            return null;
        }
        Set<String> pairs = new LinkedHashSet<>();
        access.people().pairsOf(user.personId()).forEach(p -> pairs.add(p.sectionId() + "|" + p.subjectId()));
        Set<UUID> subjects = new LinkedHashSet<>();
        if (user.is(Role.COORDINATOR)) {
            subjects.addAll(jdbc.queryForList("SELECT id FROM subjects WHERE coordinator_id = ?", UUID.class, user.personId()));
            if (user.subjectId() != null) {
                subjects.add(user.subjectId());
            }
        }
        return new Scope(pairs, subjects);
    }

    private record Scope(Set<String> pairs, Set<UUID> subjects) {
        boolean covers(Row r) {
            return subjects.contains(r.subjectId()) || pairs.contains(r.sectionId() + "|" + r.subjectId());
        }
    }

    private Row actable(CurrentUser user, UUID id) {
        if (!user.isAny(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL_ASSISTANT)) {
            throw new ForbiddenException("Only the teacher, the coordinator or the principal assistant can follow up a student at risk.");
        }
        Row r = rows("WHERE id = ? AND site_id = ?", id, user.siteId()).stream().findFirst().orElseThrow(() -> new NotFoundException("The student at risk"));
        Scope s = scope(user);
        if (s != null && !s.covers(r)) {
            throw new NotFoundException("The student at risk");
        }
        return r;
    }

    private record Row(UUID id, UUID studentId, UUID sectionId, UUID subjectId, BigDecimal average, BigDecimal lowest, int count,
                       String level, String status, Instant raisedAt, Instant resolvedAt, String note, UUID notedBy, Instant notedAt,
                       Instant parentsToldAt) {}

    private List<Row> rows(String where, Object... args) {
        return jdbc.query("SELECT id, student_id, section_id, subject_id, average_pct, lowest_pct, assessments, level, status, raised_at, resolved_at, "
                        + "note, noted_by, noted_at, parents_told_at FROM risk_flags " + where,
                (rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getInt(7), rs.getString(8), rs.getString(9), inst(rs.getTimestamp(10)),
                        inst(rs.getTimestamp(11)), rs.getString(12), rs.getObject(13, UUID.class), inst(rs.getTimestamp(14)), inst(rs.getTimestamp(15))), args);
    }

    static Instant inst(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private List<RiskView> views(CurrentUser user, List<Row> rows) {
        Scope scope = scope(user);
        List<Row> mine = rows.stream().filter(r -> scope == null || scope.covers(r)).toList();
        Map<UUID, String> subjects = access.people().subjectNames(user.siteId());
        Map<UUID, String> sections = access.people().sectionLabels(user.siteId());
        List<UUID> ids = new ArrayList<>();
        mine.forEach(r -> { ids.add(r.studentId()); if (r.notedBy() != null) ids.add(r.notedBy()); });
        Map<UUID, String> names = access.names(ids);
        Map<UUID, Integer> absences = new HashMap<>();
        java.sql.Date since = java.sql.Date.valueOf(access.today().minusDays(30));
        jdbc.query("SELECT student_id, count(*) FROM attendance_records WHERE site_id = ? AND status = 'ABSENT' AND attendance_date >= ? GROUP BY student_id",
                rs -> { absences.put(rs.getObject(1, UUID.class), rs.getInt(2)); }, user.siteId(), since);
        boolean canAct = user.isAny(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL_ASSISTANT);
        return mine.stream().map(r -> new RiskView(r.id(), r.studentId(), names.getOrDefault(r.studentId(), ""), r.sectionId(),
                sections.getOrDefault(r.sectionId(), ""), r.subjectId(), subjects.getOrDefault(r.subjectId(), ""), r.average(), r.lowest(),
                r.count(), r.level(), r.status(), r.raisedAt(), r.resolvedAt(), r.note(), r.notedBy() == null ? null : names.get(r.notedBy()),
                r.notedAt(), r.parentsToldAt(), absences.getOrDefault(r.studentId(), 0), canAct && "OPEN".equals(r.status()))).toList();
    }
}
