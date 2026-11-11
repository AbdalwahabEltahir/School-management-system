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

/** Students and parents register and sign in with their own e-mail and password; the coordinator gives students a class. */
class AccountsTest extends WorldTestSupport {

    @Autowired AccountService accounts;
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

    @Test
    void aStudentRegistersWithAnyEmailGetsAStudentIdAndSignsInWithThePassword() {
        World w = world();
        String email = mail("sami");
        var student = accounts.registerStudent(w.school().getId(), "Sami Student", email, "correct horse");
        String ip = ip();

        assertThat(student.getRole()).isEqualTo(Role.STUDENT);
        assertThat(student.getStudentCode()).matches("[1-9][0-9]{9}");
        assertThat(accounts.login(email.toUpperCase(), "correct horse", ip).getId()).isEqualTo(student.getId());
        assertThatThrownBy(() -> accounts.login(email, "wrong password", ip)).isInstanceOf(BadRequestException.class).hasMessageContaining("Wrong e-mail or password");
        assertThatThrownBy(() -> accounts.login("nobody@family-mail.test", "correct horse", ip)).isInstanceOf(BadRequestException.class).hasMessageContaining("Wrong e-mail or password");
        String hash = jdbc.queryForObject("SELECT password_hash FROM credentials WHERE person_id = ?", String.class, student.getId());
        assertThat(hash).startsWith("$2").doesNotContain("correct horse"); // only a hash is stored
        assertThatThrownBy(() -> accounts.registerStudent(w.school().getId(), "Again", email, "correct horse")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> accounts.registerStudent(w.school().getId(), "Short", mail("x"), "short")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void fiveWrongPasswordsLockTheAccountAndStaffCannotUseAPassword() {
        World w = world();
        String email = mail("lock");
        accounts.registerStudent(w.school().getId(), "Lock Student", email, "correct horse");
        String ip = ip();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> accounts.login(email, "nope nope", ip)).isInstanceOf(BadRequestException.class);
        }
        assertThatThrownBy(() -> accounts.login(email, "correct horse", ip)).isInstanceOf(ConflictException.class).hasMessageContaining("15 minutes");
        // staff have no password: they sign in with Google
        assertThatThrownBy(() -> accounts.login(w.teacher().getEmail(), "anything", ip)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void twoParentsUseTheSameStudentIdWithTheirOwnEmailAndPassword() {
        World w = world();
        var kid = accounts.registerStudent(w.school().getId(), "Layla Student", mail("layla"), "correct horse");
        String ip = ip();

        var mum = accounts.registerParent("Mona Parent", mail("mona"), "mum password", kid.getStudentCode(), ip);
        var dad = accounts.registerParent("Omar Parent", mail("omar"), "dad password", kid.getStudentCode(), ip);

        assertThat(mum.getRole()).isEqualTo(Role.PARENT);
        assertThat(mum.getSiteId()).isEqualTo(kid.getSiteId());
        for (var parent : new qa.sijill.sijill.people.model.Person[] {mum, dad}) {
            assertThat(parentPortal.children(user(parent))).singleElement().satisfies(c -> assertThat(c.name()).isEqualTo("Layla Student"));
        }
        assertThat(accounts.login(mum.getEmail(), "mum password", ip).getId()).isEqualTo(mum.getId());
        assertThat(accounts.login(dad.getEmail(), "dad password", ip).getId()).isEqualTo(dad.getId());
        assertThatThrownBy(() -> accounts.registerParent("Who", mail("who"), "some password", "1234567890", ip)).isInstanceOf(BadRequestException.class).hasMessageContaining("student ID");
        assertThatThrownBy(() -> accounts.registerParent("Dup", mum.getEmail(), "some password", kid.getStudentCode(), ip)).isInstanceOf(ConflictException.class);

        // a parent follows a second child later with that child's ID
        var other = accounts.registerStudent(w.school().getId(), "Noor Student", mail("noor"), "correct horse");
        accounts.addChild(user(mum), other.getStudentCode(), ip);
        assertThat(parentPortal.children(user(mum))).hasSize(2);
        assertThat(parentPortal.children(user(dad))).hasSize(1);
    }

    @Test
    void guessingStudentIdsIsThrottled() {
        World w = world();
        String ip = ip();
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> accounts.registerParent("G", mail("g"), "some password", "9" + String.valueOf(System.nanoTime()).substring(0, 9), ip)).isInstanceOf(BadRequestException.class);
        }
        assertThatThrownBy(() -> accounts.registerParent("G", mail("g"), "some password", "1111111111", ip)).isInstanceOf(ConflictException.class).hasMessageContaining("Too many");
    }

    @Test
    void aStudentOrParentAsksForAClassAndTheCoordinatorConfirms() {
        World w = world();
        var coordinator = user(w.coordinator());
        var section = academic.createSection(coordinator, 7, "B");
        var kid = accounts.registerStudent(w.school().getId(), "Rana Student", mail("rana"), "correct horse");
        var stranger = accounts.registerStudent(w.school().getId(), "Other Student", mail("other"), "correct horse");
        String ip = ip();
        var mum = accounts.registerParent("Rana Mum", mail("ranamum"), "mum password", kid.getStudentCode(), ip);

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
