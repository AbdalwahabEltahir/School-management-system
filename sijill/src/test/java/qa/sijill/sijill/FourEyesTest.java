package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import qa.sijill.sijill.approvals.model.ApprovalStatus;
import qa.sijill.sijill.approvals.service.ApprovalService;
import qa.sijill.sijill.checks.service.ConnectionService;
import qa.sijill.sijill.organisation.service.SiteService;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.service.PeopleService;
import qa.sijill.sijill.rules.service.RuleService;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.model.Role;

/** Four-eyes approvals, and who may change schools, rules, connections and roles. */
class FourEyesTest extends WorldTestSupport {

    @Autowired ApprovalService approvals;
    @Autowired PeopleService peopleService;
    @Autowired SiteService siteService;
    @Autowired ConnectionService connection;

    private Person secondEaaAdmin(World w) {
        return person(w.org(), sites.findById(w.leader().getSiteId()).orElseThrow(),
                "eaa2-" + UUID.randomUUID() + "@ho.test", "Second Admin", Role.EAA_ADMIN, null);
    }

    @Test
    void aRoleChangeWaitsForAnEaaAdministratorOtherThanTheRequester() {
        World w = world();
        Person other = secondEaaAdmin(w);

        peopleService.update(user(w.it()), w.teacher().getId(),
                new PeopleService.UpdateCommand(Role.COORDINATOR, null, null, null, null, null));
        assertThat(people.findById(w.teacher().getId()).orElseThrow().getRole()).isEqualTo(Role.TEACHER); // not yet

        var request = approvals.list(user(w.leader())).stream()
                .filter(r -> r.getAction().equals("ROLE_CHANGE")).findFirst().orElseThrow();
        // the person who asked (School IT) and the principal cannot approve
        assertThatThrownBy(() -> approvals.approve(user(w.it()), request.getId())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> approvals.approve(user(w.principal()), request.getId())).isInstanceOf(ForbiddenException.class);

        approvals.approve(user(other), request.getId());
        assertThat(people.findById(w.teacher().getId()).orElseThrow().getRole()).isEqualTo(Role.COORDINATOR);
    }

    @Test
    void anEaaAdministratorCannotApproveTheirOwnRequest() {
        World w = world();
        Person other = secondEaaAdmin(w);
        Person school = w.teacher();

        siteService.remove(user(w.leader()), newSchool(w.org(), "Doomed " + UUID.randomUUID(), "d-" + UUID.randomUUID() + ".test").getId());
        var request = approvals.list(user(w.leader())).stream()
                .filter(r -> r.getAction().equals("SITE_REMOVE")).findFirst().orElseThrow();

        assertThatThrownBy(() -> approvals.approve(user(w.leader()), request.getId())).isInstanceOf(ForbiddenException.class);
        assertThat(sites.findById(request.getTargetId()).orElseThrow().isActive()).isTrue();

        approvals.approve(user(other), request.getId());
        assertThat(sites.findById(request.getTargetId()).orElseThrow().isActive()).isFalse();
        assertThat(school).isNotNull();
    }

    @Test
    void aRejectedRequestChangesNothing() {
        World w = world();
        Person other = secondEaaAdmin(w);
        var target = newSchool(w.org(), "Kept " + UUID.randomUUID(), "k-" + UUID.randomUUID() + ".test");

        siteService.remove(user(w.leader()), target.getId());
        var request = approvals.list(user(w.leader())).stream().filter(r -> r.getAction().equals("SITE_REMOVE")).findFirst().orElseThrow();
        approvals.reject(user(other), request.getId(), "Not now");

        assertThat(sites.findById(target.getId()).orElseThrow().isActive()).isTrue();
        assertThat(approvals.list(user(w.leader())).get(0).getStatus()).isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    void disconnectingDriveIsForEaaAdministratorsAndNeedsASecondApproval() {
        World w = world();
        Person other = secondEaaAdmin(w);

        assertThatThrownBy(() -> connection.setEnabled(user(w.it()), w.school().getId(), false)).isInstanceOf(ForbiddenException.class);

        connection.setEnabled(user(w.leader()), w.school().getId(), false);
        boolean before = sites.findById(w.school().getId()).orElseThrow().isChecksEnabled();
        var request = approvals.list(user(w.leader())).stream().filter(r -> r.getAction().equals("DRIVE_DISCONNECT")).findFirst().orElseThrow();
        assertThat(sites.findById(w.school().getId()).orElseThrow().isChecksEnabled()).isEqualTo(before);

        approvals.approve(user(other), request.getId());
        assertThat(sites.findById(w.school().getId()).orElseThrow().isChecksEnabled()).isFalse();
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
