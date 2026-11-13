package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import qa.sijill.sijill.approvals.model.ApprovalStatus;
import qa.sijill.sijill.approvals.service.ApprovalService;
import qa.sijill.sijill.checks.service.ConnectionService;
import qa.sijill.sijill.files.service.FileService;
import qa.sijill.sijill.organisation.service.SiteService;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.service.PeopleService;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.model.Sensitivity;
import qa.sijill.sijill.workspace.DriveType;

/**
 * The one action that needs a second person is deleting a file. Everything else (publishing marks, roles, schools,
 * the Drive connection) happens directly and is logged, and who may do it is still restricted by role.
 */
class FourEyesTest extends WorldTestSupport {

    @Autowired ApprovalService approvals;
    @Autowired PeopleService peopleService;
    @Autowired SiteService siteService;
    @Autowired ConnectionService connection;
    @Autowired FileService fileService;

    private qa.sijill.sijill.files.model.SchoolFile teacherFile(World w) {
        return file(w, "Term plan " + UUID.randomUUID() + ".docx", new Profile("Policy", Sensitivity.INTERNAL, false, false), DriveType.SHARED, "Academic/Maths");
    }

    @Test
    void deletingAFileWaitsForASecondPersonAndNeverTouchesTheDrive() {
        World w = world();
        var f = teacherFile(w);

        fileService.requestDelete(user(w.teacher()), f.getId());
        assertThat(files.findById(f.getId()).orElseThrow().getTrashedAt()).isNull();               // not yet
        var request = approvals.list(user(w.principal())).stream().filter(r -> r.getAction().equals("FILE_DELETE")).findFirst().orElseThrow();

        // the person who asked, the leader of another role and a teacher cannot approve; the principal can
        assertThatThrownBy(() -> approvals.approve(user(w.teacher()), request.getId())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> approvals.approve(user(w.leader()), request.getId())).isInstanceOf(ForbiddenException.class);
        approvals.approve(user(w.principal()), request.getId());

        assertThat(files.findById(f.getId()).orElseThrow().getTrashedAt()).isNotNull();
        assertThat(count(w.teacher(), NotificationKind.SECURITY)).isEqualTo(1);                      // the requester is told
    }

    @Test
    void approversAreToldAndOnlyTheOwnerOrACoordinatorCanAsk() {
        World w = world();
        var f = teacherFile(w);
        fileService.requestDelete(user(w.teacher()), f.getId());
        assertThat(count(w.principal(), NotificationKind.SECURITY)).isEqualTo(1);
        assertThat(count(w.it(), NotificationKind.SECURITY)).isEqualTo(1);
        assertThat(count(w.coordinator(), NotificationKind.SECURITY)).isEqualTo(1);
        assertThat(count(w.teacher(), NotificationKind.SECURITY)).isZero();                          // not the person who asked

        assertThatThrownBy(() -> fileService.requestDelete(user(w.principal()), f.getId())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> fileService.requestDelete(user(w.it()), f.getId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aRejectedDeletionChangesNothing() {
        World w = world();
        var f = teacherFile(w);
        fileService.requestDelete(user(w.teacher()), f.getId());
        var request = approvals.list(user(w.it())).stream().filter(r -> r.getAction().equals("FILE_DELETE")).findFirst().orElseThrow();
        approvals.reject(user(w.it()), request.getId(), "Still needed");

        assertThat(files.findById(f.getId()).orElseThrow().getTrashedAt()).isNull();
        assertThat(approvals.list(user(w.it())).get(0).getStatus()).isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    void rolesSchoolsAndTheDriveConnectionChangeDirectlyAndAreLogged() {
        World w = world();

        peopleService.update(user(w.it()), w.teacher().getId(),
                new PeopleService.UpdateCommand(Role.COORDINATOR, null, null, null, null, null));
        assertThat(people.findById(w.teacher().getId()).orElseThrow().getRole()).isEqualTo(Role.COORDINATOR);  // at once
        assertThat(count(w.teacher(), NotificationKind.SECURITY)).isEqualTo(1);                                 // and the person is told

        var doomed = newSchool(w.org(), "Doomed " + UUID.randomUUID(), "d-" + UUID.randomUUID() + ".test");
        siteService.remove(user(w.leader()), doomed.getId());
        assertThat(sites.findById(doomed.getId()).orElseThrow().isActive()).isFalse();

        assertThatThrownBy(() -> connection.setEnabled(user(w.it()), w.school().getId(), false)).isInstanceOf(ForbiddenException.class);
        connection.setEnabled(user(w.leader()), w.school().getId(), false);
        assertThat(sites.findById(w.school().getId()).orElseThrow().isChecksEnabled()).isFalse();

        assertThat(approvals.list(user(w.leader())).stream().filter(r -> !r.getAction().equals("FILE_DELETE"))).isEmpty();
    }

    @Test
    void schoolItAndLeadershipCannotConfigureSchoolsOrRules() {
        World w = world();
        Person leadership = person(w.org(), sites.findById(w.leader().getSiteId()).orElseThrow(),
                "lead-" + UUID.randomUUID() + "@ho.test", "Read Only", Role.LEADERSHIP, null);
        var cmd = new SiteService.SiteCommand("X", "x-" + UUID.randomUUID() + ".test", null, null, null, null);

        assertThatThrownBy(() -> siteService.create(user(w.it()), cmd)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> siteService.create(user(leadership), cmd)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> siteService.update(user(w.it()), w.school().getId(), cmd)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> siteService.remove(user(leadership), w.school().getId())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> connection.setEnabled(user(leadership), w.school().getId(), false)).isInstanceOf(ForbiddenException.class);
        assertThat(siteService.list(user(leadership))).isNotEmpty(); // but they can look
    }
}
