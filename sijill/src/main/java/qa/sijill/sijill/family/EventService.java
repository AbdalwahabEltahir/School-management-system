package qa.sijill.sijill.family;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.family.FamilyDtos.ChildConsent;
import qa.sijill.sijill.family.FamilyDtos.EventDetail;
import qa.sijill.sijill.family.FamilyDtos.EventRequest;
import qa.sijill.sijill.family.FamilyDtos.EventView;
import qa.sijill.sijill.family.FamilyDtos.EventsPage;
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
 * Trips and events that need each parent's permission. A teacher creates one for classes they teach (the coordinator
 * and the principal assistant for any class); every parent of those classes is asked. A parent approves or declines
 * for each child, and can change their answer until the deadline. The organiser follows the answers and can remind
 * the parents who have not answered.
 */
@Service
@RequiredArgsConstructor
public class EventService {

    public static final String STATEMENT_EN = "I give permission for my child to take part in this trip or event, and I have read the details above.";
    public static final String STATEMENT_AR = "أوافق على مشاركة طفلي في هذه الرحلة أو الفعالية، وقد قرأت التفاصيل أعلاه.";

    private final JdbcTemplate jdbc;
    private final FamilyAccess access;
    private final AuditPublisher audit;

    private record Ev(UUID id, UUID siteId, String title, String details, String location, Instant startsAt, Instant endsAt,
                      BigDecimal cost, LocalDate deadline, UUID createdBy, Instant cancelledAt, String cancelReason) {}

    private static boolean canCreate(CurrentUser user) {
        return user.isAny(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL_ASSISTANT);
    }

    // =============================================================== staff

    @Transactional
    public EventDetail create(CurrentUser user, EventRequest r) {
        if (!canCreate(user)) {
            throw new ForbiddenException("Trips and events are created by teachers, coordinators and the principal assistant.");
        }
        String title = FamilyAccess.trim(r.title(), 200);
        if (title.length() < 3) {
            throw new BadRequestException("Give the trip or event a title.");
        }
        Set<UUID> sections = new LinkedHashSet<>(r.sectionIds());
        if (sections.isEmpty()) {
            throw new BadRequestException("Choose at least one class.");
        }
        Set<UUID> taught = user.is(Role.TEACHER) ? access.sectionsTaughtBy(user.personId()) : null;
        for (UUID s : sections) {
            if (!access.sectionInSite(s, user.siteId()) || (taught != null && !taught.contains(s))) {
                throw new ForbiddenException("You can invite only classes you teach.");
            }
        }
        LocalDate today = access.today();
        if (r.date().isBefore(today)) {
            throw new BadRequestException("The trip or event must be today or later.");
        }
        if (!r.endTime().isAfter(r.startTime())) {
            throw new BadRequestException("The end time must be after the start time.");
        }
        if (r.consentDeadline().isBefore(today) || r.consentDeadline().isAfter(r.date())) {
            throw new BadRequestException("The answer deadline must be between today and the day of the trip.");
        }
        if (r.cost() != null && (r.cost().signum() < 0 || r.cost().compareTo(BigDecimal.valueOf(100000)) > 0)) {
            throw new BadRequestException("Enter a cost between 0 and 100000, or leave it empty.");
        }
        UUID id = UUID.randomUUID();
        Instant starts = r.date().atTime(r.startTime()).atZone(SchoolCalendar.ZONE).toInstant();
        Instant ends = r.date().atTime(r.endTime()).atZone(SchoolCalendar.ZONE).toInstant();
        jdbc.update("INSERT INTO school_events (id, org_id, site_id, title, details, location, starts_at, ends_at, cost, consent_deadline, created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, user.orgId(), user.siteId(), title, FamilyAccess.trim(r.details(), 4000), FamilyAccess.trim(r.location(), 200),
                Timestamp.from(starts), Timestamp.from(ends), r.cost(), Date.valueOf(r.consentDeadline()), user.personId());
        for (UUID s : sections) {
            jdbc.update("INSERT INTO school_event_sections (event_id, section_id) VALUES (?, ?)", id, s);
        }
        audit.log(user.orgId(), user.siteId(), ActivityActions.EVENT_CREATED).by(user).on("SchoolEvent", id).change(null, title + " on " + r.date()).save();
        access.tell(parentsOf(id), user.personId(), NotificationKind.FAMILY, "مطلوب موافقتك: " + title, "Your permission is needed: " + title,
                r.date() + (r.location() == null || r.location().isBlank() ? "" : " · " + r.location().strip()) + ". Please answer by " + r.consentDeadline() + ".",
                r.date() + (r.location() == null || r.location().isBlank() ? "" : " · " + r.location().strip()) + ". يرجى الرد قبل " + r.consentDeadline() + ".");
        return detail(user, id);
    }

    @Transactional
    public EventDetail cancel(CurrentUser user, UUID id, String reason) {
        Ev e = own(user, id);
        if (e.cancelledAt() != null) {
            throw new ConflictException("This trip or event is already cancelled.");
        }
        String why = FamilyAccess.trim(reason, 300);
        if (why.isEmpty()) {
            throw new BadRequestException("Tell the parents why it is cancelled.");
        }
        jdbc.update("UPDATE school_events SET cancelled_at = ?, cancel_reason = ? WHERE id = ?", Timestamp.from(access.now()), why, id);
        audit.log(user.orgId(), user.siteId(), ActivityActions.EVENT_CANCELLED).by(user).on("SchoolEvent", id).change(e.title(), why).save();
        access.tell(parentsOf(id), user.personId(), NotificationKind.FAMILY, "أُلغيت: " + e.title(), "Cancelled: " + e.title(), why, why);
        return detail(user, id);
    }

    /** Reminds the parents of every child with no answer yet. Returns how many parents were reminded. */
    @Transactional
    public int remind(CurrentUser user, UUID id) {
        Ev e = own(user, id);
        if (e.cancelledAt() != null || access.today().isAfter(e.deadline())) {
            throw new ConflictException("Answers are closed for this trip or event.");
        }
        List<UUID> waiting = new ArrayList<>();
        for (UUID s : studentsOf(id)) {
            if (consents(id).get(s) == null) {
                waiting.add(s);
            }
        }
        List<UUID> parents = access.people().parentsOf(waiting);
        access.tell(parents, user.personId(), NotificationKind.FAMILY, "تذكير: مطلوب موافقتك على " + e.title(), "Reminder: your permission is needed for " + e.title(),
                "Please answer by " + e.deadline() + ".", "يرجى الرد قبل " + e.deadline() + ".");
        return new LinkedHashSet<>(parents).size();
    }

    // =============================================================== parent

    @Transactional
    public EventView answer(CurrentUser user, UUID id, UUID studentId, String decision, String note, boolean agreed) {
        access.requireParentOf(user, studentId);
        Ev e = find(id).orElseThrow(() -> new NotFoundException("The trip or event"));
        if (!studentsOf(id).contains(studentId)) {   // only the classes the event was sent to
            throw new NotFoundException("The trip or event");
        }
        if (e.cancelledAt() != null) {
            throw new ConflictException("This trip or event was cancelled.");
        }
        if (access.today().isAfter(e.deadline())) {
            throw new ConflictException("The deadline to answer has passed. Please contact the school.");
        }
        String d = decision == null ? "" : decision.strip().toUpperCase(Locale.ROOT);
        if (!d.equals("APPROVED") && !d.equals("DECLINED")) {
            throw new BadRequestException("Choose whether your child may take part.");
        }
        if (d.equals("APPROVED") && !agreed) {
            throw new BadRequestException("Please confirm the permission statement.");
        }
        String text = FamilyAccess.trim(note, 500);
        String statement = d.equals("APPROVED") ? STATEMENT_EN + " | " + STATEMENT_AR : "";
        jdbc.update("INSERT INTO event_consents (event_id, student_id, parent_id, decision, note, statement, decided_at) VALUES (?,?,?,?,?,?,?) "
                        + "ON CONFLICT (event_id, student_id) DO UPDATE SET parent_id = EXCLUDED.parent_id, decision = EXCLUDED.decision, note = EXCLUDED.note, "
                        + "statement = EXCLUDED.statement, decided_at = EXCLUDED.decided_at",
                id, studentId, user.personId(), d, text, statement, Timestamp.from(access.now()));
        audit.log(user.orgId(), user.siteId(), ActivityActions.EVENT_CONSENT).by(user).on("SchoolEvent", id).change(null, access.name(studentId) + ": " + d).save();
        return view(user, e);
    }

    /** Upcoming and recent events: a parent's are those of their children's classes; staff see the school's. */
    @Transactional(readOnly = true)
    public EventsPage list(CurrentUser user) {
        List<Ev> events;
        if (user.is(Role.PARENT)) {
            Set<UUID> sections = new LinkedHashSet<>();
            access.people().childrenOf(user.personId()).forEach(k -> access.sectionOf(k).ifPresent(sections::add));
            Set<UUID> ids = new LinkedHashSet<>();
            for (UUID s : sections) {
                ids.addAll(jdbc.queryForList("SELECT event_id FROM school_event_sections WHERE section_id = ?", UUID.class, s));
            }
            events = ids.stream().map(this::find).flatMap(java.util.Optional::stream).toList();
        } else if (user.isAny(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL, Role.PRINCIPAL_ASSISTANT)) {
            events = evs("WHERE site_id = ?", user.siteId());
        } else {
            throw new ForbiddenException("Trips and events are for parents, teachers, coordinators and the school leadership.");
        }
        Instant cutoff = access.today().minusDays(30).atStartOfDay(SchoolCalendar.ZONE).toInstant();
        Instant startOfToday = access.today().atStartOfDay(SchoolCalendar.ZONE).toInstant();
        List<EventView> upcoming = new ArrayList<>(), past = new ArrayList<>();
        for (Ev e : events) {
            if (!e.endsAt().isBefore(startOfToday)) {
                upcoming.add(view(user, e));
            } else if (e.endsAt().isAfter(cutoff)) {
                past.add(view(user, e));
            }
        }
        upcoming.sort((a, b) -> a.startsAt().compareTo(b.startsAt()));
        past.sort((a, b) -> b.startsAt().compareTo(a.startsAt()));
        return new EventsPage(upcoming, past, canCreate(user), STATEMENT_EN, STATEMENT_AR);
    }

    @Transactional(readOnly = true)
    public EventDetail detail(CurrentUser user, UUID id) {
        if (!user.isAny(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL, Role.PRINCIPAL_ASSISTANT)) {
            throw new ForbiddenException("The answers are for the school staff.");
        }
        Ev e = find(id).filter(x -> x.siteId().equals(user.siteId())).orElseThrow(() -> new NotFoundException("The trip or event"));
        Map<UUID, Object[]> answers = consents(id);
        List<UUID> students = studentsOf(id);
        List<UUID> nameIds = new ArrayList<>(students);
        answers.values().forEach(a -> nameIds.add((UUID) a[3]));
        Map<UUID, String> names = access.names(nameIds);
        List<ChildConsent> roster = students.stream().map(s -> {
            Object[] a = answers.get(s);
            return new ChildConsent(s, names.getOrDefault(s, ""), a == null ? null : (String) a[0], a == null ? null : (String) a[1],
                    a == null ? null : (Instant) a[2], a == null ? null : names.get((UUID) a[3]));
        }).sorted((x, y) -> x.student().compareToIgnoreCase(y.student())).toList();
        return new EventDetail(view(user, e), roster);
    }

    // =============================================================== helpers

    private Ev own(CurrentUser user, UUID id) {
        Ev e = find(id).filter(x -> x.siteId().equals(user.siteId())).orElseThrow(() -> new NotFoundException("The trip or event"));
        if (!e.createdBy().equals(user.personId()) && !user.isAny(Role.PRINCIPAL_ASSISTANT)) {
            throw new ForbiddenException("Only the organiser or the principal assistant can change this trip or event.");
        }
        return e;
    }

    private java.util.Optional<Ev> find(UUID id) {
        return evs("WHERE id = ?", id).stream().findFirst();
    }

    private List<Ev> evs(String where, Object... args) {
        return jdbc.query("SELECT id, site_id, title, details, location, starts_at, ends_at, cost, consent_deadline, created_by, cancelled_at, cancel_reason FROM school_events " + where,
                (rs, i) -> new Ev(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getTimestamp(6).toInstant(), rs.getTimestamp(7).toInstant(), rs.getBigDecimal(8), rs.getDate(9).toLocalDate(),
                        rs.getObject(10, UUID.class), RiskService.inst(rs.getTimestamp(11)), rs.getString(12)), args);
    }

    private List<UUID> sectionsOf(UUID eventId) {
        return jdbc.queryForList("SELECT section_id FROM school_event_sections WHERE event_id = ?", UUID.class, eventId);
    }

    private List<UUID> studentsOf(UUID eventId) {
        return jdbc.queryForList("SELECT e.student_id FROM enrolments e JOIN school_event_sections s ON s.section_id = e.section_id WHERE s.event_id = ?",
                UUID.class, eventId);
    }

    private List<UUID> parentsOf(UUID eventId) {
        return access.people().parentsOf(studentsOf(eventId));
    }

    /** student -> {decision, note, decidedAt, parentId} */
    private Map<UUID, Object[]> consents(UUID eventId) {
        Map<UUID, Object[]> out = new HashMap<>();
        jdbc.query("SELECT student_id, decision, note, decided_at, parent_id FROM event_consents WHERE event_id = ?",
                rs -> { out.put(rs.getObject(1, UUID.class), new Object[] {rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getObject(5, UUID.class)}); },
                eventId);
        return out;
    }

    private EventView view(CurrentUser user, Ev e) {
        List<UUID> students = studentsOf(e.id());
        Map<UUID, Object[]> answers = consents(e.id());
        int approved = 0, declined = 0;
        for (UUID s : students) {
            Object[] a = answers.get(s);
            if (a != null && "APPROVED".equals(a[0])) {
                approved++;
            } else if (a != null) {
                declined++;
            }
        }
        List<ChildConsent> children = List.of();
        if (user.is(Role.PARENT)) {
            List<UUID> mine = access.people().childrenOf(user.personId()).stream().filter(students::contains).toList();
            List<UUID> ids = new ArrayList<>(mine);
            mine.forEach(k -> { Object[] a = answers.get(k); if (a != null) ids.add((UUID) a[3]); });
            Map<UUID, String> names = access.names(ids);
            children = mine.stream().map(k -> {
                Object[] a = answers.get(k);
                return new ChildConsent(k, names.getOrDefault(k, ""), a == null ? null : (String) a[0], a == null ? null : (String) a[1],
                        a == null ? null : (Instant) a[2], a == null ? null : names.get((UUID) a[3]));
            }).toList();
        }
        Map<UUID, String> labels = access.people().sectionLabels(e.siteId());
        List<String> sections = sectionsOf(e.id()).stream().map(s -> labels.getOrDefault(s, "")).sorted().toList();
        boolean open = e.cancelledAt() == null && !access.today().isAfter(e.deadline());
        return new EventView(e.id(), e.title(), e.details(), e.location(), e.startsAt(), e.endsAt(), e.cost(), e.deadline(), open,
                e.cancelledAt() != null, e.cancelReason(), sections, access.name(e.createdBy()), e.createdBy().equals(user.personId()),
                students.size(), approved, declined, students.size() - approved - declined, children);
    }
}
