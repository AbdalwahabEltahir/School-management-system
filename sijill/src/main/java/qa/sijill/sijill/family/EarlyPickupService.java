package qa.sijill.sijill.family;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.family.FamilyDtos.EarlyBoard;
import qa.sijill.sijill.family.FamilyDtos.EarlyPickupRequest;
import qa.sijill.sijill.family.FamilyDtos.EarlyPickupView;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.shared.util.SchoolCalendar;

/**
 * Early pick-up requests. A parent asks, ahead of time, to collect a child before the end of the school day: the day,
 * the time, who is collecting (themselves or someone on the child's approved list) and why. The principal assistant
 * or the attendance clerk approves or declines it; on approval the class teachers are told so the child is ready.
 * On the day the request shows on the pick-up board, and the parent completes the pick-up as usual with Pick up.
 */
@Service
@RequiredArgsConstructor
public class EarlyPickupService {

    static final int MAX_AHEAD_DAYS = 30;
    static final LocalTime EARLIEST = LocalTime.of(6, 30);
    static final LocalTime LATEST = LocalTime.of(15, 0);

    private final JdbcTemplate jdbc;
    private final FamilyAccess access;
    private final AuditPublisher audit;

    // =============================================================== parent

    @Transactional
    public EarlyPickupView request(CurrentUser user, EarlyPickupRequest in) {
        access.requireParentOf(user, in.studentId());
        UUID section = access.sectionOf(in.studentId()).orElseThrow(() -> new BadRequestException("Your child is not in a class yet. Ask the school office."));
        LocalDate today = access.today();
        if (in.date().isBefore(today) || in.date().isAfter(today.plusDays(MAX_AHEAD_DAYS))) {
            throw new BadRequestException("Choose a day from today up to " + MAX_AHEAD_DAYS + " days ahead.");
        }
        if (!SchoolCalendar.isSchoolDay(in.date())) {
            throw new BadRequestException("That day is not a school day.");
        }
        LocalTime time = in.time().withSecond(0).withNano(0);
        if (time.isBefore(EARLIEST) || time.isAfter(LATEST)) {
            throw new BadRequestException("Choose a time during the school day (between 06:30 and 15:00).");
        }
        if (in.date().equals(today) && !ZonedDateTime.of(in.date(), time, SchoolCalendar.ZONE).toInstant().isAfter(access.now())) {
            throw new BadRequestException("That time has already passed.");
        }
        String reason = FamilyAccess.trim(in.reason(), 300);
        if (reason.length() < 3) {
            throw new BadRequestException("Give a short reason for the early pick-up.");
        }
        String collector, relationship;
        if (in.collectorId() == null) {
            collector = user.name();
            relationship = "PARENT";
        } else {
            List<Map<String, Object>> c = jdbc.queryForList("SELECT name, relationship FROM pickup_collectors WHERE id = ? AND student_id = ? AND active",
                    in.collectorId(), in.studentId());
            if (c.isEmpty()) {
                throw new BadRequestException("Choose yourself or someone on your approved list.");
            }
            collector = (String) c.get(0).get("name");
            relationship = (String) c.get(0).get("relationship");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO early_pickup_requests (id, org_id, site_id, student_id, section_id, parent_id, pickup_on, pickup_time, collector_id, "
                            + "collector_name, collector_relationship, reason) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    id, user.orgId(), user.siteId(), in.studentId(), section, user.personId(), Date.valueOf(in.date()), Time.valueOf(time),
                    in.collectorId(), collector, relationship, reason);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("There is already an early pick-up request for this child on that day.");
        }
        audit.log(user.orgId(), user.siteId(), ActivityActions.EARLY_PICKUP_REQUESTED).by(user).on("Person", in.studentId())
                .change(null, in.date() + " " + time + " by " + collector).save();
        String student = access.name(in.studentId());
        access.tell(access.staff(user.siteId(), Role.PRINCIPAL_ASSISTANT, Role.ATTENDANCE_OFFICER), user.personId(), NotificationKind.COLLECTION,
                "طلب استلام مبكر: " + student + " (" + access.sectionLabel(section) + ")", "Early pick-up request: " + student + " (" + access.sectionLabel(section) + ")",
                in.date() + " at " + time + " by " + collector + ". " + reason, in.date() + " الساعة " + time + " بواسطة " + collector + ". " + reason);
        return views(rows("WHERE id = ?", id)).get(0);
    }

    @Transactional
    public EarlyPickupView cancel(CurrentUser user, UUID id) {
        access.requireParent(user);
        EarlyPickupView r = views(rows("WHERE id = ? AND parent_id = ?", id, user.personId())).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("The request"));
        if (!r.status().equals("PENDING") && !r.status().equals("APPROVED")) {
            throw new ConflictException("This request is already finished.");
        }
        jdbc.update("UPDATE early_pickup_requests SET status = 'CANCELLED' WHERE id = ?", id);
        if (r.status().equals("APPROVED")) {
            UUID section = jdbc.queryForObject("SELECT section_id FROM early_pickup_requests WHERE id = ?", UUID.class, id);
            access.tell(access.classTeachers(section), user.personId(), NotificationKind.COLLECTION, "أُلغي الاستلام المبكر: " + r.student(),
                    "Early pick-up cancelled: " + r.student(), r.date() + " " + r.time() + ". The child stays until the end of the day.",
                    r.date() + " " + r.time() + ". يبقى الطالب حتى نهاية اليوم.");
        }
        return views(rows("WHERE id = ?", id)).get(0);
    }

    @Transactional(readOnly = true)
    public List<EarlyPickupView> mine(CurrentUser user) {
        access.requireParent(user);
        List<UUID> kids = access.people().childrenOf(user.personId());
        List<EarlyPickupView> out = new ArrayList<>();
        Date since = Date.valueOf(access.today().minusDays(14));
        for (UUID k : kids) {
            out.addAll(views(rows("WHERE student_id = ? AND pickup_on >= ?", k, since)));
        }
        out.sort((a, b) -> a.date().equals(b.date()) ? a.time().compareTo(b.time()) : b.date().compareTo(a.date()));
        return out;
    }

    // =============================================================== staff

    private static boolean canDecide(CurrentUser user) {
        return user.isAny(Role.PRINCIPAL_ASSISTANT, Role.ATTENDANCE_OFFICER);
    }

    /** Waiting requests, today's approved early pick-ups and the coming ones. Teachers see their own classes. */
    @Transactional(readOnly = true)
    public EarlyBoard board(CurrentUser user) {
        if (!user.isAny(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL, Role.PRINCIPAL_ASSISTANT, Role.SCHOOL_IT, Role.ATTENDANCE_OFFICER)) {
            throw new ForbiddenException("Early pick-ups are for the school staff.");
        }
        Date today = Date.valueOf(access.today());
        Set<UUID> scope = user.is(Role.TEACHER) ? access.sectionsTaughtBy(user.personId()) : null;
        List<EarlyPickupView> pending = new ArrayList<>(), todays = new ArrayList<>(), upcoming = new ArrayList<>();
        for (Map<String, Object> r : rows("WHERE site_id = ? AND pickup_on >= ? AND status IN ('PENDING','APPROVED')", user.siteId(), today)) {
            if (scope != null && !scope.contains((UUID) r.get("section_id"))) {
                continue;
            }
            EarlyPickupView v = views(List.of(r)).get(0);
            if (v.status().equals("PENDING")) {
                pending.add(v);
            } else if (v.date().equals(access.today())) {
                todays.add(v);
            } else {
                upcoming.add(v);
            }
        }
        java.util.Comparator<EarlyPickupView> byTime = (a, b) -> a.date().equals(b.date()) ? a.time().compareTo(b.time()) : a.date().compareTo(b.date());
        pending.sort(byTime);
        todays.sort(byTime);
        upcoming.sort(byTime);
        return new EarlyBoard(pending, todays, upcoming, canDecide(user));
    }

    @Transactional
    public EarlyPickupView decide(CurrentUser user, UUID id, boolean approve, String reason) {
        if (!canDecide(user)) {
            throw new ForbiddenException("Only the principal assistant or the attendance clerk can approve early pick-ups.");
        }
        List<Map<String, Object>> found = rows("WHERE id = ? AND site_id = ?", id, user.siteId());
        if (found.isEmpty()) {
            throw new NotFoundException("The request");
        }
        EarlyPickupView r = views(found).get(0);
        if (!r.status().equals("PENDING")) {
            throw new ConflictException("This request has already been decided.");
        }
        String why = FamilyAccess.trim(reason, 300);
        if (!approve && why.isEmpty()) {
            throw new BadRequestException("Give the parent a short reason.");
        }
        jdbc.update("UPDATE early_pickup_requests SET status = ?, decided_by = ?, decided_at = ?, decline_reason = ? WHERE id = ?",
                approve ? "APPROVED" : "DECLINED", user.personId(), Timestamp.from(access.now()), approve ? null : why, id);
        audit.log(user.orgId(), user.siteId(), ActivityActions.EARLY_PICKUP_DECIDED).by(user).on("Person", r.studentId())
                .change("PENDING", (approve ? "APPROVED" : "DECLINED: " + why)).save();
        UUID parent = (UUID) found.get(0).get("parent_id");
        UUID section = (UUID) found.get(0).get("section_id");
        if (approve) {
            access.tell(List.of(parent), user.personId(), NotificationKind.COLLECTION, "تمت الموافقة على الاستلام المبكر: " + r.student(),
                    "Early pick-up approved: " + r.student(), r.date() + " at " + r.time() + ". Press Pick up in Hafez when you collect " + r.student() + ".",
                    r.date() + " الساعة " + r.time() + ". اضغط زر الاستلام في حافظ عند استلام " + r.student() + ".");
            access.tell(access.classTeachers(section), user.personId(), NotificationKind.COLLECTION,
                    "استلام مبكر: " + r.student() + " (" + r.section() + ")", "Early pick-up: " + r.student() + " (" + r.section() + ")",
                    r.date() + " at " + r.time() + " by " + r.collector() + ". Please have the child ready.",
                    r.date() + " الساعة " + r.time() + " بواسطة " + r.collector() + ". يرجى تجهيز الطالب.");
        } else {
            access.tell(List.of(parent), user.personId(), NotificationKind.COLLECTION, "لم تتم الموافقة على الاستلام المبكر: " + r.student(),
                    "Early pick-up not approved: " + r.student(), r.date() + " at " + r.time() + ": " + why, r.date() + " الساعة " + r.time() + ": " + why);
        }
        return views(rows("WHERE id = ?", id)).get(0);
    }

    // =============================================================== reading

    private List<Map<String, Object>> rows(String where, Object... args) {
        return jdbc.queryForList("SELECT * FROM early_pickup_requests " + where, args);
    }

    private List<EarlyPickupView> views(List<Map<String, Object>> rows) {
        List<UUID> ids = new ArrayList<>();
        rows.forEach(r -> { ids.add((UUID) r.get("student_id")); ids.add((UUID) r.get("parent_id")); if (r.get("decided_by") != null) ids.add((UUID) r.get("decided_by")); });
        Map<UUID, String> names = access.names(ids);
        return rows.stream().map(r -> {
            LocalDate day = ((Date) r.get("pickup_on")).toLocalDate();
            UUID student = (UUID) r.get("student_id");
            Integer picked = jdbc.queryForObject("SELECT count(*) FROM pickup_release_log WHERE student_id = ? AND released_at >= ? AND released_at < ?", Integer.class,
                    student, Timestamp.from(day.atStartOfDay(SchoolCalendar.ZONE).toInstant()), Timestamp.from(day.plusDays(1).atStartOfDay(SchoolCalendar.ZONE).toInstant()));
            return new EarlyPickupView((UUID) r.get("id"), student, names.getOrDefault(student, ""), access.sectionLabel((UUID) r.get("section_id")),
                    names.getOrDefault((UUID) r.get("parent_id"), ""), day, ((Time) r.get("pickup_time")).toLocalTime(), (String) r.get("collector_name"),
                    ((String) r.get("collector_relationship")).toUpperCase(Locale.ROOT), (String) r.get("reason"), (String) r.get("status"),
                    r.get("decided_by") == null ? null : names.get((UUID) r.get("decided_by")), RiskService.inst((Timestamp) r.get("decided_at")),
                    (String) r.get("decline_reason"), RiskService.inst((Timestamp) r.get("created_at")), picked != null && picked > 0);
        }).toList();
    }
}
