package qa.hafez.hafez.academic.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import qa.hafez.hafez.academic.model.Enrolment;
import qa.hafez.hafez.academic.model.Section;
import qa.hafez.hafez.academic.repository.EnrolmentRepository;
import qa.hafez.hafez.academic.repository.SectionRepository;
import qa.hafez.hafez.academic.repository.TeachingAssignmentRepository;
import qa.hafez.hafez.organisation.model.Subject;
import qa.hafez.hafez.organisation.repository.SubjectRepository;
import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.people.repository.PersonRepository;
import qa.hafez.hafez.shared.exception.ForbiddenException;
import qa.hafez.hafez.shared.exception.NotFoundException;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.security.AccessPolicy;
import qa.hafez.hafez.shared.security.CurrentUser;
import qa.hafez.hafez.shared.util.SchoolCalendar;

/**
 * Who may touch which section, in one place (SEC-001, BR-012): the coordinator sets the school up, a teacher works on the
 * sections they are assigned to, a student sees only their own section. Another school's sections are "not found".
 */
@Component
@RequiredArgsConstructor
public class AcademicAccess {

    private final SectionRepository sections;
    private final EnrolmentRepository enrolments;
    private final TeachingAssignmentRepository assignments;
    private final PersonRepository people;
    private final SubjectRepository subjects;
    private final AccessPolicy policy;
    private final Clock clock;

    /** School IT acts as the school Admin: it unlocks marks and publishes results, it does not set the school up. */
    public void requireAdmin(CurrentUser user) {
        policy.requireRole(user, Role.SCHOOL_IT);
    }

    /** The coordinator creates terms, classes and sections, enrols students, assigns teachers and builds the timetable. */
    public void requireCoordinator(CurrentUser user) {
        policy.requireRole(user, Role.COORDINATOR);
    }

    /** Today's date in the school's time zone. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(SchoolCalendar.ZONE));
    }

    /** A section of the user's own school, else "not found". */
    public Section section(CurrentUser user, UUID sectionId) {
        return sections.findById(sectionId).filter(s -> s.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new NotFoundException("The section"));
    }

    public boolean teaches(CurrentUser user, UUID sectionId) {
        return user.isAny(Role.TEACHER, Role.COORDINATOR) && assignments.existsByTeacherIdAndSectionId(user.personId(), sectionId);
    }

    public boolean teaches(CurrentUser user, UUID sectionId, UUID subjectId) {
        return user.isAny(Role.TEACHER, Role.COORDINATOR)
                && assignments.existsByTeacherIdAndSectionIdAndSubjectId(user.personId(), sectionId, subjectId);
    }

    /** The section, if the user teaches it (any subject). Otherwise the user is refused. */
    public Section requireTeacherOf(CurrentUser user, UUID sectionId) {
        Section section = section(user, sectionId);
        if (!teaches(user, sectionId)) {
            throw new ForbiddenException("You do not teach this section.");
        }
        return section;
    }

    /** May this user see the section? Admin, principal and coordinator for their school, a teacher for theirs. */
    public Section requireView(CurrentUser user, UUID sectionId) {
        Section section = section(user, sectionId);
        boolean ok = user.isAny(Role.SCHOOL_IT, Role.PRINCIPAL, Role.COORDINATOR, Role.ATTENDANCE_OFFICER) || teaches(user, sectionId)
                || (user.is(Role.STUDENT) && enrolments.findByStudentId(user.personId())
                        .map(e -> e.getSectionId().equals(sectionId)).orElse(false));
        if (!ok) {
            throw new NotFoundException("The section");
        }
        return section;
    }

    /** A student's own section. */
    public Section studentSection(CurrentUser user) {
        policy.requireRole(user, Role.STUDENT);
        UUID sectionId = enrolments.findByStudentId(user.personId()).map(Enrolment::getSectionId)
                .orElseThrow(() -> new NotFoundException("Your section (you are not enrolled in a section yet)"));
        return sections.findById(sectionId).orElseThrow(() -> new NotFoundException("Your section"));
    }

    /** The students of a section, by name. */
    public List<Person> students(UUID sectionId) {
        List<UUID> ids = enrolments.findBySectionId(sectionId).stream().map(Enrolment::getStudentId).toList();
        return people.findAllById(ids).stream().sorted(Comparator.comparing(Person::getDisplayName)).toList();
    }

    public Map<UUID, String> personNames(Collection<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        people.findAllById(ids).forEach(p -> out.put(p.getId(), p.getDisplayName()));
        return out;
    }

    public Map<UUID, String> subjectNames(UUID siteId) {
        Map<UUID, String> out = new HashMap<>();
        for (Subject s : subjects.findBySiteIdOrderByName(siteId)) {
            out.put(s.getId(), s.getName());
        }
        return out;
    }

    public Map<UUID, Section> sectionsById(UUID siteId) {
        Map<UUID, Section> out = new HashMap<>();
        sections.findBySiteIdOrderByGradeAscNameAsc(siteId).forEach(s -> out.put(s.getId(), s));
        return out;
    }

    public static String label(Section s) {
        return s == null ? "" : s.getGrade() + s.getName();
    }
}
