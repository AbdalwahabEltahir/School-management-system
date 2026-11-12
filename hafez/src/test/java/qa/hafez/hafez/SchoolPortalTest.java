package qa.hafez.hafez;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import qa.hafez.hafez.academic.dto.AcademicDtos;
import qa.hafez.hafez.academic.dto.AcademicDtos.MarkInput;
import qa.hafez.hafez.academic.dto.AcademicDtos.NoticeRequest;
import qa.hafez.hafez.academic.model.AttendanceStatus;
import qa.hafez.hafez.academic.model.NoticeAudience;
import qa.hafez.hafez.academic.service.AcademicSetupService;
import qa.hafez.hafez.academic.service.AttendanceService;
import qa.hafez.hafez.academic.service.MarksService;
import qa.hafez.hafez.academic.service.MaterialService;
import qa.hafez.hafez.academic.service.NoticeService;
import qa.hafez.hafez.academic.service.PortalService;
import qa.hafez.hafez.audit.service.ActivityLogService;
import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.planning.service.PlanningService;
import qa.hafez.hafez.shared.exception.BadRequestException;
import qa.hafez.hafez.shared.exception.ConflictException;
import qa.hafez.hafez.shared.exception.ForbiddenException;
import qa.hafez.hafez.shared.exception.NotFoundException;
import qa.hafez.hafez.shared.model.NotificationKind;
import qa.hafez.hafez.shared.model.Role;

/**
 * The school portal's rules, on a real PostgreSQL: setup and clashes, attendance windows, the marks-to-results flow,
 * notices, materials, and who may see what (SMS BR-002 to BR-014). The clock is fixed to Tuesday 10 November 2026,
 * 09:00 in Qatar, so "today" is always a school day.
 */
@Import(SchoolPortalTest.FixedClock.class)
class SchoolPortalTest extends WorldTestSupport {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-11-10T06:00:00Z"), ZoneOffset.UTC);
        }
    }

    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);          // Tuesday
    static final LocalDate YESTERDAY = LocalDate.of(2026, 11, 9);       // Monday
    static final LocalDate FRIDAY = LocalDate.of(2026, 11, 6);

    @Autowired AcademicSetupService setup;
    @Autowired AttendanceService attendance;
    @Autowired MarksService marks;
    @Autowired qa.hafez.hafez.approvals.service.ApprovalService approvals;
    @Autowired NoticeService notices;
    @Autowired qa.hafez.hafez.notifications.service.NotificationService notificationService;
    @Autowired MaterialService materials;
    @Autowired PortalService portal;
    @Autowired qa.hafez.hafez.academic.service.ParentPortalService parentPortal;
    @Autowired PlanningService planning;
    @Autowired ActivityLogService activityLog;
    @Autowired qa.hafez.hafez.people.service.PeopleService peopleService;
    @Autowired qa.hafez.hafez.organisation.service.SettingsService settingsService;

    /** A school with section 7B (three students) and section 7C (one student), Mathematics taught by the teacher in 7B. */
    private record School(World w, UUID b, UUID c, Person s1, Person s2, Person s3, Person other) {}

    private Person student(World w, String name) {
        return person(w.org(), w.school(), name.toLowerCase().replace(' ', '.') + "@" + w.school().getDomain(), name, Role.STUDENT, null);
    }

    private School school() {
        World w = world();
        var coordinator = user(w.coordinator());
        for (int n = 1; n <= 3; n++) {
            setup.saveTerm(coordinator, n, "Term " + n, TODAY.plusDays(90L * (n - 1) - 30), TODAY.plusDays(90L * n - 31));
        }
        UUID b = setup.createSection(coordinator, 7, "B").id();
        UUID c = setup.createSection(coordinator, 7, "C").id();
        Person s1 = student(w, "Amal Student");
        Person s2 = student(w, "Bader Student");
        Person s3 = student(w, "Cara Student");
        Person other = student(w, "Dina Other");
        for (Person p : List.of(s1, s2, s3)) {
            setup.enrol(coordinator, b, p.getEmail());
        }
        setup.enrol(coordinator, c, other.getEmail());
        setup.assign(coordinator, b, w.maths().getId(), w.teacher().getId());
        return new School(w, b, c, s1, s2, s3, other);
    }

    // ------------------------------------------------------------------ setup and clashes

    @Test
    void aStudentIsInOneSectionAndEnrollingAgainMovesThem() {
        School s = school();
        setup.enrol(user(s.w().coordinator()), s.c(), s.s1().getEmail());

        assertThat(setup.roster(user(s.w().it()), s.b())).extracting(AcademicDtos.StudentView::name).doesNotContain("Amal Student");
        assertThat(setup.roster(user(s.w().it()), s.c())).extracting(AcademicDtos.StudentView::name).contains("Amal Student");
    }

    @Test
    void onlyAStudentCanBeEnrolledAndOnlyByAdmin() {
        School s = school();
        assertThatThrownBy(() -> setup.enrol(user(s.w().coordinator()), s.b(), s.w().teacher().getEmail())).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> setup.enrol(user(s.w().teacher()), s.b(), s.s1().getEmail())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> setup.createSection(user(s.w().teacher()), 8, "A")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> setup.createSection(user(s.w().coordinator()), 7, "B")).isInstanceOf(ConflictException.class).hasMessageContaining("already exists");
    }

    @Test
    void theTimetableRefusesASectionTeacherOrRoomBookedTwiceAndNamesTheClash() {
        School s = school();
        var it = user(s.w().coordinator());
        setup.assign(it, s.c(), s.w().maths().getId(), s.w().teacher().getId()); // the same teacher also teaches 7C
        setup.addLesson(it, s.b(), s.w().maths().getId(), 0, 2, "Room 12");

        assertThatThrownBy(() -> setup.addLesson(it, s.b(), s.w().maths().getId(), 0, 2, null))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Section 7B already has Mathematics in period 2 on Sunday");
        assertThatThrownBy(() -> setup.addLesson(it, s.c(), s.w().maths().getId(), 0, 2, "Room 99"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Tariq Teacher already teaches another section in period 2 on Sunday");
        // another teacher, same room
        Person t2 = person(s.w().org(), s.w().school(), "t2@" + s.w().school().getDomain(), "Second Teacher", Role.TEACHER, null);
        UUID english = subjects.save(newSubject(s.w(), "English")).getId();
        setup.assign(it, s.c(), english, t2.getId());
        assertThatThrownBy(() -> setup.addLesson(it, s.c(), english, 0, 2, "room 12"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Room room 12 is already booked in period 2 on Sunday");

        // a free slot works; weekends and unassigned subjects are refused
        assertThat(setup.addLesson(it, s.c(), english, 0, 3, "Room 12").teacher()).isEqualTo("Second Teacher");
        assertThatThrownBy(() -> setup.addLesson(it, s.b(), s.w().maths().getId(), 5, 1, null)).isInstanceOf(BadRequestException.class).hasMessageContaining("Sunday to Thursday");
        assertThatThrownBy(() -> setup.addLesson(it, s.b(), english, 1, 1, null)).isInstanceOf(BadRequestException.class).hasMessageContaining("Assign a teacher");
    }

    private qa.hafez.hafez.organisation.model.Subject newSubject(World w, String name) {
        var subject = new qa.hafez.hafez.organisation.model.Subject();
        subject.setOrgId(w.org().getId());
        subject.setSiteId(w.school().getId());
        subject.setName(name);
        return subject;
    }

    @Test
    void eachRoleSeesOnlyItsOwnTimetable() {
        School s = school();
        var it = user(s.w().coordinator());
        setup.addLesson(it, s.b(), s.w().maths().getId(), 1, 1, null); // Monday, today is Tuesday
        setup.addLesson(it, s.b(), s.w().maths().getId(), 2, 1, null); // Tuesday = today

        assertThat(setup.timetable(user(s.s1()), null)).hasSize(2);
        assertThat(setup.timetable(user(s.other()), null)).isEmpty(); // section 7C has no lessons
        assertThat(setup.timetable(user(s.w().teacher()), null)).hasSize(2);
        assertThat(portal.studentDashboard(user(s.s1())).today()).hasSize(1);
        assertThat(portal.teacherToday(user(s.w().teacher())).lessons()).hasSize(1);
        assertThatThrownBy(() -> setup.timetable(user(s.w().it()), null)).isInstanceOf(BadRequestException.class); // staff must choose a section
        assertThatThrownBy(() -> setup.timetable(user(s.w().leader()), null)).isInstanceOf(ForbiddenException.class);
    }

    // ------------------------------------------------------------------ attendance

    @Test
    void attendanceDefaultsToPresentAndTheTeacherRecordsTodayOnly() {
        School s = school();
        var teacher = user(s.w().teacher());

        assertThat(attendance.roster(teacher, s.b(), TODAY)).extracting(AcademicDtos.RosterRow::status).containsOnly(AttendanceStatus.PRESENT);
        int saved = attendance.record(teacher, s.b(), TODAY, Map.of(s.s2().getId(), AttendanceStatus.ABSENT), null);

        assertThat(saved).isEqualTo(3); // the first time everyone is saved, absentees as marked, the rest present
        assertThat(attendance.roster(teacher, s.b(), TODAY)).filteredOn(r -> r.name().equals("Bader Student"))
                .extracting(AcademicDtos.RosterRow::status).containsExactly(AttendanceStatus.ABSENT);
        assertThat(attendance.mine(user(s.s2())).absent()).isEqualTo(1);
        assertThat(attendance.mine(user(s.s1())).present()).isEqualTo(1);
        // the teacher can correct the same day
        assertThat(attendance.record(teacher, s.b(), TODAY, Map.of(s.s2().getId(), AttendanceStatus.LEAVE), null)).isEqualTo(1);
        assertThat(attendance.mine(user(s.s2())).leave()).isEqualTo(1);
    }

    @Test
    void afterTheDayOnlySchoolItMayChangeAttendanceAndMustGiveAReason() {
        School s = school();
        var teacher = user(s.w().teacher());
        var it = user(s.w().it());

        assertThatThrownBy(() -> attendance.record(teacher, s.b(), YESTERDAY, Map.of(), null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> attendance.record(it, s.b(), YESTERDAY, Map.of(s.s1().getId(), AttendanceStatus.ABSENT), " "))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("reason");
        attendance.record(it, s.b(), YESTERDAY, Map.of(s.s1().getId(), AttendanceStatus.ABSENT), "Doctor's note");
        attendance.record(it, s.b(), YESTERDAY, Map.of(s.s1().getId(), AttendanceStatus.LEAVE), "Changed to approved leave");

        // the correction is in the tamper-evident log with the old and the new value (FR-049)
        assertThat(activityLog.list(user(s.w().leader()), "ATTENDANCE_CHANGED", 0, 20))
                .anySatisfy(e -> assertThat(e.getBeforeState()).contains("ABSENT"));
    }

    @Test
    void attendanceRefusesWeekendsFutureDaysAndStudentsOfAnotherSection() {
        School s = school();
        var teacher = user(s.w().teacher());

        assertThatThrownBy(() -> attendance.record(teacher, s.b(), FRIDAY, Map.of(), null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> attendance.record(teacher, s.b(), TODAY.plusDays(1), Map.of(), null)).isInstanceOf(BadRequestException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> attendance.record(teacher, s.b(), TODAY, Map.of(s.other().getId(), AttendanceStatus.ABSENT), null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("not in section");
        // a teacher who does not teach the section, and a student, cannot even read the roster
        assertThatThrownBy(() -> attendance.roster(teacher, s.c(), TODAY)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> attendance.roster(user(s.s1()), s.b(), TODAY)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> attendance.mine(teacher)).isInstanceOf(ForbiddenException.class);
    }

    // ------------------------------------------------------------------ marks and results

    private UUID assessment(School s, String name, int term, String max) {
        return marks.create(user(s.w().teacher()), s.b(), s.w().maths().getId(), term, name, new BigDecimal(max)).id();
    }

    private List<MarkInput> allMarks(School s, String a, String b, String c) {
        return List.of(new MarkInput(s.s1().getId(), new BigDecimal(a), false), new MarkInput(s.s2().getId(), new BigDecimal(b), false),
                new MarkInput(s.s3().getId(), c == null ? null : new BigDecimal(c), c == null));
    }

    @Test
    void anInvalidRowSavesNothingAndEveryBadRowIsListed() {
        School s = school();
        UUID quiz = assessment(s, "Quiz 1", 1, "40");
        var teacher = user(s.w().teacher());

        assertThatThrownBy(() -> marks.enter(teacher, quiz, List.of(
                new MarkInput(s.s1().getId(), new BigDecimal("41"), false),
                new MarkInput(s.s2().getId(), new BigDecimal("-1"), false),
                new MarkInput(s.s3().getId(), new BigDecimal("30"), false))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Nothing was saved").hasMessageContaining("Amal Student").hasMessageContaining("Bader Student")
                .hasMessageNotContaining("Cara Student");
        assertThat(marks.sheet(teacher, quiz)).extracting(AcademicDtos.MarkRow::value).containsOnlyNulls(); // the valid row was not saved either
        assertThatThrownBy(() -> marks.enter(teacher, quiz, List.of(new MarkInput(s.s1().getId(), null, false)))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> marks.enter(teacher, quiz, List.of(new MarkInput(s.s1().getId(), new BigDecimal("5"), true)))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void marksCanBeSubmittedOnlyWhenCompleteThenAreLockedUntilSchoolItUnlocks() {
        School s = school();
        UUID quiz = assessment(s, "Quiz 1", 1, "40");
        var teacher = user(s.w().teacher());
        var it = user(s.w().it());

        marks.enter(teacher, quiz, List.of(new MarkInput(s.s1().getId(), new BigDecimal("35"), false)));
        assertThatThrownBy(() -> marks.submit(teacher, quiz)).isInstanceOf(ConflictException.class).hasMessageContaining("2 student(s) have no mark");
        marks.enter(teacher, quiz, allMarks(s, "35", "28.5", null)); // the third student was absent
        marks.submit(teacher, quiz);

        assertThatThrownBy(() -> marks.enter(teacher, quiz, allMarks(s, "36", "28.5", null))).isInstanceOf(ConflictException.class).hasMessageContaining("locked");
        assertThatThrownBy(() -> marks.unlock(it, quiz, "I want to")).isInstanceOf(ForbiddenException.class); // not School IT's job
        assertThatThrownBy(() -> marks.unlock(teacher, quiz, " ")).isInstanceOf(BadRequestException.class);

        marks.unlock(teacher, quiz, "Wrong mark for Amal");
        assertThat(marks.enter(teacher, quiz, allMarks(s, "36", "28.5", null))).isEqualTo(1);
        assertThat(marks.list(teacher, null)).singleElement().satisfies(a -> assertThat(a.submitted()).isFalse());
    }

    @Test
    void studentsSeeNothingUntilPublishedThenOnlyTheirOwnMarks() {
        School s = school();
        UUID quiz = assessment(s, "Quiz 1", 1, "40");
        UUID exam = assessment(s, "Exam", 2, "100");
        var teacher = user(s.w().teacher());
        var it = user(s.w().it());
        marks.enter(teacher, quiz, allMarks(s, "35", "28.5", "30"));
        marks.submit(teacher, quiz);

        assertThat(marks.myResults(user(s.s1()))).isEmpty();
        assertThatThrownBy(() -> marks.publish(teacher, s.b(), 3)).isInstanceOf(ConflictException.class).hasMessageContaining("no assessments");
        marks.enter(teacher, exam, allMarks(s, "80", "70", "60"));
        assertThatThrownBy(() -> marks.publish(teacher, s.b(), 2)).isInstanceOf(ConflictException.class).hasMessageContaining("not submitted");

        long told = count(s.s1(), NotificationKind.SYSTEM);
        assertThatThrownBy(() -> marks.publish(it, s.b(), 1)).isInstanceOf(ForbiddenException.class); // School IT does not publish
        marks.publish(teacher, s.b(), 1);

        // four-eyes: asking is not enough, the students still see nothing, and the teacher cannot approve their own request
        assertThat(marks.myResults(user(s.s1()))).isEmpty();
        var request = approvals.list(user(s.w().principal())).stream().filter(r -> r.getAction().equals("MARKS_PUBLISH")).findFirst().orElseThrow();
        assertThatThrownBy(() -> approvals.approve(teacher, request.getId())).isInstanceOf(ForbiddenException.class);
        approvals.approve(user(s.w().principal()), request.getId());

        assertThat(count(s.s1(), NotificationKind.SYSTEM)).isEqualTo(told + 1); // "your results are published"
        assertThat(marks.myResults(user(s.s1()))).singleElement().satisfies(r -> {
            assertThat(r.assessment()).isEqualTo("Quiz 1");
            assertThat(r.value()).isEqualByComparingTo("35");
            assertThat(r.maxMark()).isEqualByComparingTo("40");
        });
        assertThat(marks.myResults(user(s.s2())).get(0).value()).isEqualByComparingTo("28.5"); // each student sees their own
        assertThat(marks.myResults(user(s.other()))).isEmpty(); // another section sees nothing
        assertThatThrownBy(() -> marks.publish(teacher, s.b(), 1)).isInstanceOf(ConflictException.class).hasMessageContaining("already published");
        assertThatThrownBy(() -> marks.unlock(teacher, quiz, "x")).isInstanceOf(ConflictException.class).hasMessageContaining("Unpublish");
        assertThatThrownBy(() -> marks.unpublish(teacher, s.b(), 1, "")).isInstanceOf(BadRequestException.class);
        marks.unpublish(teacher, s.b(), 1, "A mark was wrong");
        assertThat(marks.myResults(user(s.s1()))).isEmpty();
    }

    @Test
    void thisWeeksSubmissionsAreVisibleToLeadershipAndTheMatrixShowsEveryAssessment() {
        School s = school();
        UUID quiz = assessment(s, "Quiz 1", 1, "40");
        var teacher = user(s.w().teacher());
        marks.enter(teacher, quiz, allMarks(s, "30", "20", "10"));

        var before = marks.weekly(user(s.w().principal()), null);
        assertThat(before).anySatisfy(r -> { assertThat(r.assessments()).isEqualTo(1); assertThat(r.status()).isEqualTo("IN_PROGRESS"); });
        marks.submit(teacher, quiz);
        assertThat(marks.weekly(user(s.w().principal()), null)).anySatisfy(r -> assertThat(r.status()).isEqualTo("DONE"));
        assertThat(marks.weekly(user(s.w().leader()), null)).isNotNull();           // the network can read it
        assertThatThrownBy(() -> marks.weekly(teacher, null)).isInstanceOf(ForbiddenException.class);

        var matrix = marks.matrix(user(s.w().principal()), s.b());
        assertThat(matrix.columns()).hasSize(1);
        assertThat(matrix.rows()).hasSize(3);
        assertThat(matrix.rows().get(0).stage()).isEqualTo("SIJIL");
        assertThat(matrix.rows().get(0).averagePercent()).isNotNull();
        assertThatThrownBy(() -> marks.matrix(user(s.w().leader()), s.b())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void marksAreEnteredOnlyByTheTeacherOfThatSubjectForThatSection() {
        School s = school();
        UUID quiz = assessment(s, "Quiz 1", 1, "40");
        Person other = person(s.w().org(), s.w().school(), "o@" + s.w().school().getDomain(), "Other Teacher", Role.TEACHER, null);

        assertThatThrownBy(() -> marks.enter(user(other), quiz, allMarks(s, "1", "2", "3"))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> marks.sheet(user(other), quiz)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> marks.enter(user(s.w().it()), quiz, allMarks(s, "1", "2", "3"))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> marks.list(user(s.s1()), null)).isInstanceOf(ForbiddenException.class);
        assertThat(marks.list(user(other), null)).isEmpty();
        assertThatThrownBy(() -> marks.create(user(other), s.b(), s.w().maths().getId(), 1, "Not mine", BigDecimal.TEN)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> marks.create(user(s.w().it()), s.b(), s.w().maths().getId(), 1, "IT", BigDecimal.TEN)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> marks.create(user(s.w().teacher()), s.b(), s.w().maths().getId(), 4, "Bad term", BigDecimal.TEN)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void onlyTheCoordinatorCreatesTermsClassesAndSectionsAndTeachersSetTheAssessments() {
        School s = school();
        var it = user(s.w().it());
        var teacher = user(s.w().teacher());
        var coordinator = user(s.w().coordinator());

        assertThatThrownBy(() -> setup.createSection(it, 8, "A")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> setup.saveTerm(it, 1, "Term 1", TODAY, TODAY.plusDays(30))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> setup.createSection(teacher, 8, "A")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> setup.saveTerm(teacher, 1, "Term 1", TODAY, TODAY.plusDays(30))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> setup.assign(it, s.b(), s.w().maths().getId(), s.w().teacher().getId())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> setup.saveTerm(coordinator, 1, "Backwards", TODAY, TODAY.minusDays(1))).isInstanceOf(BadRequestException.class);
        assertThat(setup.createSection(coordinator, 8, "A").grade()).isEqualTo(8);
        assertThat(setup.terms(teacher)).hasSize(3);

        // the teacher sets the assessment, but only for a term the coordinator has created
        assertThat(marks.create(teacher, s.b(), s.w().maths().getId(), 3, "Final", BigDecimal.TEN).term()).isEqualTo(3);
    }

    @Test
    void subjectsAndDocumentTypesBelongToTheCoordinatorNotSchoolIt() {
        World w = world();
        assertThatThrownBy(() -> settingsService.saveSubject(user(w.it()), null, "Physics", null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> settingsService.saveDocType(user(w.it()), "Lab report", qa.hafez.hafez.shared.model.Sensitivity.INTERNAL)).isInstanceOf(ForbiddenException.class);
        assertThat(settingsService.saveSubject(user(w.coordinator()), null, "Physics", null).getName()).isEqualTo("Physics");
        assertThat(settingsService.saveDocType(user(w.coordinator()), "Lab report", qa.hafez.hafez.shared.model.Sensitivity.INTERNAL).getName()).isEqualTo("Lab report");
    }

    @Test
    void schoolItAddsAPersonWhoCanThenSignIn() {
        World w = world();
        String domain = w.school().getDomain();
        var p = peopleService.add(user(w.it()), "New.Teacher@" + domain, "New Teacher", Role.TEACHER, null);
        assertThat(p.getEmail()).isEqualTo("new.teacher@" + domain);
        assertThat(p.canSignIn()).isTrue();
        assertThatThrownBy(() -> peopleService.add(user(w.it()), "new.teacher@" + domain, "Again", Role.TEACHER, null)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> peopleService.add(user(w.it()), "someone@unknown-domain.test", "X", Role.TEACHER, null)).isInstanceOf(BadRequestException.class).hasMessageContaining("domain");
        assertThat(peopleService.add(user(w.it()), "kid@my-own-mail.test", "Kid", Role.STUDENT, null).canSignIn()).isTrue(); // a student may use any e-mail
        assertThatThrownBy(() -> peopleService.add(user(w.it()), "boss@" + domain, "Boss", Role.LEADERSHIP, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> peopleService.add(user(w.teacher()), "x@" + domain, "X", Role.TEACHER, null)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aParentSeesOnlyTheirOwnChildReadOnly() {
        School s = school();
        var it = user(s.w().it());
        assertThatThrownBy(() -> peopleService.add(it, "mum@my-own-mail.test", "Mum", Role.PARENT, null, null)).isInstanceOf(BadRequestException.class);
        var parent = peopleService.add(it, "mum@my-own-mail.test", "Mum", Role.PARENT, null, s.s1().getId()); // any e-mail
        var me = user(parent);

        assertThat(parentPortal.children(me)).singleElement().satisfies(c -> assertThat(c.id()).isEqualTo(s.s1().getId()));
        assertThat(parentPortal.child(me, s.s1().getId()).section()).isNotNull();
        assertThatThrownBy(() -> parentPortal.child(me, s.s2().getId())).isInstanceOf(NotFoundException.class); // not their child
        assertThatThrownBy(() -> marks.list(me, null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> setup.roster(me, s.b())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> parentPortal.children(user(s.s1()))).isInstanceOf(ForbiddenException.class); // students are not parents
    }

    @Test
    void anAssessmentNeedsATermThatTheCoordinatorCreated() {
        World w = world();
        var coordinator = user(w.coordinator());
        UUID b = setup.createSection(coordinator, 7, "B").id();
        setup.assign(coordinator, b, w.maths().getId(), w.teacher().getId());
        assertThatThrownBy(() -> marks.create(user(w.teacher()), b, w.maths().getId(), 1, "Quiz", BigDecimal.TEN))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Term 1 has not been created");
        setup.saveTerm(coordinator, 1, "Term 1", TODAY, TODAY.plusDays(30));
        assertThat(marks.create(user(w.teacher()), b, w.maths().getId(), 1, "Quiz", BigDecimal.TEN).name()).isEqualTo("Quiz");
    }

    // ------------------------------------------------------------------ notices and materials

    private NoticeRequest notice(String titleAr, Instant starts, Instant ends, NoticeAudience audience) {
        return new NoticeRequest("Holiday", titleAr, "School is closed", titleAr == null ? null : "المدرسة مغلقة", starts, ends, audience);
    }

    @Test
    void aNoticeShowsOnlyBetweenItsDatesToItsAudienceAndNeedsItsArabicText() {
        School s = school();
        var it = user(s.w().it());
        Instant now = Instant.parse("2026-11-10T06:00:00Z");

        assertThatThrownBy(() -> notices.create(it, notice("عطلة", now, now.minusSeconds(60), NoticeAudience.ALL))).isInstanceOf(BadRequestException.class).hasMessageContaining("after its start");
        assertThatThrownBy(() -> notices.create(it, notice(null, now.minusSeconds(60), now.plusSeconds(3600), NoticeAudience.ALL))).isInstanceOf(BadRequestException.class).hasMessageContaining("Arabic");
        assertThatThrownBy(() -> notices.create(user(s.w().teacher()), notice("عطلة", now, now.plusSeconds(60), NoticeAudience.ALL))).isInstanceOf(ForbiddenException.class);

        notices.create(it, notice("عطلة", now.minusSeconds(3600), now.plusSeconds(3600), NoticeAudience.STUDENTS)); // showing now, students
        notices.create(it, notice("عطلة", now.minusSeconds(3600), now.plusSeconds(3600), NoticeAudience.STAFF));    // showing now, staff
        notices.create(it, notice("عطلة", now.plusSeconds(3600), now.plusSeconds(7200), NoticeAudience.ALL));        // not yet
        notices.create(it, notice("عطلة", now.minusSeconds(7200), now.minusSeconds(3600), NoticeAudience.ALL));      // over

        assertThat(notices.current(user(s.s1()))).singleElement().satisfies(n -> assertThat(n.audience()).isEqualTo(NoticeAudience.STUDENTS));
        assertThat(notices.current(user(s.w().teacher()))).singleElement().satisfies(n -> assertThat(n.audience()).isEqualTo(NoticeAudience.STAFF));
        assertThat(notices.manage(it)).hasSize(4);
        assertThatThrownBy(() -> notices.manage(user(s.w().teacher()))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aStaffNoticeRingsTheBellOfStaffButNotOfStudentsOrItsAuthor() {
        School s = school();
        var it = user(s.w().it());
        Instant now = Instant.parse("2026-11-10T06:00:00Z");

        notices.create(it, notice("عطلة", now.minusSeconds(60), now.plusSeconds(3600), NoticeAudience.STAFF));

        assertThat(notificationService.mine(user(s.w().teacher()))).anyMatch(n -> (n.getTitle().contains("Holiday") || n.getTitle().contains("عطلة")));
        assertThat(notificationService.mine(user(s.s1()))).noneMatch(n -> (n.getTitle().contains("Holiday") || n.getTitle().contains("عطلة")));
        assertThat(notificationService.mine(it)).noneMatch(n -> (n.getTitle().contains("Holiday") || n.getTitle().contains("عطلة")));
    }

    @Test
    void materialsAreHttpsLinksSharedByTheTeacherOfThatSubjectAndSeenByThatSectionOnly() {
        School s = school();
        var teacher = user(s.w().teacher());
        UUID maths = s.w().maths().getId();

        assertThatThrownBy(() -> materials.add(teacher, s.b(), maths, "Notes", "http://insecure.example/x")).isInstanceOf(BadRequestException.class).hasMessageContaining("https");
        assertThatThrownBy(() -> materials.add(teacher, s.b(), maths, "Notes", "javascript:alert(1)")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> materials.add(teacher, s.c(), maths, "Notes", "https://drive.google.com/x")).isInstanceOf(ForbiddenException.class); // 7C is not theirs
        var added = materials.add(teacher, s.b(), maths, "Fractions notes", "https://drive.google.com/file/d/abc");

        assertThat(materials.list(user(s.s1()), null)).extracting(AcademicDtos.MaterialView::title).containsExactly("Fractions notes");
        assertThat(materials.list(user(s.other()), null)).isEmpty();
        assertThat(portal.studentDashboard(user(s.s1())).materials()).hasSize(1);
        assertThatThrownBy(() -> materials.remove(user(s.s1()), added.id())).isInstanceOf(ForbiddenException.class);
        materials.remove(teacher, added.id());
        assertThat(materials.list(user(s.s1()), null)).isEmpty();
    }

    // ------------------------------------------------------------------ who can see what

    @Test
    void anotherSchoolNeverSeesThisSchoolsSections() {
        School s = school();
        World other = world(); // a different organisation and school

        assertThatThrownBy(() -> setup.roster(user(other.it()), s.b())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> setup.enrol(user(other.coordinator()), s.b(), s.s1().getEmail())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> attendance.roster(user(other.it()), s.b(), TODAY)).isInstanceOf(NotFoundException.class);
        assertThat(setup.sections(user(other.it()))).isEmpty();
        assertThatThrownBy(() -> marks.create(user(other.it()), s.b(), other.maths().getId(), 1, "X", BigDecimal.TEN)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aStudentSeesOnlyTheirOwnPortalAndNoStaffScreens() {
        School s = school();
        var student = user(s.s1());

        assertThat(portal.studentDashboard(student).section().name()).isEqualTo("B");
        assertThat(setup.sections(student)).singleElement().satisfies(x -> assertThat(x.name()).isEqualTo("B"));
        assertThatThrownBy(() -> portal.teacherToday(student)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> setup.roster(student, s.b())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> setup.assignmentsOf(student, s.b())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> planning.list(student)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> activityLog.list(student, null, 0, 10)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> portal.studentDashboard(user(s.w().teacher()))).isInstanceOf(ForbiddenException.class);
        // a student who is not enrolled yet gets a clear "not found", not someone else's section
        Person unplaced = student(s.w(), "Eli Unplaced");
        assertThatThrownBy(() -> portal.studentDashboard(user(unplaced))).isInstanceOf(NotFoundException.class);
    }
}
