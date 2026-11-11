package qa.sijill.sijill.academic.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.academic.dto.AcademicDtos.ClassOption;
import qa.sijill.sijill.academic.dto.AcademicDtos.PendingRequest;
import qa.sijill.sijill.academic.dto.AcademicDtos.StudentStatus;
import qa.sijill.sijill.academic.dto.AcademicDtos.UnenrolledStudent;
import qa.sijill.sijill.academic.model.Enrolment;
import qa.sijill.sijill.academic.model.Section;
import qa.sijill.sijill.academic.repository.EnrolmentRepository;
import qa.sijill.sijill.academic.repository.SectionRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.StudentCodes;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.people.service.ParentLinks;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.AccessPolicy;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * How a student gets a place in a class. A student, or a parent for their child, asks for a class; the coordinator
 * confirms it (or declines), and confirming enrols the student exactly as the coordinator's own enrol button does.
 * The coordinator can also enrol any student without a class straight from the list.
 */
@Service
@RequiredArgsConstructor
public class EnrolmentRequestService {

    private final JdbcTemplate jdbc;
    private final PersonRepository people;
    private final SectionRepository sections;
    private final EnrolmentRepository enrolments;
    private final AcademicAccess access;
    private final AcademicSetupService setup;
    private final ParentLinks links;
    private final AccessPolicy policy;
    private final AuditPublisher audit;

    /** The classes a student or parent may ask for. */
    @Transactional(readOnly = true)
    public List<ClassOption> classes(CurrentUser user) {
        if (!user.isAny(Role.STUDENT, Role.PARENT)) {
            throw new ForbiddenException("Class requests are for students and parents.");
        }
        return sections.findBySiteIdOrderByGradeAscNameAsc(user.siteId()).stream()
                .map(s -> new ClassOption(s.getId(), AcademicAccess.label(s))).toList();
    }

    /** A student's own ID, class and pending request: what the "waiting for a class" screen shows. */
    @Transactional
    public StudentStatus status(CurrentUser user) {
        policy.requireRole(user, Role.STUDENT);
        Person me = people.findById(user.personId()).orElseThrow(() -> new NotFoundException("Your account"));
        if (me.getStudentCode() == null) { // a person whose role was changed to Student later
            me.setStudentCode(StudentCodes.next());
            people.save(me);
        }
        Map<UUID, Section> byId = access.sectionsById(user.siteId());
        String section = enrolments.findByStudentId(me.getId()).map(e -> AcademicAccess.label(byId.get(e.getSectionId()))).orElse(null);
        return new StudentStatus(me.getStudentCode(), me.getDisplayName(), section, pendingLabel(me.getId()));
    }

    /** The class a student is waiting for, or null. */
    @Transactional(readOnly = true)
    public String pendingLabel(UUID studentId) {
        List<UUID> ids = jdbc.queryForList("SELECT section_id FROM enrolment_requests WHERE student_id = ? AND status = 'PENDING'", UUID.class, studentId);
        return ids.isEmpty() ? null : sections.findById(ids.get(0)).map(AcademicAccess::label).orElse(null);
    }

    @Transactional
    public void request(CurrentUser user, UUID studentId, UUID sectionId) {
        UUID target;
        if (user.is(Role.STUDENT)) {
            target = user.personId();
        } else if (user.is(Role.PARENT) && studentId != null && links.isLinked(user.personId(), studentId)) {
            target = studentId;
        } else if (user.is(Role.PARENT)) {
            throw new NotFoundException("The child");
        } else {
            throw new ForbiddenException("Class requests are for students and parents.");
        }
        Section section = sections.findById(sectionId).filter(s -> s.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new BadRequestException("Please choose a class."));
        if (enrolments.findByStudentId(target).isPresent()) {
            throw new ConflictException("This student is already in a class.");
        }
        if (pendingLabel(target) != null) {
            throw new ConflictException("A request for this student is already waiting for the coordinator.");
        }
        jdbc.update("INSERT INTO enrolment_requests (site_id, student_id, section_id, requested_by) VALUES (?, ?, ?, ?)",
                user.siteId(), target, section.getId(), user.personId());
        audit.log(user.orgId(), user.siteId(), ActivityActions.ENROLMENT_REQUESTED).by(user).on("Person", target)
                .change(null, AcademicAccess.label(section)).save();
    }

    /** The coordinator's queue. */
    @Transactional(readOnly = true)
    public List<PendingRequest> pending(CurrentUser user) {
        policy.requireRole(user, Role.COORDINATOR);
        var rows = jdbc.queryForList("SELECT id, student_id, section_id, requested_by FROM enrolment_requests WHERE site_id = ? AND status = 'PENDING' ORDER BY created_at", user.siteId());
        Map<UUID, Person> who = people.findAllById(rows.stream().flatMap(r -> java.util.stream.Stream.of((UUID) r.get("student_id"), (UUID) r.get("requested_by"))).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Person::getId, p -> p));
        Map<UUID, Section> byId = access.sectionsById(user.siteId());
        return rows.stream().map(r -> {
            Person student = who.get((UUID) r.get("student_id"));
            Person by = who.get((UUID) r.get("requested_by"));
            return new PendingRequest((UUID) r.get("id"), student == null ? "" : student.getDisplayName(), student == null ? "" : student.getStudentCode(),
                    AcademicAccess.label(byId.get((UUID) r.get("section_id"))), by == null ? "" : by.getDisplayName() + " (" + by.getRole().name().toLowerCase() + ")");
        }).toList();
    }

    @Transactional
    public void decide(CurrentUser user, UUID requestId, boolean approve) {
        policy.requireRole(user, Role.COORDINATOR);
        var rows = jdbc.queryForList("SELECT student_id, section_id FROM enrolment_requests WHERE id = ? AND site_id = ? AND status = 'PENDING'", requestId, user.siteId());
        if (rows.isEmpty()) {
            throw new NotFoundException("The request");
        }
        UUID studentId = (UUID) rows.get(0).get("student_id");
        UUID sectionId = (UUID) rows.get(0).get("section_id");
        if (approve) {
            Person student = people.findById(studentId).orElseThrow(() -> new NotFoundException("The student"));
            setup.enrol(user, sectionId, student.getEmail());
        }
        jdbc.update("UPDATE enrolment_requests SET status = ?, decided_by = ?, decided_at = now() WHERE id = ?", approve ? "APPROVED" : "DECLINED", user.personId(), requestId);
        audit.log(user.orgId(), user.siteId(), ActivityActions.ENROLMENT_REQUEST_DECIDED).by(user).on("Person", studentId)
                .change("pending", approve ? "approved" : "declined").save();
    }

    /** The school's students who are not in any class yet, with their IDs, for the coordinator's enrol list. */
    @Transactional(readOnly = true)
    public List<UnenrolledStudent> unenrolled(CurrentUser user) {
        policy.requireRole(user, Role.COORDINATOR);
        Set<UUID> enrolled = enrolments.findBySiteId(user.siteId()).stream().map(Enrolment::getStudentId).collect(Collectors.toSet());
        return people.findBySiteIdAndRole(user.siteId(), Role.STUDENT).stream()
                .filter(p -> p.canSignIn() && !enrolled.contains(p.getId()))
                .sorted(java.util.Comparator.comparing(Person::getDisplayName))
                .map(p -> new UnenrolledStudent(p.getId(), p.getDisplayName(), p.getEmail(), p.getStudentCode())).toList();
    }
}
