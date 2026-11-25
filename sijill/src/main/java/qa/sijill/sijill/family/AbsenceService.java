package qa.sijill.sijill.family;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import qa.sijill.sijill.family.FamilyDtos.ChildRef;
import qa.sijill.sijill.family.FamilyDtos.ExcuseRequest;
import qa.sijill.sijill.family.FamilyDtos.ExcuseView;
import qa.sijill.sijill.family.FamilyDtos.ParentAbsences;
import qa.sijill.sijill.family.FamilyDtos.StaffAbsences;
import qa.sijill.sijill.family.FamilyDtos.UnexplainedDay;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditEvent;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Absence notes. A parent explains a day (or days) their child was, or will be, away. The attendance clerk or the
 * principal assistant accepts or rejects it; on acceptance the absent days in that range become excused leave, and
 * any day still to come is excused as soon as attendance records the child absent. When attendance records a child
 * absent with no note, the parents are asked (once per day) to send the reason.
 */
@Service
@RequiredArgsConstructor
public class AbsenceService {

    /** How far back a note can reach, and how far ahead it can be planned. */
    static final int MAX_PAST_DAYS = 30;
    static final int MAX_AHEAD_DAYS = 60;
    static final int MAX_LENGTH_DAYS = 30;
    static final Set<String> CATEGORIES = Set.of("ILLNESS", "MEDICAL", "FAMILY", "TRAVEL", "OTHER");

    private final JdbcTemplate jdbc;
    private final FamilyAccess access;
    private final AuditPublisher audit;

    // =============================================================== parent

    @Transactional
    public ExcuseView submit(CurrentUser user, ExcuseRequest in) {
        access.requireParentOf(user, in.studentId());
        UUID section = access.sectionOf(in.studentId()).orElseThrow(() -> new BadRequestException("Your child is not in a class yet."));
        LocalDate from = in.fromDate(), to = in.toDate() == null ? in.fromDate() : in.toDate();
        LocalDate today = access.today();
        if (to.isBefore(from)) {
            throw new BadRequestException("The last day must be on or after the first day.");
        }
        if (from.isBefore(today.minusDays(MAX_PAST_DAYS))) {
            throw new BadRequestException("A note can explain absences of the last " + MAX_PAST_DAYS + " days only.");
        }
        if (to.isAfter(today.plusDays(MAX_AHEAD_DAYS))) {
            throw new BadRequestException("A planned absence can be at most " + MAX_AHEAD_DAYS + " days ahead.");
        }
        if (from.plusDays(MAX_LENGTH_DAYS).isBefore(to)) {
            throw new BadRequestException("One note can cover at most " + MAX_LENGTH_DAYS + " days.");
        }
        String category = in.category().strip().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) {
            throw new BadRequestException("Choose the reason for the absence.");
        }
        String details = FamilyAccess.trim(in.details(), 1000);
        if ("OTHER".equals(category) && details.isEmpty()) {
            throw new BadRequestException("Please describe the reason.");
        }
        Integer overlap = jdbc.queryForObject("SELECT count(*) FROM absence_excuses WHERE student_id = ? AND status IN ('PENDING','ACCEPTED') "
                + "AND from_date <= ? AND to_date >= ?", Integer.class, in.studentId(), Date.valueOf(to), Date.valueOf(from));
        if (overlap != null && overlap > 0) {
            throw new ConflictException("A note for these days has already been sent.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO absence_excuses (id, org_id, site_id, student_id, parent_id, from_date, to_date, category, details) VALUES (?,?,?,?,?,?,?,?,?)",
                id, user.orgId(), user.siteId(), in.studentId(), user.personId(), Date.valueOf(from), Date.valueOf(to), category, details);
        audit.log(user.orgId(), user.siteId(), ActivityActions.EXCUSE_SUBMITTED).by(user).on("Person", in.studentId())
                .change(null, from + (to.equals(from) ? "" : " to " + to) + " " + category).save();
        String student = access.name(in.studentId());
        String days = from.equals(to) ? from.toString() : from + " to " + to;
        List<UUID> reviewers = access.staff(user.siteId(), Role.ATTENDANCE_OFFICER, Role.PRINCIPAL_ASSISTANT);
        access.tell(reviewers, user.personId(), NotificationKind.FAMILY, "عذر غياب جديد: " + student + " (" + access.sectionLabel(section) + ")",
                "New absence note: " + student + " (" + access.sectionLabel(section) + ")", days + " · " + label(category) + (details.isEmpty() ? "" : " · " + details),
                days + " · " + labelAr(category) + (details.isEmpty() ? "" : " · " + details));
        return views(rows("WHERE e.id = ?", id)).get(0);
    }

    @Transactional
    public ExcuseView cancel(CurrentUser user, UUID id) {
        access.requireParent(user);
        ExcuseView e = views(rows("WHERE e.id = ? AND e.parent_id = ?", id, user.personId())).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("The note"));
        if (!"PENDING".equals(e.status())) {
            throw new ConflictException("This note has already been reviewed.");
        }
        jdbc.update("UPDATE absence_excuses SET status = 'CANCELLED' WHERE id = ?", id);
        return views(rows("WHERE e.id = ?", id)).get(0);
    }

    /** The parent's children, the absent days with no note yet, and the notes sent (any linked parent's). */
    @Transactional(readOnly = true)
    public ParentAbsences mine(CurrentUser user) {
        access.requireParent(user);
        List<UUID> kids = access.people().childrenOf(user.personId());
        Map<UUID, String> names = access.names(kids);
        List<ChildRef> children = kids.stream().map(k -> new ChildRef(k, names.getOrDefault(k, ""), access.sectionOf(k).map(access::sectionLabel).orElse(""))).toList();
        List<UnexplainedDay> unexplained = new ArrayList<>();
        List<ExcuseView> notes = new ArrayList<>();
        Date since = Date.valueOf(access.today().minusDays(MAX_PAST_DAYS));
        for (UUID k : kids) {
            jdbc.query("SELECT ar.attendance_date FROM attendance_records ar WHERE ar.student_id = ? AND ar.status = 'ABSENT' AND ar.attendance_date >= ? "
                            + "AND NOT EXISTS (SELECT 1 FROM absence_excuses x WHERE x.student_id = ar.student_id AND x.status IN ('PENDING','ACCEPTED','REJECTED') "
                            + "AND ar.attendance_date BETWEEN x.from_date AND x.to_date) ORDER BY ar.attendance_date DESC",
                    rs -> { unexplained.add(new UnexplainedDay(k, names.getOrDefault(k, ""), rs.getDate(1).toLocalDate())); }, k, since);
            notes.addAll(views(rows("WHERE e.student_id = ? ORDER BY e.created_at DESC LIMIT 50", k)));
        }
        notes.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return new ParentAbsences(children, unexplained, notes);
    }

    // =============================================================== staff

    private static boolean canReview(CurrentUser user) {
        return user.isAny(Role.ATTENDANCE_OFFICER, Role.PRINCIPAL_ASSISTANT);
    }

    @Transactional(readOnly = true)
    public StaffAbsences board(CurrentUser user) {
        if (!user.isAny(Role.ATTENDANCE_OFFICER, Role.PRINCIPAL_ASSISTANT, Role.PRINCIPAL, Role.SCHOOL_IT)) {
            throw new ForbiddenException("Absence notes are for the attendance clerk, the principal assistant and the principal.");
        }
        Date today = Date.valueOf(access.today());
        return new StaffAbsences(
                views(rows("WHERE e.site_id = ? AND e.status = 'PENDING' ORDER BY e.from_date, e.created_at", user.siteId())),
                views(rows("WHERE e.site_id = ? AND e.status = 'ACCEPTED' AND ? BETWEEN e.from_date AND e.to_date ORDER BY e.from_date", user.siteId(), today)),
                views(rows("WHERE e.site_id = ? AND e.status IN ('ACCEPTED','REJECTED') ORDER BY e.reviewed_at DESC LIMIT 50", user.siteId())),
                canReview(user));
    }

    /** Accept (absent days in the range become excused leave) or reject (a short reason for the parent is required). */
    @Transactional
    public ExcuseView review(CurrentUser user, UUID id, String decision, String note) {
        if (!canReview(user)) {
            throw new ForbiddenException("Only the attendance clerk or the principal assistant can review absence notes.");
        }
        ExcuseView e = views(rows("WHERE e.id = ? AND e.site_id = ?", id, user.siteId())).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("The note"));
        if (!"PENDING".equals(e.status())) {
            throw new ConflictException("This note has already been reviewed.");
        }
        String d = decision == null ? "" : decision.strip().toUpperCase(Locale.ROOT);
        if (!d.equals("ACCEPTED") && !d.equals("REJECTED")) {
            throw new BadRequestException("Accept or reject the note.");
        }
        String why = FamilyAccess.trim(note, 500);
        if (d.equals("REJECTED") && why.isEmpty()) {
            throw new BadRequestException("Give the parent a short reason.");
        }
        jdbc.update("UPDATE absence_excuses SET status = ?, reviewed_by = ?, reviewed_at = ?, review_note = ? WHERE id = ?",
                d, user.personId(), Timestamp.from(access.now()), why.isEmpty() ? null : why, id);
        int excused = d.equals("ACCEPTED") ? excuse(user.orgId(), user.siteId(), user.personId(), e.studentId(), e.fromDate(), e.toDate(), e.category()) : 0;
        audit.log(user.orgId(), user.siteId(), ActivityActions.EXCUSE_REVIEWED).by(user).on("Person", e.studentId())
                .change("PENDING", d + (excused > 0 ? " (" + excused + " day(s) excused)" : "")).save();
        UUID parent = jdbc.queryForObject("SELECT parent_id FROM absence_excuses WHERE id = ?", UUID.class, id);
        String days = e.fromDate().equals(e.toDate()) ? e.fromDate().toString() : e.fromDate() + " to " + e.toDate();
        if (d.equals("ACCEPTED")) {
            access.tell(List.of(parent), user.personId(), NotificationKind.FAMILY, "تم قبول عذر غياب " + e.student(),
                    "Absence note accepted for " + e.student(), days + ": the absence is excused." + (why.isEmpty() ? "" : " " + why),
                    days + ": الغياب بعذر مقبول." + (why.isEmpty() ? "" : " " + why));
        } else {
            access.tell(List.of(parent), user.personId(), NotificationKind.FAMILY, "لم يُقبل عذر غياب " + e.student(),
                    "Absence note not accepted for " + e.student(), days + ": " + why, days + ": " + why);
        }
        return views(rows("WHERE e.id = ?", id)).get(0);
    }

    /** The child's absent days in the range become excused leave, each change written to the activity log. */
    private int excuse(UUID orgId, UUID siteId, UUID by, UUID studentId, LocalDate from, LocalDate to, String category) {
        List<Map<String, Object>> days = jdbc.queryForList("SELECT id, attendance_date FROM attendance_records WHERE student_id = ? AND status = 'ABSENT' "
                + "AND attendance_date BETWEEN ? AND ?", studentId, Date.valueOf(from), Date.valueOf(to));
        for (Map<String, Object> day : days) {
            jdbc.update("UPDATE attendance_records SET status = 'LEAVE', reason = ? WHERE id = ?", "Excused by parent note: " + label(category), day.get("id"));
            audit.log(orgId, siteId, ActivityActions.ATTENDANCE_CHANGED).by(by).on("Person", studentId)
                    .change(day.get("attendance_date") + " ABSENT", day.get("attendance_date") + " LEAVE (parent note)").save();
        }
        return days.size();
    }

    /**
     * Attendance was saved for a class: a child recorded absent on a day an accepted note covers is excused at once;
     * otherwise the parents are asked for the reason (once per child and day).
     */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAttendance(AuditEvent e) {
        if (!ActivityActions.ATTENDANCE_RECORDED.equals(e.action()) || e.entityId() == null || e.after() == null || e.after().length() < 10) {
            return;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(e.after().substring(0, 10));
        } catch (RuntimeException ex) {
            return;
        }
        List<UUID> absent = jdbc.queryForList("SELECT student_id FROM attendance_records WHERE section_id = ? AND attendance_date = ? AND status = 'ABSENT'",
                UUID.class, e.entityId(), Date.valueOf(date));
        for (UUID student : absent) {
            List<Map<String, Object>> accepted = jdbc.queryForList("SELECT category FROM absence_excuses WHERE student_id = ? AND status = 'ACCEPTED' "
                    + "AND ? BETWEEN from_date AND to_date LIMIT 1", student, Date.valueOf(date));
            if (!accepted.isEmpty()) {
                excuse(e.orgId(), e.siteId(), null, student, date, date, (String) accepted.get(0).get("category"));
                continue;
            }
            Integer pending = jdbc.queryForObject("SELECT count(*) FROM absence_excuses WHERE student_id = ? AND status = 'PENDING' AND ? BETWEEN from_date AND to_date",
                    Integer.class, student, Date.valueOf(date));
            if (pending != null && pending > 0) {
                continue;
            }
            if (jdbc.update("INSERT INTO absence_alerts (student_id, absent_on) VALUES (?, ?) ON CONFLICT DO NOTHING", student, Date.valueOf(date)) == 0) {
                continue;
            }
            String name = access.name(student);
            access.tell(access.people().parentsOf(List.of(student)), null, NotificationKind.FAMILY, name + " غائب اليوم " + date,
                    name + " was marked absent on " + date, "If you know why, send the reason from Absence notes in Hafez.",
                    "إذا كنت تعرف السبب، أرسله من صفحة أعذار الغياب في حافظ.");
        }
    }

    // =============================================================== reading

    private List<Map<String, Object>> rows(String where, Object... args) {
        return jdbc.queryForList("SELECT e.* FROM absence_excuses e " + where, args);
    }

    private List<ExcuseView> views(List<Map<String, Object>> rows) {
        List<UUID> ids = new ArrayList<>();
        rows.forEach(r -> { ids.add((UUID) r.get("student_id")); ids.add((UUID) r.get("parent_id")); if (r.get("reviewed_by") != null) ids.add((UUID) r.get("reviewed_by")); });
        Map<UUID, String> names = access.names(ids);
        Map<UUID, String> sections = new HashMap<>();
        return rows.stream().map(r -> {
            UUID student = (UUID) r.get("student_id");
            String section = sections.computeIfAbsent(student, s -> access.sectionOf(s).map(access::sectionLabel).orElse(""));
            return new ExcuseView((UUID) r.get("id"), student, names.getOrDefault(student, ""), section, names.getOrDefault((UUID) r.get("parent_id"), ""),
                    ((Date) r.get("from_date")).toLocalDate(), ((Date) r.get("to_date")).toLocalDate(), (String) r.get("category"), (String) r.get("details"),
                    (String) r.get("status"), r.get("reviewed_by") == null ? null : names.get((UUID) r.get("reviewed_by")),
                    RiskService.inst((Timestamp) r.get("reviewed_at")), (String) r.get("review_note"), RiskService.inst((Timestamp) r.get("created_at")));
        }).toList();
    }

    static String label(String category) {
        return switch (category) {
            case "ILLNESS" -> "Illness";
            case "MEDICAL" -> "Medical appointment";
            case "FAMILY" -> "Family matter";
            case "TRAVEL" -> "Travel";
            default -> "Other";
        };
    }

    static String labelAr(String category) {
        return switch (category) {
            case "ILLNESS" -> "مرض";
            case "MEDICAL" -> "موعد طبي";
            case "FAMILY" -> "ظرف عائلي";
            case "TRAVEL" -> "سفر";
            default -> "آخر";
        };
    }
}
