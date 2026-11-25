package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import qa.sijill.sijill.academic.dto.AcademicDtos.MarkInput;
import qa.sijill.sijill.academic.model.AttendanceStatus;
import qa.sijill.sijill.academic.model.Enrolment;
import qa.sijill.sijill.academic.model.Section;
import qa.sijill.sijill.academic.model.TeachingAssignment;
import qa.sijill.sijill.academic.repository.EnrolmentRepository;
import qa.sijill.sijill.academic.repository.SectionRepository;
import qa.sijill.sijill.academic.repository.TeachingAssignmentRepository;
import qa.sijill.sijill.academic.service.AcademicAccess;
import qa.sijill.sijill.academic.service.AcademicSetupService;
import qa.sijill.sijill.academic.service.AttendanceService;
import qa.sijill.sijill.academic.service.MarksService;
import qa.sijill.sijill.family.AbsenceService;
import qa.sijill.sijill.family.EarlyPickupService;
import qa.sijill.sijill.family.EventService;
import qa.sijill.sijill.family.FamilyDtos;
import qa.sijill.sijill.family.MeetingService;
import qa.sijill.sijill.family.RiskService;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.service.ParentLinks;
import qa.sijill.sijill.pickup.dto.PickupDtos;
import qa.sijill.sijill.pickup.service.PickupService;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.util.SchoolCalendar;

/**
 * Family engagement, end to end against a real database: low-grade warnings, absence notes, trip permission, early
 * pick-up requests and parent-teacher meetings, with who may do what and who is told.
 */
class FamilyEngagementTest extends WorldTestSupport {

    @Autowired RiskService risk;
    @Autowired AbsenceService absences;
    @Autowired EventService events;
    @Autowired EarlyPickupService early;
    @Autowired MeetingService meetings;
    @Autowired MarksService marks;
    @Autowired AttendanceService attendance;
    @Autowired AcademicSetupService academic;
    @Autowired AcademicAccess academicAccess;
    @Autowired PickupService pickup;
    @Autowired SectionRepository sections;
    @Autowired EnrolmentRepository enrolments;
    @Autowired TeachingAssignmentRepository assignments;
    @Autowired ParentLinks links;
    @Autowired JdbcTemplate jdbc;

    /** A class 7B with two children (each with their own parent), maths taught by the world's teacher. */
    private record Scene(World w, Section section, Person amal, Person amalMum, Person omar, Person omarDad, Person clerk, Person assistant,
                         Person otherTeacher) {}

    private Scene scene() {
        World w = world();
        Section section = section(w, 7, "B");
        Person amal = student(w, section, "Amal Al-Sulaiti");
        Person omar = student(w, section, "Omar Haddad");
        Person amalMum = parent(w, amal, "Maha Al-Sulaiti");
        Person omarDad = parent(w, omar, "Khalid Haddad");
        teach(w, section, w.teacher());
        Person clerk = person(w.org(), w.school(), "clerk@" + w.school().getDomain(), "Cleo Clerk", Role.ATTENDANCE_OFFICER, null);
        Person assistant = person(w.org(), w.school(), "assistant@" + w.school().getDomain(), "Asma Assistant", Role.PRINCIPAL_ASSISTANT, null);
        Person otherTeacher = person(w.org(), w.school(), "other@" + w.school().getDomain(), "Olga Other", Role.TEACHER, w.maths());
        return new Scene(w, section, amal, amalMum, omar, omarDad, clerk, assistant, otherTeacher);
    }

    private Section section(World w, int grade, String name) {
        Section s = new Section();
        s.setOrgId(w.org().getId());
        s.setSiteId(w.school().getId());
        s.setGrade(grade);
        s.setName(name);
        return sections.save(s);
    }

    private Person student(World w, Section section, String name) {
        Person p = person(w.org(), w.school(), "s-" + UUID.randomUUID() + "@" + w.school().getDomain(), name, Role.STUDENT, null);
        Enrolment e = new Enrolment();
        e.setOrgId(w.org().getId());
        e.setSiteId(w.school().getId());
        e.setSectionId(section.getId());
        e.setStudentId(p.getId());
        enrolments.save(e);
        return p;
    }

    private Person parent(World w, Person child, String name) {
        Person p = person(w.org(), w.school(), "p-" + UUID.randomUUID() + "@family.example", name, Role.PARENT, null);
        links.link(p.getId(), child.getId());
        return p;
    }

    private void teach(World w, Section section, Person teacher) {
        TeachingAssignment ta = new TeachingAssignment();
        ta.setOrgId(w.org().getId());
        ta.setSiteId(w.school().getId());
        ta.setSectionId(section.getId());
        ta.setSubjectId(w.maths().getId());
        ta.setTeacherId(teacher.getId());
        assignments.save(ta);
    }

    private UUID assess(Scene s, String name, int amalMark, int omarMark) {
        var a = marks.create(user(s.w().teacher()), s.section().getId(), s.w().maths().getId(), 1, name, BigDecimal.valueOf(100));
        marks.enter(user(s.w().teacher()), a.id(), List.of(new MarkInput(s.amal().getId(), BigDecimal.valueOf(amalMark), false),
                new MarkInput(s.omar().getId(), BigDecimal.valueOf(omarMark), false)));
        marks.submit(user(s.w().teacher()), a.id());
        return a.id();
    }

    private LocalDate nextSchoolDay() {
        LocalDate d = academicAccess.today().plusDays(1);
        while (!SchoolCalendar.isSchoolDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    // =============================================================== 1. low-grade warnings

    @Test
    void lowMarksRaiseAWarningAutomaticallyForTheRightStaffAndItClearsWhenMarksRecover() {
        Scene s = scene();
        LocalDate today = academicAccess.today();
        academic.saveTerm(user(s.w().it()), "2026-2027", 1, "Term 1", today.minusDays(30), today.plusDays(60));

        assess(s, "Quiz 1", 40, 90);
        // submitting the marks raised the flag at once (no one had to open the page), and told the teacher, coordinator and assistant
        assertThat(count(s.w().teacher(), NotificationKind.AT_RISK)).isEqualTo(1);
        assertThat(count(s.w().coordinator(), NotificationKind.AT_RISK)).isEqualTo(1);
        assertThat(count(s.assistant(), NotificationKind.AT_RISK)).isEqualTo(1);
        assertThat(count(s.amalMum(), NotificationKind.AT_RISK)).isZero();        // parents are told only when staff decide to
        assertThat(count(s.otherTeacher(), NotificationKind.AT_RISK)).isZero();

        var board = risk.board(user(s.w().teacher()));
        assertThat(board.open()).singleElement().satisfies(f -> {
            assertThat(f.student()).isEqualTo("Amal Al-Sulaiti");
            assertThat(f.level()).isEqualTo("HIGH");
            assertThat(f.averagePct()).isEqualByComparingTo("40");
            assertThat(f.canAct()).isTrue();
        });
        // a teacher who does not teach this class sees nothing; the principal sees it but cannot act; parents cannot open it
        assertThat(risk.board(user(s.otherTeacher())).open()).isEmpty();
        assertThat(risk.board(user(s.w().principal())).open()).singleElement().satisfies(f -> assertThat(f.canAct()).isFalse());
        assertThatThrownBy(() -> risk.board(user(s.amalMum()))).isInstanceOf(ForbiddenException.class);
        UUID flag = board.open().get(0).id();
        assertThatThrownBy(() -> risk.note(user(s.otherTeacher()), flag, "x")).isInstanceOf(NotFoundException.class);

        // a second low mark updates the same flag (no second alert); opening the page again does not alert again
        assess(s, "Quiz 2", 70, 95);
        assertThat(risk.board(user(s.w().teacher())).open()).singleElement().satisfies(f -> {
            assertThat(f.id()).isEqualTo(flag);
            assertThat(f.level()).isEqualTo("WATCH");                                      // 55%: below the line, not high
            assertThat(f.assessments()).isEqualTo(2);
        });
        assertThat(count(s.w().teacher(), NotificationKind.AT_RISK)).isEqualTo(1);

        risk.note(user(s.w().teacher()), flag, "Extra practice on Tuesdays.");
        var told = risk.tellParents(user(s.w().teacher()), flag, "Please check her homework this week.");
        assertThat(told.parentsToldAt()).isNotNull();
        assertThat(told.note()).isEqualTo("Extra practice on Tuesdays.");
        assertThat(count(s.amalMum(), NotificationKind.AT_RISK)).isEqualTo(1);
        assertThat(count(s.omarDad(), NotificationKind.AT_RISK)).isZero();

        // the average recovers: the flag resolves by itself and moves to "improved"
        assess(s, "Quiz 3", 100, 90);
        var after = risk.board(user(s.w().teacher()));
        assertThat(after.open()).isEmpty();
        assertThat(after.improved()).singleElement().satisfies(f -> assertThat(f.id()).isEqualTo(flag));
        assertThatThrownBy(() -> risk.tellParents(user(s.w().teacher()), flag, null)).isInstanceOf(BadRequestException.class);
    }

    // =============================================================== 2. absence notes

    @Test
    void aParentExplainsAnAbsenceAndAcceptingItExcusesTheDay() {
        Scene s = scene();
        LocalDate today = academicAccess.today();
        var clerk = user(s.clerk());
        attendance.record(clerk, s.section().getId(), today, Map.of(s.amal().getId(), AttendanceStatus.ABSENT), null);
        attendance.record(clerk, s.section().getId(), today, Map.of(s.omar().getId(), AttendanceStatus.PRESENT), null);   // saved again
        // the parent is asked for the reason once, however often the day is saved
        assertThat(count(s.amalMum(), NotificationKind.FAMILY)).isEqualTo(1);
        assertThat(count(s.omarDad(), NotificationKind.FAMILY)).isZero();
        assertThat(absences.mine(user(s.amalMum())).unexplained()).singleElement().satisfies(d -> assertThat(d.date()).isEqualTo(today));

        // only for your own child, a real reason, and "other" needs words
        assertThatThrownBy(() -> absences.submit(user(s.omarDad()), new FamilyDtos.ExcuseRequest(s.amal().getId(), today, null, "ILLNESS", "")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> absences.submit(user(s.amalMum()), new FamilyDtos.ExcuseRequest(s.amal().getId(), today, null, "OTHER", " ")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> absences.submit(user(s.amalMum()), new FamilyDtos.ExcuseRequest(s.amal().getId(), today.minusDays(40), today, "ILLNESS", "")))
                .isInstanceOf(BadRequestException.class);

        var note = absences.submit(user(s.amalMum()), new FamilyDtos.ExcuseRequest(s.amal().getId(), today, null, "ILLNESS", "Fever"));
        assertThat(note.status()).isEqualTo("PENDING");
        assertThat(count(s.clerk(), NotificationKind.FAMILY)).isEqualTo(1);
        assertThat(count(s.assistant(), NotificationKind.FAMILY)).isEqualTo(1);
        assertThat(absences.mine(user(s.amalMum())).unexplained()).isEmpty();
        assertThatThrownBy(() -> absences.submit(user(s.amalMum()), new FamilyDtos.ExcuseRequest(s.amal().getId(), today, null, "FAMILY", "")))
                .isInstanceOf(ConflictException.class);                                          // one note per day

        // teachers cannot review; a rejection needs a reason
        assertThatThrownBy(() -> absences.review(user(s.w().teacher()), note.id(), "ACCEPTED", null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> absences.review(clerk, note.id(), "REJECTED", "")).isInstanceOf(BadRequestException.class);
        assertThat(absences.board(clerk).pending()).extracting(FamilyDtos.ExcuseView::id).containsExactly(note.id());

        var accepted = absences.review(clerk, note.id(), "ACCEPTED", null);
        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(accepted.reviewedBy()).isEqualTo("Cleo Clerk");
        assertThat(statusOn(s.amal(), today)).isEqualTo("LEAVE");                              // the absence is now excused
        assertThat(count(s.amalMum(), NotificationKind.FAMILY)).isEqualTo(2);
        assertThatThrownBy(() -> absences.review(clerk, note.id(), "REJECTED", "late")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> absences.cancel(user(s.amalMum()), note.id())).isInstanceOf(ConflictException.class);
    }

    @Test
    void aNoteAcceptedBeforeTheDayExcusesTheAbsenceWhenAttendanceIsTaken() {
        Scene s = scene();
        LocalDate today = academicAccess.today();
        var note = absences.submit(user(s.omarDad()), new FamilyDtos.ExcuseRequest(s.omar().getId(), today, today.plusDays(2), "MEDICAL", "Dentist"));
        absences.review(user(s.assistant()), note.id(), "ACCEPTED", "Get well soon");
        attendance.record(user(s.clerk()), s.section().getId(), today, Map.of(s.omar().getId(), AttendanceStatus.ABSENT), null);
        assertThat(statusOn(s.omar(), today)).isEqualTo("LEAVE");
        // no "why was he absent?" message: the school already knows
        assertThat(count(s.omarDad(), NotificationKind.FAMILY)).isEqualTo(1);                   // only the acceptance
        assertThat(absences.board(user(s.clerk())).today()).extracting(FamilyDtos.ExcuseView::id).containsExactly(note.id());

        // a pending note can be withdrawn by the parent
        var planned = absences.submit(user(s.amalMum()), new FamilyDtos.ExcuseRequest(s.amal().getId(), today.plusDays(7), null, "TRAVEL", ""));
        assertThat(absences.cancel(user(s.amalMum()), planned.id()).status()).isEqualTo("CANCELLED");
    }

    private String statusOn(Person student, LocalDate day) {
        return jdbc.queryForObject("SELECT status FROM attendance_records WHERE student_id = ? AND attendance_date = ?", String.class, student.getId(), day);
    }

    // =============================================================== 3. trips and events

    @Test
    void parentsApproveATripOnlineAndTheOrganiserFollowsTheAnswers() {
        Scene s = scene();
        Section other = section(s.w(), 8, "A");
        LocalDate day = academicAccess.today().plusDays(10);
        var teacher = user(s.w().teacher());
        assertThatThrownBy(() -> events.create(teacher, trip(day, day.plusDays(1), List.of(other.getId())))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> events.create(teacher, trip(day, day.plusDays(1), List.of(s.section().getId())))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> events.create(user(s.amalMum()), trip(day, day.minusDays(2), List.of(s.section().getId())))).isInstanceOf(ForbiddenException.class);

        var created = events.create(teacher, trip(day, day.minusDays(2), List.of(s.section().getId())));
        UUID id = created.event().id();
        assertThat(created.event().students()).isEqualTo(2);
        assertThat(created.event().waiting()).isEqualTo(2);
        assertThat(count(s.amalMum(), NotificationKind.FAMILY)).isEqualTo(1);
        assertThat(count(s.omarDad(), NotificationKind.FAMILY)).isEqualTo(1);

        // the parent sees it for their own child only, and must agree to the statement to approve
        var page = events.list(user(s.amalMum()));
        assertThat(page.upcoming()).singleElement().satisfies(e -> assertThat(e.children()).extracting(FamilyDtos.ChildConsent::student).containsExactly("Amal Al-Sulaiti"));
        assertThatThrownBy(() -> events.answer(user(s.amalMum()), id, s.amal().getId(), "APPROVED", "", false)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> events.answer(user(s.amalMum()), id, s.omar().getId(), "APPROVED", "", true)).isInstanceOf(NotFoundException.class);
        var answered = events.answer(user(s.amalMum()), id, s.amal().getId(), "APPROVED", "She has asthma: inhaler in her bag", true);
        assertThat(answered.approved()).isEqualTo(1);
        assertThat(answered.children().get(0).decision()).isEqualTo("APPROVED");
        // a parent can change their mind until the deadline
        assertThat(events.answer(user(s.amalMum()), id, s.amal().getId(), "DECLINED", "", false).declined()).isEqualTo(1);
        events.answer(user(s.amalMum()), id, s.amal().getId(), "APPROVED", "", true);

        var detail = events.detail(teacher, id);
        assertThat(detail.roster()).extracting(FamilyDtos.ChildConsent::decision).containsExactlyInAnyOrder("APPROVED", null);
        assertThat(jdbc.queryForObject("SELECT statement FROM event_consents WHERE event_id = ?", String.class, id)).contains("I give permission");

        assertThat(events.remind(teacher, id)).isEqualTo(1);                                    // only Omar's father has not answered
        assertThat(count(s.omarDad(), NotificationKind.FAMILY)).isEqualTo(2);
        assertThatThrownBy(() -> events.remind(user(s.otherTeacher()), id)).isInstanceOf(ForbiddenException.class);

        events.cancel(teacher, id, "Bus not available");
        assertThat(count(s.amalMum(), NotificationKind.FAMILY)).isEqualTo(2);
        assertThatThrownBy(() -> events.answer(user(s.omarDad()), id, s.omar().getId(), "APPROVED", "", true)).isInstanceOf(ConflictException.class);
    }

    private FamilyDtos.EventRequest trip(LocalDate day, LocalDate deadline, List<UUID> sections) {
        return new FamilyDtos.EventRequest("Science museum trip", "Bring a packed lunch.", "National Museum", day, LocalTime.of(8, 30), LocalTime.of(13, 0),
                BigDecimal.valueOf(25), deadline, sections);
    }

    // =============================================================== 4. early pick-up

    @Test
    void aParentRequestsAnEarlyPickupAndTheSchoolApprovesIt() {
        Scene s = scene();
        LocalDate day = nextSchoolDay();
        var mum = user(s.amalMum());
        assertThatThrownBy(() -> early.request(mum, new FamilyDtos.EarlyPickupRequest(s.amal().getId(), day, LocalTime.of(18, 0), null, "Doctor")))
                .isInstanceOf(BadRequestException.class);                                         // after school
        var omarsDriver = pickup.addCollector(user(s.omarDad()), s.omar().getId(), new PickupDtos.CollectorRequest("Yusuf Driver", "DRIVER", "", "QID-12345678"));
        assertThatThrownBy(() -> early.request(mum, new FamilyDtos.EarlyPickupRequest(s.amal().getId(), day, LocalTime.of(11, 0), omarsDriver.id(), "Doctor")))
                .isInstanceOf(BadRequestException.class);                                         // not on Amal's list
        assertThatThrownBy(() -> early.request(user(s.omarDad()), new FamilyDtos.EarlyPickupRequest(s.amal().getId(), day, LocalTime.of(11, 0), null, "Doctor")))
                .isInstanceOf(NotFoundException.class);

        var req = early.request(mum, new FamilyDtos.EarlyPickupRequest(s.amal().getId(), day, LocalTime.of(11, 0), null, "Dentist appointment"));
        assertThat(req.status()).isEqualTo("PENDING");
        assertThat(req.collector()).isEqualTo("Maha Al-Sulaiti");
        assertThat(count(s.assistant(), NotificationKind.COLLECTION)).isEqualTo(1);
        assertThat(count(s.clerk(), NotificationKind.COLLECTION)).isEqualTo(1);
        assertThatThrownBy(() -> early.request(mum, new FamilyDtos.EarlyPickupRequest(s.amal().getId(), day, LocalTime.of(12, 0), null, "Again")))
                .isInstanceOf(ConflictException.class);                                           // one per child per day

        assertThatThrownBy(() -> early.decide(user(s.w().teacher()), req.id(), true, null)).isInstanceOf(ForbiddenException.class);
        assertThat(early.board(user(s.assistant())).pending()).extracting(FamilyDtos.EarlyPickupView::id).containsExactly(req.id());
        var approved = early.decide(user(s.assistant()), req.id(), true, null);
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.decidedBy()).isEqualTo("Asma Assistant");
        assertThat(count(s.amalMum(), NotificationKind.COLLECTION)).isEqualTo(1);
        assertThat(count(s.w().teacher(), NotificationKind.COLLECTION)).isEqualTo(1);           // the class teacher gets the child ready
        assertThat(count(s.otherTeacher(), NotificationKind.COLLECTION)).isZero();
        assertThat(early.board(user(s.w().teacher())).upcoming()).extracting(FamilyDtos.EarlyPickupView::id).containsExactly(req.id());
        assertThat(early.board(user(s.otherTeacher())).upcoming()).isEmpty();
        assertThatThrownBy(() -> early.decide(user(s.clerk()), req.id(), false, "late")).isInstanceOf(ConflictException.class);

        // Omar's father asks with the driver; the clerk declines, with a reason
        var other = early.request(user(s.omarDad()), new FamilyDtos.EarlyPickupRequest(s.omar().getId(), day, LocalTime.of(10, 0), omarsDriver.id(), "Family trip"));
        assertThat(other.collector()).isEqualTo("Yusuf Driver");
        assertThatThrownBy(() -> early.decide(user(s.clerk()), other.id(), false, " ")).isInstanceOf(BadRequestException.class);
        assertThat(early.decide(user(s.clerk()), other.id(), false, "Exam that morning").status()).isEqualTo("DECLINED");

        // the parent can cancel an approved request; the teachers are told
        assertThat(early.cancel(mum, req.id()).status()).isEqualTo("CANCELLED");
        assertThat(count(s.w().teacher(), NotificationKind.COLLECTION)).isEqualTo(2);
        assertThat(early.mine(mum)).extracting(FamilyDtos.EarlyPickupView::status).containsExactly("CANCELLED");
    }

    // =============================================================== 5. parent-teacher meetings

    @Test
    void aParentBooksAFreeSlotWithTheirChildsTeacherAndASlotCanBeBookedOnce() {
        Scene s = scene();
        LocalDate day = academicAccess.today().plusDays(3);
        var teacher = user(s.w().teacher());
        assertThatThrownBy(() -> meetings.open(user(s.amalMum()), new FamilyDtos.SlotsRequest(day, LocalTime.of(9, 0), LocalTime.of(10, 0), 20, "IN_PERSON", "Room 4")))
                .isInstanceOf(ForbiddenException.class);
        var opened = meetings.open(teacher, new FamilyDtos.SlotsRequest(day, LocalTime.of(9, 0), LocalTime.of(10, 0), 20, "IN_PERSON", "Room 4"));
        assertThat(opened.free()).isEqualTo(3);
        // opening the same hour again adds nothing twice
        assertThat(meetings.open(teacher, new FamilyDtos.SlotsRequest(day, LocalTime.of(9, 0), LocalTime.of(10, 0), 20, "IN_PERSON", "Room 4")).free()).isEqualTo(3);
        meetings.open(user(s.otherTeacher()), new FamilyDtos.SlotsRequest(day, LocalTime.of(9, 0), LocalTime.of(9, 30), 30, "ONLINE", "https://meet.example/x"));

        var view = meetings.parent(user(s.amalMum()));
        assertThat(view.children()).singleElement().satisfies(c -> assertThat(c.teachers()).singleElement().satisfies(t -> {
            assertThat(t.teacher()).isEqualTo("Tariq Teacher");                                   // only Amal's own teachers
            assertThat(t.free()).hasSize(3);
        }));
        UUID first = opened.slots().get(0).id();
        UUID othersSlot = meetings.teacher(user(s.otherTeacher())).slots().get(0).id();
        assertThatThrownBy(() -> meetings.book(user(s.amalMum()), othersSlot, s.amal().getId(), "Progress")).isInstanceOf(NotFoundException.class);

        var booking = meetings.book(user(s.amalMum()), first, s.amal().getId(), "Her maths progress");
        assertThat(booking.status()).isEqualTo("BOOKED");
        assertThat(count(s.w().teacher(), NotificationKind.FAMILY)).isEqualTo(1);
        assertThatThrownBy(() -> meetings.book(user(s.omarDad()), first, s.omar().getId(), "Behaviour")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> meetings.book(user(s.amalMum()), opened.slots().get(1).id(), s.amal().getId(), "Again")).isInstanceOf(ConflictException.class);
        assertThat(meetings.parent(user(s.omarDad())).children().get(0).teachers().get(0).free()).hasSize(2);   // the booked slot is gone
        var mine = meetings.teacher(teacher);
        assertThat(mine.booked()).isEqualTo(1);
        assertThat(mine.slots().get(0).student()).isEqualTo("Amal Al-Sulaiti");

        // the teacher cancels a booked slot only with a reason; the parent is told and can book again
        assertThatThrownBy(() -> meetings.removeSlot(teacher, first, "")).isInstanceOf(BadRequestException.class);
        meetings.removeSlot(teacher, first, "I am on a course that day");
        assertThat(count(s.amalMum(), NotificationKind.FAMILY)).isEqualTo(1);
        assertThat(meetings.parent(user(s.amalMum())).bookings()).singleElement().satisfies(b -> {
            assertThat(b.status()).isEqualTo("CANCELLED");
            assertThat(b.cancelReason()).isEqualTo("I am on a course that day");
        });
        var second = meetings.book(user(s.amalMum()), opened.slots().get(1).id(), s.amal().getId(), "Her maths progress");
        meetings.cancelByParent(user(s.amalMum()), second.id(), "Something came up");
        assertThat(count(s.w().teacher(), NotificationKind.FAMILY)).isEqualTo(3);              // booked, booked, cancelled
        assertThat(meetings.teacher(teacher).free()).isEqualTo(2);
    }
}
