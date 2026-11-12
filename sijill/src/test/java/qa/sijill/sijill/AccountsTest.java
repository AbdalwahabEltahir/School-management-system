package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import qa.sijill.sijill.academic.service.AcademicSetupService;
import qa.sijill.sijill.academic.service.EnrolmentRequestService;
import qa.sijill.sijill.academic.service.ParentPortalService;
import qa.sijill.sijill.people.service.AccountService;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;

/** Invite-only onboarding: the school issues one-time codes; students and parents then sign in with their own e-mail and password. */
class AccountsTest extends WorldTestSupport {

    @Autowired AccountService accounts;
    @Autowired qa.sijill.sijill.people.service.InvitationService invitations;
    @Autowired EnrolmentRequestService enrol;
    @Autowired ParentPortalService parentPortal;
    @Autowired AcademicSetupService academic;
    @Autowired JdbcTemplate jdbc;

    private String ip() {
        return "ip-" + UUID.randomUUID(); // each test gets its own throttle bucket
    }

    private String mail(String who) {
        return who + "-" + UUID.randomUUID().toString().substring(0, 6) + "@family-mail.test";
    }

    private qa.sijill.sijill.people.model.Person student(World w, String name) {
        return person(w.org(), w.school(), mail("kid"), name, Role.STUDENT, null);
    }

    /** The school (School IT) issues a code and the student sets a password; returns the activated student. */
    private qa.sijill.sijill.people.model.Person activated(World w, String name, String password) {
        var kid = student(w, name);
        var issued = invitations.issue(user(w.it()), "STUDENT", java.util.List.of(kid.getId()), null);
        return invitations.redeemStudent(issued.code(), password, ip());
    }

    @Test
    void thereIsNoOpenRegistrationAndAStudentActivatesWithAOneTimeCode() {
        World w = world();
        var kid = student(w, "Sami Student");
        String ip = ip();
        assertThat(kid.getStudentCode()).matches("[1-9][0-9]{9}");
        assertThatThrownBy(() -> accounts.login(kid.getEmail(), "correct horse", ip)).isInstanceOf(BadRequestException.class); // no password yet

        var issued = invitations.issue(user(w.it()), "STUDENT", java.util.List.of(kid.getId()), null);
        assertThat(issued.code()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");
        String stored = jdbc.queryForObject("SELECT code_hash FROM invitations WHERE id = ?", String.class, issued.id());
        assertThat(stored).doesNotContain(issued.code()); // only a hash is stored

        assertThatThrownBy(() -> invitations.redeemStudent(issued.code(), "short", ip)).isInstanceOf(BadRequestException.class);
        invitations.redeemStudent(issued.code().toLowerCase(), "correct horse", ip);
        assertThat(accounts.login(kid.getEmail().toUpperCase(), "correct horse", ip).getId()).isEqualTo(kid.getId());
        assertThatThrownBy(() -> accounts.login(kid.getEmail(), "wrong password", ip)).isInstanceOf(BadRequestException.class).hasMessageContaining("Wrong e-mail or password");
        String hash = jdbc.queryForObject("SELECT password_hash FROM credentials WHERE person_id = ?", String.class, kid.getId());
        assertThat(hash).startsWith("$2").doesNotContain("correct horse");
        // the code works once
        assertThatThrownBy(() -> invitations.redeemStudent(issued.code(), "another password", ip)).isInstanceOf(BadRequestException.class).hasMessageContaining("not valid");
    }

    @Test
    void fiveWrongPasswordsLockTheAccountAndStaffCannotUseAPassword() {
        World w = world();
        var kid = activated(w, "Lock Student", "correct horse");
        String email = kid.getEmail();
        String ip = ip();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> accounts.login(email, "nope nope", ip)).isInstanceOf(BadRequestException.class);
        }
        assertThatThrownBy(() -> accounts.login(email, "correct horse", ip)).isInstanceOf(ConflictException.class).hasMessageContaining("15 minutes");
        // staff have no password: they sign in with Google
        assertThatThrownBy(() -> accounts.login(w.teacher().getEmail(), "anything", ip)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void oneCodeLinksSeveralChildrenAndEachParentUsesTheirOwnCode() {
        World w = world();
        var layla = student(w, "Layla Student");
        var noor = student(w, "Noor Student");
        String ip = ip();

        var both = invitations.issue(user(w.it()), "PARENT", java.util.List.of(layla.getId(), noor.getId()), null);
        var mum = invitations.redeemNewParent(both.code(), "Mona Parent", mail("mona"), "mum password", ip);
        var justLayla = invitations.issue(user(w.coordinator()), "PARENT", java.util.List.of(layla.getId()), null);
        var dad = invitations.redeemNewParent(justLayla.code(), "Omar Parent", mail("omar"), "dad password", ip);

        assertThat(mum.getRole()).isEqualTo(Role.PARENT);
        assertThat(parentPortal.children(user(mum))).extracting(c -> c.name()).containsExactlyInAnyOrder("Layla Student", "Noor Student");
        assertThat(parentPortal.children(user(dad))).singleElement().satisfies(c -> assertThat(c.name()).isEqualTo("Layla Student"));
        assertThat(accounts.login(mum.getEmail(), "mum password", ip).getId()).isEqualTo(mum.getId());

        // a code is used once, and a wrong one says nothing about why
        assertThatThrownBy(() -> invitations.redeemNewParent(both.code(), "Again", mail("again"), "some password", ip)).isInstanceOf(BadRequestException.class).hasMessageContaining("not valid");
        assertThatThrownBy(() -> invitations.redeemNewParent("AAAA-BBBB-CCCC", "Who", mail("who"), "some password", ip)).isInstanceOf(BadRequestException.class).hasMessageContaining("not valid");

        // an existing parent follows another child with a new code
        var third = student(w, "Rima Student");
        var more = invitations.issue(user(w.it()), "PARENT", java.util.List.of(third.getId()), null);
        invitations.redeemForParent(user(dad), more.code(), ip);
        assertThat(parentPortal.children(user(dad))).hasSize(2);
    }

    @Test
    void invitationsAreIssuedBySchoolStaffForTheirOwnStudentsOnly() {
        World w = world();
        World other = world();
        var kid = student(w, "Kid Student");
        String ip = ip();
        assertThatThrownBy(() -> invitations.issue(user(w.teacher()), "PARENT", java.util.List.of(kid.getId()), null)).isInstanceOf(qa.sijill.sijill.shared.exception.ForbiddenException.class);
        assertThatThrownBy(() -> invitations.issue(user(other.it()), "PARENT", java.util.List.of(kid.getId()), null)).isInstanceOf(BadRequestException.class);

        var bound = invitations.issue(user(w.it()), "PARENT", java.util.List.of(kid.getId()), "mum@family-mail.test");
        assertThatThrownBy(() -> invitations.redeemNewParent(bound.code(), "Wrong Person", "someone-else@family-mail.test", "some password", ip)).isInstanceOf(BadRequestException.class);
        invitations.revoke(user(w.it()), bound.id());
        assertThatThrownBy(() -> invitations.redeemNewParent(bound.code(), "Mum", "mum@family-mail.test", "some password", ip)).isInstanceOf(BadRequestException.class).hasMessageContaining("not valid");
        assertThat(invitations.list(user(w.it()))).anySatisfy(i -> assertThat(i.status()).isEqualTo("REVOKED"));
    }

    @Test
    void guessingInvitationCodesIsThrottled() {
        String ip = ip();
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> invitations.redeemNewParent("ZZZZ-" + System.nanoTime() % 9999 + "-GUESS", "G", mail("g"), "some password", ip)).isInstanceOf(BadRequestException.class);
        }
        assertThatThrownBy(() -> invitations.redeemNewParent("AAAA-BBBB-CCCC", "G", mail("g"), "some password", ip)).isInstanceOf(ConflictException.class).hasMessageContaining("Too many");
    }

    @Test
    void aStudentOrParentAsksForAClassAndTheCoordinatorConfirms() {
        World w = world();
        var coordinator = user(w.coordinator());
        var section = academic.createSection(coordinator, 7, "B");
        var kid = activated(w, "Rana Student", "correct horse");
        var stranger = activated(w, "Other Student", "correct horse");
        String ip = ip();
        var mum = invitations.redeemNewParent(invitations.issue(user(w.it()), "PARENT", java.util.List.of(kid.getId()), null).code(),
                "Rana Mum", mail("ranamum"), "mum password", ip);

        assertThat(enrol.status(user(kid)).section()).isNull();
        assertThat(enrol.unenrolled(coordinator)).extracting(s -> s.studentCode()).contains(kid.getStudentCode());
        assertThat(enrol.classes(user(mum))).extracting(c -> c.id()).contains(section.id());

        assertThatThrownBy(() -> enrol.request(user(mum), stranger.getId(), section.id())).isInstanceOf(NotFoundException.class); // not her child
        enrol.request(user(mum), kid.getId(), section.id());
        assertThatThrownBy(() -> enrol.request(user(kid), null, section.id())).isInstanceOf(ConflictException.class); // already waiting
        assertThat(parentPortal.children(user(mum)).get(0).pendingClass()).isNotNull();

        var queue = enrol.pending(coordinator);
        assertThat(queue).singleElement().satisfies(r -> assertThat(r.studentCode()).isEqualTo(kid.getStudentCode()));
        assertThatThrownBy(() -> enrol.pending(user(w.teacher()))).isInstanceOf(qa.sijill.sijill.shared.exception.ForbiddenException.class);

        enrol.decide(coordinator, queue.get(0).id(), true);
        assertThat(enrol.status(user(kid)).section()).isNotNull();
        assertThat(enrol.pending(coordinator)).isEmpty();
        assertThat(enrol.unenrolled(coordinator)).extracting(s -> s.studentCode()).doesNotContain(kid.getStudentCode());
        assertThatThrownBy(() -> enrol.request(user(kid), null, section.id())).isInstanceOf(ConflictException.class); // already in a class

        // a declined request leaves the student without a class
        enrol.request(user(stranger), null, section.id());
        enrol.decide(coordinator, enrol.pending(coordinator).get(0).id(), false);
        assertThat(enrol.status(user(stranger)).section()).isNull();
    }
}
