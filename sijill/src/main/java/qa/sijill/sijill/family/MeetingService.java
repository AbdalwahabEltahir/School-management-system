package qa.sijill.sijill.family;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

import qa.sijill.sijill.family.FamilyDtos.BookingView;
import qa.sijill.sijill.family.FamilyDtos.ChildTeachers;
import qa.sijill.sijill.family.FamilyDtos.ParentMeetings;
import qa.sijill.sijill.family.FamilyDtos.SlotView;
import qa.sijill.sijill.family.FamilyDtos.SlotsRequest;
import qa.sijill.sijill.family.FamilyDtos.TeacherMeetings;
import qa.sijill.sijill.family.FamilyDtos.TeacherOption;
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
 * Parent-teacher meetings. A teacher opens free slots (a time range cut into meetings of a few minutes, in person,
 * online or by phone). A parent sees the free slots of their child's teachers and books one with a topic; a slot can
 * be booked by only one family (the database refuses a second booking). Either side can cancel; the other is told.
 */
@Service
@RequiredArgsConstructor
public class MeetingService {

    static final int MAX_AHEAD_DAYS = 60;
    static final int MAX_SLOTS_PER_DAY = 24;
    static final Set<String> MODES = Set.of("IN_PERSON", "ONLINE", "PHONE");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private final JdbcTemplate jdbc;
    private final FamilyAccess access;
    private final AuditPublisher audit;

    private static boolean isTeacher(CurrentUser user) {
        return user.isAny(Role.TEACHER, Role.COORDINATOR);
    }

    // =============================================================== teacher

    /** Cuts {@code from}..{@code to} into slots of {@code minutes}; times the teacher already has a slot at are skipped. */
    @Transactional
    public TeacherMeetings open(CurrentUser user, SlotsRequest r) {
        if (!isTeacher(user)) {
            throw new ForbiddenException("Meeting slots are opened by teachers.");
        }
        if (r.minutes() < 5 || r.minutes() > 120) {
            throw new BadRequestException("A meeting lasts between 5 and 120 minutes.");
        }
        String mode = r.mode().strip().toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            throw new BadRequestException("Choose in person, online or by phone.");
        }
        String place = FamilyAccess.trim(r.place(), 300);
        if ("ONLINE".equals(mode) && !place.isEmpty() && !place.startsWith("https://")) {
            throw new BadRequestException("An online meeting link must start with https://");
        }
        if (r.date().isBefore(access.today()) || r.date().isAfter(access.today().plusDays(MAX_AHEAD_DAYS))) {
            throw new BadRequestException("Choose a day from today up to " + MAX_AHEAD_DAYS + " days ahead.");
        }
        if (!r.to().isAfter(r.from())) {
            throw new BadRequestException("The end time must be after the start time.");
        }
        List<Instant> starts = new ArrayList<>();
        int from = r.from().toSecondOfDay() / 60, to = r.to().toSecondOfDay() / 60;
        for (int m = from; m + r.minutes() <= to; m += r.minutes()) {
            Instant at = ZonedDateTime.of(r.date(), LocalTime.of(m / 60, m % 60), SchoolCalendar.ZONE).toInstant();
            if (at.isAfter(access.now())) {
                starts.add(at);
            }
        }
        if (starts.isEmpty()) {
            throw new BadRequestException("No meeting fits in that time (or it has already passed).");
        }
        if (starts.size() > MAX_SLOTS_PER_DAY) {
            throw new BadRequestException("Open at most " + MAX_SLOTS_PER_DAY + " slots at a time.");
        }
        for (Instant at : starts) {
            jdbc.update("INSERT INTO meeting_slots (id, org_id, site_id, teacher_id, starts_at, minutes, mode, place) VALUES (?,?,?,?,?,?,?,?) "
                            + "ON CONFLICT (teacher_id, starts_at) WHERE removed_at IS NULL DO NOTHING",
                    UUID.randomUUID(), user.orgId(), user.siteId(), user.personId(), Timestamp.from(at), r.minutes(), mode, place);
        }
        return teacher(user);
    }

    @Transactional(readOnly = true)
    public TeacherMeetings teacher(CurrentUser user) {
        if (!isTeacher(user)) {
            throw new ForbiddenException("This is the teacher's meeting list.");
        }
        List<SlotView> slots = slots("WHERE s.teacher_id = ? AND s.removed_at IS NULL AND s.starts_at > ? ORDER BY s.starts_at",
                user.personId(), Timestamp.from(access.now().minus(1, ChronoUnit.HOURS)));
        int booked = (int) slots.stream().filter(SlotView::booked).count();
        return new TeacherMeetings(slots, booked, slots.size() - booked);
    }

    /** Removes a slot. If a family had booked it, the booking is cancelled and the parent told why. */
    @Transactional
    public TeacherMeetings removeSlot(CurrentUser user, UUID slotId, String reason) {
        if (!isTeacher(user)) {
            throw new ForbiddenException("Only the teacher can remove their slot.");
        }
        SlotView s = slots("WHERE s.id = ? AND s.teacher_id = ? AND s.removed_at IS NULL", slotId, user.personId()).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("The slot"));
        String why = FamilyAccess.trim(reason, 300);
        if (s.booked()) {
            if (why.isEmpty()) {
                throw new BadRequestException("A family booked this slot. Tell them why it is cancelled.");
            }
            cancelBooking(user, s.bookingId(), why);
        }
        jdbc.update("UPDATE meeting_slots SET removed_at = ? WHERE id = ?", Timestamp.from(access.now()), slotId);
        return teacher(user);
    }

    // =============================================================== parent

    @Transactional(readOnly = true)
    public ParentMeetings parent(CurrentUser user) {
        access.requireParent(user);
        List<ChildTeachers> children = new ArrayList<>();
        Timestamp now = Timestamp.from(access.now());
        Timestamp until = Timestamp.from(access.now().plus(MAX_AHEAD_DAYS, ChronoUnit.DAYS));
        for (UUID k : access.people().childrenOf(user.personId())) {
            UUID section = access.sectionOf(k).orElse(null);
            if (section == null) {
                continue;
            }
            Map<UUID, List<String>> subjects = new LinkedHashMap<>();
            for (Map<String, Object> t : access.people().teachersOf(section)) {
                subjects.computeIfAbsent((UUID) t.get("teacher_id"), x -> new ArrayList<>()).add((String) t.get("subject"));
            }
            Map<UUID, String> names = access.names(subjects.keySet());
            List<TeacherOption> teachers = subjects.entrySet().stream().map(e -> new TeacherOption(e.getKey(), names.getOrDefault(e.getKey(), ""),
                    String.join(", ", e.getValue()), slots("WHERE s.teacher_id = ? AND s.removed_at IS NULL AND s.starts_at > ? AND s.starts_at < ? "
                            + "AND NOT EXISTS (SELECT 1 FROM meeting_bookings b WHERE b.slot_id = s.id AND b.status = 'BOOKED') ORDER BY s.starts_at LIMIT 40",
                    e.getKey(), now, until))).toList();
            children.add(new ChildTeachers(k, access.name(k), access.sectionLabel(section), teachers));
        }
        List<BookingView> bookings = bookings("WHERE b.parent_id = ? AND s.starts_at > ? ORDER BY s.starts_at", user.personId(),
                Timestamp.from(access.now().minus(14, ChronoUnit.DAYS)));
        return new ParentMeetings(children, bookings);
    }

    @Transactional
    public BookingView book(CurrentUser user, UUID slotId, UUID studentId, String topic) {
        access.requireParentOf(user, studentId);
        String what = FamilyAccess.trim(topic, 500);
        if (what.length() < 3) {
            throw new BadRequestException("Tell the teacher briefly what you would like to talk about.");
        }
        List<Map<String, Object>> found = jdbc.queryForList("SELECT teacher_id, starts_at, site_id FROM meeting_slots WHERE id = ? AND removed_at IS NULL", slotId);
        if (found.isEmpty()) {
            throw new NotFoundException("The slot");
        }
        UUID teacher = (UUID) found.get(0).get("teacher_id");
        Instant at = ((Timestamp) found.get(0).get("starts_at")).toInstant();
        UUID section = access.sectionOf(studentId).orElseThrow(() -> new NotFoundException("The slot"));
        if (!access.classTeachers(section).contains(teacher)) {
            throw new NotFoundException("The slot");   // only your child's own teachers
        }
        if (!at.isAfter(access.now())) {
            throw new ConflictException("This slot has already started.");
        }
        Integer already = jdbc.queryForObject("SELECT count(*) FROM meeting_bookings b JOIN meeting_slots s ON s.id = b.slot_id WHERE b.parent_id = ? "
                + "AND b.student_id = ? AND s.teacher_id = ? AND b.status = 'BOOKED' AND s.starts_at > ?", Integer.class, user.personId(), studentId, teacher, Timestamp.from(access.now()));
        if (already != null && already > 0) {
            throw new ConflictException("You already have a meeting booked with this teacher for this child. Cancel it first to choose another time.");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO meeting_bookings (id, org_id, site_id, slot_id, parent_id, student_id, topic) VALUES (?,?,?,?,?,?,?)",
                    id, user.orgId(), found.get(0).get("site_id"), slotId, user.personId(), studentId, what);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("Someone has just booked this slot. Please choose another time.");
        }
        String when = ZonedDateTime.ofInstant(at, SchoolCalendar.ZONE).format(WHEN);
        String student = access.name(studentId);
        audit.log(user.orgId(), user.siteId(), ActivityActions.MEETING_BOOKED).by(user).on("MeetingSlot", slotId).change(null, when + " · " + student).save();
        access.tell(List.of(teacher), user.personId(), NotificationKind.FAMILY, "موعد جديد مع ولي أمر " + student, "New meeting booked: " + user.name() + " (" + student + ")",
                when + ". Topic: " + what, when + ". الموضوع: " + what);
        return bookings("WHERE b.id = ?", id).get(0);
    }

    /** The parent cancels their booking; the slot becomes free again and the teacher is told. */
    @Transactional
    public BookingView cancelByParent(CurrentUser user, UUID bookingId, String reason) {
        access.requireParent(user);
        BookingView b = bookings("WHERE b.id = ? AND b.parent_id = ?", bookingId, user.personId()).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("The booking"));
        if (!"BOOKED".equals(b.status())) {
            throw new ConflictException("This booking is already cancelled.");
        }
        return cancelBooking(user, bookingId, FamilyAccess.trim(reason, 300));
    }

    private BookingView cancelBooking(CurrentUser user, UUID bookingId, String why) {
        jdbc.update("UPDATE meeting_bookings SET status = 'CANCELLED', cancelled_by = ?, cancel_reason = ?, cancelled_at = ? WHERE id = ? AND status = 'BOOKED'",
                user.personId(), why.isEmpty() ? null : why, Timestamp.from(access.now()), bookingId);
        Map<String, Object> row = jdbc.queryForMap("SELECT b.parent_id, b.student_id, s.teacher_id, s.starts_at FROM meeting_bookings b JOIN meeting_slots s ON s.id = b.slot_id WHERE b.id = ?", bookingId);
        String when = ZonedDateTime.ofInstant(((Timestamp) row.get("starts_at")).toInstant(), SchoolCalendar.ZONE).format(WHEN);
        UUID other = user.is(Role.PARENT) ? (UUID) row.get("teacher_id") : (UUID) row.get("parent_id");
        String student = access.name((UUID) row.get("student_id"));
        audit.log(user.orgId(), user.siteId(), ActivityActions.MEETING_CANCELLED).by(user).on("MeetingBooking", bookingId).change(when, why).save();
        access.tell(List.of(other), user.personId(), NotificationKind.FAMILY, "أُلغي الموعد: " + student, "Meeting cancelled: " + student,
                when + (why.isEmpty() ? "" : ". " + why) + (user.is(Role.PARENT) ? "" : ". You can book another time in Hafez."),
                when + (why.isEmpty() ? "" : ". " + why) + (user.is(Role.PARENT) ? "" : ". يمكنك حجز موعد آخر في حافظ."));
        return bookings("WHERE b.id = ?", bookingId).get(0);
    }

    // =============================================================== reading

    private List<SlotView> slots(String where, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT s.id, s.starts_at, s.minutes, s.mode, s.place, b.id AS booking_id, b.parent_id, b.student_id, b.topic "
                + "FROM meeting_slots s LEFT JOIN meeting_bookings b ON b.slot_id = s.id AND b.status = 'BOOKED' " + where, args);
        List<UUID> ids = new ArrayList<>();
        rows.forEach(r -> { if (r.get("parent_id") != null) { ids.add((UUID) r.get("parent_id")); ids.add((UUID) r.get("student_id")); } });
        Map<UUID, String> names = access.names(ids);
        return rows.stream().map(r -> new SlotView((UUID) r.get("id"), ((Timestamp) r.get("starts_at")).toInstant(), ((Number) r.get("minutes")).intValue(),
                (String) r.get("mode"), (String) r.get("place"), r.get("booking_id") != null, (UUID) r.get("booking_id"),
                r.get("parent_id") == null ? null : names.get((UUID) r.get("parent_id")), r.get("student_id") == null ? null : names.get((UUID) r.get("student_id")),
                (String) r.get("topic"))).toList();
    }

    private List<BookingView> bookings(String where, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT b.id, b.slot_id, s.starts_at, s.minutes, s.mode, s.place, s.teacher_id, b.student_id, b.topic, b.status, "
                + "b.cancel_reason, b.cancelled_by FROM meeting_bookings b JOIN meeting_slots s ON s.id = b.slot_id " + where, args);
        List<UUID> ids = new ArrayList<>();
        rows.forEach(r -> { ids.add((UUID) r.get("teacher_id")); ids.add((UUID) r.get("student_id")); if (r.get("cancelled_by") != null) ids.add((UUID) r.get("cancelled_by")); });
        Map<UUID, String> names = access.names(ids);
        return rows.stream().map(r -> new BookingView((UUID) r.get("id"), (UUID) r.get("slot_id"), ((Timestamp) r.get("starts_at")).toInstant(),
                ((Number) r.get("minutes")).intValue(), (String) r.get("mode"), (String) r.get("place"), names.getOrDefault((UUID) r.get("teacher_id"), ""),
                names.getOrDefault((UUID) r.get("student_id"), ""), (String) r.get("topic"), (String) r.get("status"), (String) r.get("cancel_reason"),
                r.get("cancelled_by") == null ? null : names.get((UUID) r.get("cancelled_by")))).toList();
    }
}
