package qa.sijill.sijill.pickup.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.academic.model.Enrolment;
import qa.sijill.sijill.academic.model.Section;
import qa.sijill.sijill.academic.model.TeachingAssignment;
import qa.sijill.sijill.academic.repository.EnrolmentRepository;
import qa.sijill.sijill.academic.repository.SectionRepository;
import qa.sijill.sijill.academic.repository.TeachingAssignmentRepository;
import qa.sijill.sijill.academic.service.AcademicAccess;
import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.model.Subject;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.organisation.repository.SubjectRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.people.service.ParentLinks;
import qa.sijill.sijill.pickup.dto.PickupDtos;
import qa.sijill.sijill.pickup.model.PickupCollector;
import qa.sijill.sijill.pickup.model.PickupRelease;
import qa.sijill.sijill.pickup.model.PickupRequest;
import qa.sijill.sijill.pickup.model.PickupStatus;
import qa.sijill.sijill.pickup.model.Relationship;
import qa.sijill.sijill.pickup.repository.PickupCollectorRepository;
import qa.sijill.sijill.pickup.repository.PickupReleaseRepository;
import qa.sijill.sijill.pickup.repository.PickupRequestRepository;
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
import qa.sijill.sijill.shared.util.SchoolCalendar;

/**
 * Student pick-up and collection.
 *
 * <ul>
 *   <li><b>Parent:</b> keeps a list of approved collectors for each child, and presses "I have arrived" (as themselves
 *       or naming one approved collector).</li>
 *   <li><b>Teacher or coordinator:</b> is alerted, checks the collector's ID against the list (<i>Verify</i>), then
 *       <i>Release</i>s the child, or <i>Decline</i>s. A collector who is not on the list, or whose approval was
 *       withdrawn, can never be released. Teachers act only on their own classes; coordinators on the whole school.</li>
 *   <li><b>Principal, School IT and the attendance clerk</b> read the board and the log.</li>
 *   <li>Every release is written to an append-only chain-of-custody log (student, collector, exact time, staff member).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PickupService {

    private static final int MAX_COLLECTORS_PER_CHILD = 10;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<PickupStatus> OPEN = EnumSet.of(PickupStatus.ARRIVED, PickupStatus.VERIFYING);

    private final PickupCollectorRepository collectors;
    private final PickupRequestRepository requests;
    private final PickupReleaseRepository releases;
    private final PersonRepository people;
    private final ParentLinks links;
    private final EnrolmentRepository enrolments;
    private final SectionRepository sections;
    private final TeachingAssignmentRepository assignments;
    private final SubjectRepository subjects;
    private final SiteRepository sites;
    private final AuditPublisher audit;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    // =============================================================== parent: the registry

    @Transactional(readOnly = true)
    public List<PickupDtos.CollectorView> collectorsOf(CurrentUser user, UUID studentId) {
        requireParentOf(user, studentId);
        return collectors.findByStudentIdAndActiveTrueOrderByName(studentId).stream().map(PickupDtos.CollectorView::from).toList();
    }

    @Transactional
    public PickupDtos.CollectorView addCollector(CurrentUser user, UUID studentId, PickupDtos.CollectorRequest in) {
        Person student = requireParentOf(user, studentId);
        String name = in.name() == null ? "" : in.name().strip();
        if (name.length() < 2 || name.length() > 100) {
            throw new BadRequestException("Enter the collector's full name.");
        }
        Relationship relationship;
        try {
            relationship = Relationship.valueOf(in.relationship() == null ? "" : in.relationship().strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Choose how this person is related to your child.");
        }
        if (relationship == Relationship.PARENT) {
            throw new BadRequestException("You do not need to add yourself. Choose yourself when you arrive.");
        }
        String id = in.idNumber() == null ? "" : in.idNumber().replaceAll("[^A-Za-z0-9]", "");
        if (id.length() < 4) {
            throw new BadRequestException("Enter the collector's ID number (at least 4 letters or digits). Only the last four are kept.");
        }
        String phone = in.phone() == null ? "" : in.phone().strip();
        if (phone.length() > 30 || !phone.matches("[0-9 +()-]*")) {
            throw new BadRequestException("Enter a phone number using digits, spaces, + ( ) and - only.");
        }
        if (collectors.findByStudentIdAndActiveTrueOrderByName(studentId).size() >= MAX_COLLECTORS_PER_CHILD) {
            throw new ConflictException("You can approve at most " + MAX_COLLECTORS_PER_CHILD + " collectors for one child. Remove one first.");
        }
        PickupCollector c = new PickupCollector();
        c.setOrgId(student.getOrgId());
        c.setSiteId(student.getSiteId());
        c.setStudentId(studentId);
        c.setName(name);
        c.setRelationship(relationship);
        c.setPhone(phone.isEmpty() ? null : phone);
        c.setIdLast4(id.substring(id.length() - 4).toUpperCase(Locale.ROOT));
        c.setCreatedBy(user.personId());
        collectors.save(c);
        audit.log(c.getOrgId(), c.getSiteId(), ActivityActions.PICKUP_COLLECTOR_ADDED).by(user).on("Person", studentId)
                .change(null, name + " (" + relationship + ")").save();
        return PickupDtos.CollectorView.from(c);
    }

    @Transactional
    public void removeCollector(CurrentUser user, UUID collectorId) {
        PickupCollector c = collectors.findById(collectorId).filter(PickupCollector::isActive).orElseThrow(() -> new NotFoundException("The collector"));
        requireParentOf(user, c.getStudentId());
        c.setActive(false);
        audit.log(c.getOrgId(), c.getSiteId(), ActivityActions.PICKUP_COLLECTOR_REMOVED).by(user).on("Person", c.getStudentId())
                .change(c.getName(), null).save();
    }

    // =============================================================== parent: "I have arrived"

    @Transactional
    public PickupDtos.RequestView arrive(CurrentUser user, UUID studentId, UUID collectorId) {
        Person student = requireParentOf(user, studentId);
        Enrolment enrolment = enrolments.findByStudentId(studentId)
                .orElseThrow(() -> new BadRequestException("Your child is not in a class yet. Ask the school office."));
        Section section = sections.findById(enrolment.getSectionId()).orElseThrow(() -> new NotFoundException("The class"));
        if (requests.findFirstByStudentIdAndStatusIn(studentId, OPEN).isPresent()) {
            throw new ConflictException("You have already told the school you have arrived for this child.");
        }
        Person parent = people.findById(user.personId()).orElseThrow(() -> new NotFoundException("Your account"));

        PickupRequest r = new PickupRequest();
        r.setOrgId(student.getOrgId());
        r.setSiteId(student.getSiteId());
        r.setStudentId(studentId);
        r.setStudentName(student.getDisplayName());
        r.setSectionId(section.getId());
        r.setSectionLabel(AcademicAccess.label(section));
        r.setParentId(parent.getId());
        if (collectorId == null) {
            r.setCollectorName(parent.getDisplayName());
            r.setCollectorRelationship(Relationship.PARENT);
            r.setCollectorPhone(parent.getPhone());
        } else {
            PickupCollector c = collectors.findById(collectorId)
                    .filter(x -> x.isActive() && x.getStudentId().equals(studentId))
                    .orElseThrow(() -> new BadRequestException("Choose yourself or someone on your approved list."));
            r.setCollectorId(c.getId());
            r.setCollectorName(c.getName());
            r.setCollectorRelationship(c.getRelationship());
            r.setCollectorIdLast4(c.getIdLast4());
            r.setCollectorPhone(c.getPhone());
        }
        requests.save(r);
        audit.log(r.getOrgId(), r.getSiteId(), ActivityActions.PICKUP_ARRIVED).by(user).on("PickupRequest", r.getId())
                .change(null, r.getStudentName() + " / " + r.getCollectorName()).save();

        String detail = "Collector: " + r.getCollectorName() + " (" + label(r.getCollectorRelationship()) + "). Open the Pick-up board to verify and release.";
        for (Person staff : staffToAlert(r)) {
            publisher.publishEvent(new PersonNotice(staff.getId(), NotificationKind.COLLECTION,
                    "وصل ولي أمر الطالب " + r.getStudentName(), "A parent has arrived for " + r.getStudentName() + " (" + r.getSectionLabel() + ")", detail));
        }
        return PickupDtos.RequestView.from(r);
    }

    @Transactional
    public PickupDtos.RequestView cancel(CurrentUser user, UUID requestId) {
        PickupRequest r = requests.findById(requestId).filter(x -> x.getParentId().equals(user.personId())).orElseThrow(() -> new NotFoundException("The request"));
        if (!r.getStatus().open()) {
            throw new ConflictException("This request is already finished.");
        }
        r.setStatus(PickupStatus.CANCELLED);
        r.setUpdatedAt(Instant.now(clock));
        audit.log(r.getOrgId(), r.getSiteId(), ActivityActions.PICKUP_CANCELLED).by(user).on("PickupRequest", r.getId())
                .change(null, r.getStudentName()).save();
        return PickupDtos.RequestView.from(r);
    }

    /** For each of the parent's children: the approved collectors, the open or latest request of today, and recent releases. */
    @Transactional(readOnly = true)
    public List<PickupDtos.ChildPickup> mine(CurrentUser user) {
        if (!user.is(Role.PARENT)) {
            throw new ForbiddenException("This is for parents.");
        }
        Instant startOfDay = startOfToday();
        List<PickupDtos.ChildPickup> out = new ArrayList<>();
        for (UUID studentId : links.childrenOf(user.personId())) {
            Person student = people.findById(studentId).orElse(null);
            if (student == null) {
                continue;
            }
            String section = enrolments.findByStudentId(studentId).flatMap(e -> sections.findById(e.getSectionId())).map(AcademicAccess::label).orElse("");
            PickupRequest current = requests.findFirstByStudentIdOrderByArrivedAtDesc(studentId)
                    .filter(r -> r.getStatus().open() || r.getUpdatedAt().isAfter(startOfDay)).orElse(null);
            out.add(new PickupDtos.ChildPickup(studentId, student.getDisplayName(), section,
                    collectors.findByStudentIdAndActiveTrueOrderByName(studentId).stream().map(PickupDtos.CollectorView::from).toList(),
                    current == null ? null : PickupDtos.RequestView.from(current),
                    releases.findTop200ByStudentIdOrderByReleasedAtDesc(studentId).stream().limit(5).map(PickupDtos.ReleaseView::from).toList()));
        }
        return out;
    }

    // =============================================================== staff: the live board

    @Transactional(readOnly = true)
    public PickupDtos.Board board(CurrentUser user) {
        requireStaffReader(user);
        Set<UUID> scope = sectionScope(user);
        List<PickupRequest> open = requests.findBySiteIdAndStatusInOrderByArrivedAt(user.siteId(), OPEN).stream().filter(r -> inScope(scope, r)).toList();
        List<PickupRequest> done = requests.findBySiteIdAndStatusInAndUpdatedAtAfterOrderByUpdatedAtDesc(user.siteId(),
                EnumSet.of(PickupStatus.RELEASED, PickupStatus.DECLINED), startOfToday()).stream().filter(r -> inScope(scope, r)).toList();
        return new PickupDtos.Board(
                open.stream().filter(r -> r.getStatus() == PickupStatus.ARRIVED).map(PickupDtos.RequestView::from).toList(),
                open.stream().filter(r -> r.getStatus() == PickupStatus.VERIFYING).map(PickupDtos.RequestView::from).toList(),
                done.stream().map(PickupDtos.RequestView::from).toList(), canAct(user));
    }

    /** The staff member has checked the collector's ID against the approved list. */
    @Transactional
    public PickupDtos.RequestView verify(CurrentUser user, UUID requestId) {
        PickupRequest r = actable(user, requestId);
        if (r.getStatus() != PickupStatus.ARRIVED) {
            throw new ConflictException("This request is already being checked or is finished.");
        }
        requireStillApproved(r);
        r.setStatus(PickupStatus.VERIFYING);
        r.setVerifiedBy(user.personId());
        r.setVerifiedByName(user.name());
        r.setVerifiedAt(Instant.now(clock));
        r.setUpdatedAt(r.getVerifiedAt());
        audit.log(r.getOrgId(), r.getSiteId(), ActivityActions.PICKUP_VERIFIED).by(user).on("PickupRequest", r.getId())
                .change(null, r.getStudentName() + " / " + r.getCollectorName()).save();
        return PickupDtos.RequestView.from(r);
    }

    /** Hands the child over and writes the chain-of-custody record. Only after the ID was verified. */
    @Transactional
    public PickupDtos.RequestView release(CurrentUser user, UUID requestId) {
        PickupRequest r = actable(user, requestId);
        if (r.getStatus() != PickupStatus.VERIFYING) {
            throw new ConflictException(r.getStatus() == PickupStatus.ARRIVED
                    ? "Verify the collector's ID first." : "This request is already finished.");
        }
        requireStillApproved(r);
        Instant now = Instant.now(clock);
        r.setStatus(PickupStatus.RELEASED);
        r.setDecidedBy(user.personId());
        r.setDecidedByName(user.name());
        r.setDecidedAt(now);
        r.setUpdatedAt(now);

        PickupRelease log = new PickupRelease();
        log.setOrgId(r.getOrgId());
        log.setSiteId(r.getSiteId());
        log.setRequestId(r.getId());
        log.setStudentId(r.getStudentId());
        log.setStudentName(r.getStudentName());
        log.setSectionLabel(r.getSectionLabel());
        log.setCollectorName(r.getCollectorName());
        log.setCollectorRelationship(r.getCollectorRelationship());
        log.setReleasedBy(user.personId());
        log.setReleasedByName(user.name());
        log.setReleasedAt(now);
        releases.save(log);

        audit.log(r.getOrgId(), r.getSiteId(), ActivityActions.PICKUP_RELEASED).by(user).on("PickupRequest", r.getId())
                .change(null, r.getStudentName() + " to " + r.getCollectorName()).save();
        String at = ZonedDateTime.ofInstant(now, SchoolCalendar.ZONE).format(CLOCK);
        publisher.publishEvent(new PersonNotice(r.getParentId(), NotificationKind.COLLECTION, r.getStudentName() + " تم تسليمه",
                r.getStudentName() + " was released", r.getStudentName() + " was released to " + r.getCollectorName() + " at " + at + "."));
        return PickupDtos.RequestView.from(r);
    }

    @Transactional
    public PickupDtos.RequestView decline(CurrentUser user, UUID requestId, String reason) {
        PickupRequest r = actable(user, requestId);
        if (!r.getStatus().open()) {
            throw new ConflictException("This request is already finished.");
        }
        String why = reason == null ? "" : reason.strip();
        if (why.isEmpty()) {
            throw new BadRequestException("Give a short reason, so the parent knows what to do.");
        }
        Instant now = Instant.now(clock);
        r.setStatus(PickupStatus.DECLINED);
        r.setDeclineReason(why.length() > 300 ? why.substring(0, 300) : why);
        r.setDecidedBy(user.personId());
        r.setDecidedByName(user.name());
        r.setDecidedAt(now);
        r.setUpdatedAt(now);
        audit.log(r.getOrgId(), r.getSiteId(), ActivityActions.PICKUP_DECLINED).by(user).on("PickupRequest", r.getId())
                .change(null, r.getStudentName() + ": " + r.getDeclineReason()).save();
        publisher.publishEvent(new PersonNotice(r.getParentId(), NotificationKind.COLLECTION, "لم يتم تسليم " + r.getStudentName(),
                r.getStudentName() + " was not released", "The school could not release " + r.getStudentName() + ": " + r.getDeclineReason()));
        return PickupDtos.RequestView.from(r);
    }

    // =============================================================== the chain-of-custody log

    /** Releases between two dates (default: the last 30 days). Staff see their school (teachers their own classes); parents see their children. */
    @Transactional(readOnly = true)
    public List<PickupDtos.ReleaseView> log(CurrentUser user, LocalDate from, LocalDate to) {
        if (user.is(Role.PARENT)) {
            Set<UUID> mine = new LinkedHashSet<>(links.childrenOf(user.personId()));
            return mine.stream().flatMap(id -> releases.findTop200ByStudentIdOrderByReleasedAtDesc(id).stream())
                    .sorted((a, b) -> b.getReleasedAt().compareTo(a.getReleasedAt())).map(PickupDtos.ReleaseView::from).toList();
        }
        requireStaffReader(user);
        LocalDate end = to == null ? LocalDate.now(clock.withZone(SchoolCalendar.ZONE)) : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        Set<UUID> scope = sectionScope(user);
        Set<UUID> students = scope == null ? null : scope.stream().flatMap(s -> enrolments.findBySectionId(s).stream())
                .map(Enrolment::getStudentId).collect(Collectors.toSet());
        return releases.findTop500BySiteIdAndReleasedAtBetweenOrderByReleasedAtDesc(user.siteId(),
                        start.atStartOfDay(SchoolCalendar.ZONE).toInstant(), end.plusDays(1).atStartOfDay(SchoolCalendar.ZONE).toInstant()).stream()
                .filter(r -> students == null || students.contains(r.getStudentId())).map(PickupDtos.ReleaseView::from).toList();
    }

    // =============================================================== helpers

    private Person requireParentOf(CurrentUser user, UUID studentId) {
        if (!user.is(Role.PARENT) || !links.isLinked(user.personId(), studentId)) {
            throw new NotFoundException("The child");
        }
        return people.findById(studentId).orElseThrow(() -> new NotFoundException("The child"));
    }

    private void requireStaffReader(CurrentUser user) {
        if (!user.isAny(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL, Role.SCHOOL_IT, Role.ATTENDANCE_OFFICER)) {
            throw new ForbiddenException("The pick-up board is for teachers, coordinators, the principal, School IT and the attendance clerk.");
        }
    }

    private static boolean canAct(CurrentUser user) {
        return user.isAny(Role.TEACHER, Role.COORDINATOR);
    }

    /** The classes a teacher may handle; null means the whole school (coordinator, principal, School IT). */
    private Set<UUID> sectionScope(CurrentUser user) {
        if (!user.is(Role.TEACHER)) {
            return null;
        }
        return assignments.findByTeacherId(user.personId()).stream().map(TeachingAssignment::getSectionId).collect(Collectors.toSet());
    }

    private static boolean inScope(Set<UUID> scope, PickupRequest r) {
        return scope == null || scope.contains(r.getSectionId());
    }

    /** A request the user may act on: same school, and for a teacher one of their own classes. */
    private PickupRequest actable(CurrentUser user, UUID requestId) {
        if (!canAct(user)) {
            throw new ForbiddenException("Only a teacher or a coordinator can verify and release a child.");
        }
        PickupRequest r = requests.findById(requestId).filter(x -> x.getSiteId().equals(user.siteId())).orElseThrow(() -> new NotFoundException("The request"));
        if (!inScope(sectionScope(user), r)) {
            throw new NotFoundException("The request");
        }
        return r;
    }

    /** The registry is checked again at the moment of verifying and releasing: a withdrawn approval stops the release. */
    private void requireStillApproved(PickupRequest r) {
        if (r.getCollectorId() == null) {
            if (!links.isLinked(r.getParentId(), r.getStudentId())) {
                throw new ConflictException("This parent is no longer linked to the child. Decline the request.");
            }
            return;
        }
        boolean ok = collectors.findById(r.getCollectorId()).filter(c -> c.isActive() && c.getStudentId().equals(r.getStudentId())).isPresent();
        if (!ok) {
            throw new ConflictException("This person is no longer on the approved list. Decline the request.");
        }
    }

    /** The child's teachers and coordinators: teachers of the class, the coordinators of their subjects, and the school coordinator. */
    private List<Person> staffToAlert(PickupRequest r) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (TeachingAssignment ta : assignments.findBySectionId(r.getSectionId())) {
            ids.add(ta.getTeacherId());
            subjects.findById(ta.getSubjectId()).map(Subject::getCoordinatorId).ifPresent(ids::add);
        }
        sites.findById(r.getSiteId()).map(Site::getCoordinatorId).ifPresent(ids::add);
        ids.remove(null);
        return ids.stream().map(people::findById).flatMap(java.util.Optional::stream).filter(Person::canSignIn).toList();
    }

    private Instant startOfToday() {
        return LocalDate.now(clock.withZone(SchoolCalendar.ZONE)).atStartOfDay(SchoolCalendar.ZONE).toInstant();
    }

    private static String label(Relationship r) {
        return r.name().toLowerCase(Locale.ROOT);
    }
}
