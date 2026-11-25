package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import qa.sijill.sijill.academic.model.Enrolment;
import qa.sijill.sijill.academic.model.Section;
import qa.sijill.sijill.academic.model.TeachingAssignment;
import qa.sijill.sijill.academic.repository.EnrolmentRepository;
import qa.sijill.sijill.academic.repository.SectionRepository;
import qa.sijill.sijill.academic.repository.TeachingAssignmentRepository;
import qa.sijill.sijill.academic.service.AcademicSetupService;
import qa.sijill.sijill.network.SchoolAdminService;
import qa.sijill.sijill.organisation.model.Subject;
import qa.sijill.sijill.organisation.service.SettingsService;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;

/** Sessions on terms, subject details, and the School IT tracking reads. */
class SchoolAdminTest extends WorldTestSupport {

    @Autowired SchoolAdminService admin;
    @Autowired AcademicSetupService setup;
    @Autowired SettingsService settingsService;
    @Autowired SectionRepository sections;
    @Autowired EnrolmentRepository enrolments;
    @Autowired TeachingAssignmentRepository assignments;
    @Autowired JdbcTemplate jdbc;

    private record Scene(World w, Section section, Person student) {}

    private Scene scene() {
        World w = world();
        Section section = new Section();
        section.setOrgId(w.org().getId());
        section.setSiteId(w.school().getId());
        section.setGrade(6);
        section.setName("B");
        sections.save(section);
        Person student = person(w.org(), w.school(), "kid-" + UUID.randomUUID() + "@" + w.school().getDomain(), "Lina Student", Role.STUDENT, null);
        Enrolment e = new Enrolment();
        e.setOrgId(w.org().getId());
        e.setSiteId(w.school().getId());
        e.setSectionId(section.getId());
        e.setStudentId(student.getId());
        enrolments.save(e);
        TeachingAssignment ta = new TeachingAssignment();
        ta.setOrgId(w.org().getId());
        ta.setSiteId(w.school().getId());
        ta.setSectionId(section.getId());
        ta.setSubjectId(w.maths().getId());
        ta.setTeacherId(w.teacher().getId());
        assignments.save(ta);
        return new Scene(w, section, student);
    }

    @Test
    void termsLiveInSessionsAndTheCurrentOneIsTheOneCoveringToday() {
        World w = world();
        var it = user(w.it());
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Qatar"));
        setup.saveTerm(it, "2019-2020", 1, "Old term 1", LocalDate.of(2019, 9, 1), LocalDate.of(2019, 12, 1));
        setup.saveTerm(it, "2019-2020", 2, "Old term 2", LocalDate.of(2020, 1, 5), LocalDate.of(2020, 3, 30));
        setup.saveTerm(it, "2099-2100", 1, "Future term 1", LocalDate.of(2099, 9, 1), LocalDate.of(2099, 12, 1));
        setup.saveTerm(it, "2019-2020", 1, "Renamed", LocalDate.of(2019, 9, 2), LocalDate.of(2019, 12, 1));           // editing replaces, it does not duplicate

        var sessions = setup.sessions(it);
        assertThat(sessions.all()).containsExactly("2099-2100", "2019-2020");
        assertThat(setup.terms(it, "2019-2020")).extracting(t -> t.name()).containsExactly("Renamed", "Old term 2");
        assertThat(setup.terms(it, "2099-2100")).hasSize(1);
        assertThat(sessions.current()).isEqualTo("2099-2100");                                                           // nothing covers today: the newest session
        setup.saveTerm(it, "2026-2027", 2, "Current term", today.minusDays(10), today.plusDays(10));
        assertThat(setup.sessions(it).current()).isEqualTo("2026-2027");
        assertThat(setup.terms(it)).extracting(t -> t.name()).containsExactly("Current term");
        assertThatThrownBy(() -> setup.saveTerm(user(w.teacher()), "2019-2020", 1, "No", LocalDate.of(2019, 9, 1), LocalDate.of(2019, 10, 1))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aSubjectHasACodeGradesAndPeriodsAWeek() {
        World w = world();
        Subject s = settingsService.saveSubject(user(w.it()), null, "Physics", null, "phy", 6, 12, 4);
        assertThat(s.getCode()).isEqualTo("PHY");
        assertThat(s.getGradeFrom()).isEqualTo(6);
        assertThat(s.getMaxPeriods()).isEqualTo(4);
        assertThatThrownBy(() -> settingsService.saveSubject(user(w.it()), s.getId(), null, null, null, 9, 3, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> settingsService.saveSubject(user(w.it()), null, "X", null, null, 0, null, null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void enrolmentsAndTeacherAssignmentsAreListedForTheOwnSchoolOnly() {
        Scene s = scene();
        var it = user(s.w().it());
        assertThat(admin.enrolments(it)).extracting(r -> r.name()).containsExactly("Lina Student");
        assertThat(admin.enrolments(it).get(0).section()).isEqualTo("B");
        var rows = admin.assignments(it, s.section().getId());
        assertThat(rows).extracting(r -> r.subject()).contains("Mathematics");
        assertThat(rows.stream().filter(r -> r.subject().equals("Mathematics")).findFirst().orElseThrow().teacher()).isEqualTo("Tariq Teacher");
        Scene other = scene();
        assertThatThrownBy(() -> admin.assignments(it, other.section().getId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> admin.enrolments(user(s.w().principal()))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void attendanceMarksAndResultsAreTrackedPerSection() {
        Scene s = scene();
        var it = user(s.w().it());
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Qatar"));
        jdbc.update("INSERT INTO attendance_records (id, created_at, org_id, site_id, section_id, student_id, attendance_date, status, recorded_by) VALUES (?, now(), ?, ?, ?, ?, ?, 'ABSENT', ?)",
                UUID.randomUUID(), s.w().org().getId(), s.w().school().getId(), s.section().getId(), s.student().getId(), today, s.w().teacher().getId());
        var att = admin.attendance(it, today.minusDays(5), today, null);
        assertThat(att.sections()).hasSize(1);
        assertThat(att.sections().get(0).absent()).isEqualTo(1);
        assertThat(att.sections().get(0).students()).isEqualTo(1);
        assertThat(att.daily()).hasSize(1);
        assertThat(admin.attendance(it, today.minusDays(5), today, 7).sections()).isEmpty();                               // another grade

        var before = admin.marks(it, 1, null);
        assertThat(before.rows()).hasSize(1);
        assertThat(before.rows().get(0).cells().get(0).status()).isEqualTo("NOT_ENTERED");
        assertThat(admin.results(it, 1, null).rows().get(0).status()).isEqualTo("INCOMPLETE");

        UUID assessment = UUID.randomUUID();
        jdbc.update("INSERT INTO assessments (id, created_at, org_id, site_id, section_id, subject_id, term, name, max_mark, submitted_at, week_start) VALUES (?, now(), ?, ?, ?, ?, 1, 'Midterm', 50, now(), current_date)",
                assessment, s.w().org().getId(), s.w().school().getId(), s.section().getId(), s.w().maths().getId());
        jdbc.update("INSERT INTO marks (id, created_at, org_id, site_id, assessment_id, student_id, mark_value, absent) VALUES (?, now(), ?, ?, ?, ?, 40, false)",
                UUID.randomUUID(), s.w().org().getId(), s.w().school().getId(), assessment, s.student().getId());
        var after = admin.marks(it, 1, null);
        assertThat(after.rows().get(0).cells().get(0).status()).isEqualTo("SUBMITTED");
        assertThat(after.ready()).isEqualTo(1);
        assertThat(after.assessments()).hasSize(1);
        assertThat(admin.results(it, 1, null).rows().get(0).status()).isEqualTo("READY");

        jdbc.update("INSERT INTO result_publications (id, created_at, org_id, site_id, section_id, term, published_at, published_by) VALUES (?, now(), ?, ?, ?, 1, now(), ?)",
                UUID.randomUUID(), s.w().org().getId(), s.w().school().getId(), s.section().getId(), s.w().it().getId());
        assertThat(admin.results(it, 1, null).rows().get(0).status()).isEqualTo("PUBLISHED");
        assertThat(admin.marks(it, 1, null).ready()).isZero();
    }
}
