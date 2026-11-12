package qa.sijill.sijill.academic.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.academic.dto.AcademicDtos.ChildView;
import qa.sijill.sijill.academic.dto.AcademicDtos.StudentDashboard;
import qa.sijill.sijill.academic.model.Section;
import qa.sijill.sijill.academic.repository.EnrolmentRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.people.service.ParentLinks;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.AccessPolicy;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * The parent portal: a parent sees exactly what their linked child sees, read-only. The child's view is built by the
 * student services, so the same rules apply (published results only, no classmates). Any other student is "not found".
 */
@Service
@RequiredArgsConstructor
public class ParentPortalService {

    private final ParentLinks links;
    private final PersonRepository people;
    private final EnrolmentRepository enrolments;
    private final AcademicAccess access;
    private final AccessPolicy policy;
    private final PortalService portal;
    private final EnrolmentRequestService requests;
    private final qa.sijill.sijill.shared.events.AuditPublisher audit;

    @Transactional(readOnly = true)
    public List<ChildView> children(CurrentUser user) {
        policy.requireRole(user, Role.PARENT);
        Map<UUID, Section> sections = access.sectionsById(user.siteId());
        return people.findAllById(links.childrenOf(user.personId())).stream()
                .map(c -> new ChildView(c.getId(), c.getDisplayName(),
                        enrolments.findByStudentId(c.getId()).map(e -> AcademicAccess.label(sections.get(e.getSectionId())))
                                .orElse(null),
                        requests.pendingLabel(c.getId())))
                .toList();
    }

    @Transactional
    public StudentDashboard child(CurrentUser user, UUID studentId) {
        policy.requireRole(user, Role.PARENT);
        if (!links.isLinked(user.personId(), studentId)) {
            throw new NotFoundException("The child");
        }
        Person c = people.findById(studentId).orElseThrow(() -> new NotFoundException("The child"));
        audit.log(c.getOrgId(), c.getSiteId(), qa.sijill.sijill.shared.events.ActivityActions.STUDENT_RECORD_VIEWED).by(user)
                .on("Person", c.getId()).change(null, "parent view").save();
        return portal.studentDashboard(new CurrentUser(c.getId(), c.getOrgId(), c.getSiteId(), Role.STUDENT, null, c.getEmail(), c.getDisplayName()));
    }
}
