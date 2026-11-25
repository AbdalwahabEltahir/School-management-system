package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import qa.sijill.sijill.network.NetworkDtos.CreateItRequest;
import qa.sijill.sijill.network.NetworkDtos.ItAccount;
import qa.sijill.sijill.network.NetworkDtos.LogPage;
import qa.sijill.sijill.network.NetworkDtos.School;
import qa.sijill.sijill.network.NetworkService;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.model.Role;

import org.springframework.beans.factory.annotation.Autowired;

/** EAA IT across the network: school head counts, switching a school, School IT accounts, and the searchable log. */
class NetworkTest extends WorldTestSupport {

    @Autowired NetworkService network;

    private record Scene(World w, Person eaa) {}

    private Scene scene() {
        World w = world();
        Person eaa = person(w.org(), w.school(), "eaa-" + UUID.randomUUID() + "@" + w.school().getDomain(), "Lina EAA", Role.EAA_ADMIN, null);
        return new Scene(w, eaa);
    }

    @Test
    void schoolsShowStudentsStaffAndPrincipalAndOnlyEaaMaySee() {
        Scene s = scene();
        person(s.w().org(), s.w().school(), "kid1@" + s.w().school().getDomain(), "Kid One", Role.STUDENT, null);
        person(s.w().org(), s.w().school(), "kid2@" + s.w().school().getDomain(), "Kid Two", Role.STUDENT, null);

        School school = network.schools(user(s.eaa())).stream().filter(x -> x.id().equals(s.w().school().getId())).findFirst().orElseThrow();
        assertThat(school.students()).isEqualTo(2);
        assertThat(school.staff()).isEqualTo(4);                       // teacher, coordinator, principal, school IT
        assertThat(school.principalName()).isEqualTo("Pia Principal");
        assertThat(network.school(user(s.eaa()), school.id()).roles()).extracting(r -> r.role()).contains("STUDENT", "TEACHER", "PRINCIPAL");

        assertThatThrownBy(() -> network.schools(user(s.w().it()))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> network.schools(user(s.w().principal()))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void eaaSwitchesASchoolOffAndOnAndItIsLogged() {
        Scene s = scene();
        UUID id = s.w().school().getId();
        assertThat(network.setSchoolActive(user(s.eaa()), id, false).school().active()).isFalse();
        assertThat(network.setSchoolActive(user(s.eaa()), id, true).school().active()).isTrue();
        LogPage log = network.log(user(s.eaa()), null, null, id, "SITE_UPDATED", null, null, 0, 20);
        assertThat(log.total()).isGreaterThanOrEqualTo(2);
        assertThat(log.rows().get(0).actorName()).isEqualTo("Lina EAA");
        assertThatThrownBy(() -> network.setSchoolActive(user(s.w().it()), id, false)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void eaaCreatesSuspendsReactivatesAndDeletesASchoolItAccount() {
        Scene s = scene();
        String email = "newit-" + UUID.randomUUID() + "@" + s.w().school().getDomain();
        ItAccount made = network.createIt(user(s.eaa()), new CreateItRequest(s.w().school().getId(), "Nadia IT", email));
        assertThat(made.status()).isEqualTo("ACTIVE");
        assertThat(network.itAccounts(user(s.eaa()))).extracting(a -> a.email()).contains(email, s.w().it().getEmail());

        assertThat(network.setItStatus(user(s.eaa()), made.id(), "LEFT").status()).isEqualTo("LEFT");
        assertThat(network.setItStatus(user(s.eaa()), made.id(), "ACTIVE").status()).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> network.setItStatus(user(s.eaa()), made.id(), "BANANA")).isInstanceOf(BadRequestException.class);

        network.deleteIt(user(s.eaa()), made.id());
        assertThat(network.itAccounts(user(s.eaa()))).extracting(a -> a.email()).doesNotContain(email);

        assertThatThrownBy(() -> network.createIt(user(s.eaa()), new CreateItRequest(s.w().school().getId(), "Dup", s.w().it().getEmail()))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> network.createIt(user(s.eaa()), new CreateItRequest(s.w().school().getId(), "Bad", "x@nowhere.example"))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> network.createIt(user(s.w().it()), new CreateItRequest(s.w().school().getId(), "No", email))).isInstanceOf(ForbiddenException.class);
        // only School IT accounts can be managed here
        assertThatThrownBy(() -> network.deleteIt(user(s.eaa()), s.w().teacher().getId())).isInstanceOf(qa.sijill.sijill.shared.exception.NotFoundException.class);
    }

    @Test
    void theLogNamesWhoDidItAndFiltersByDateSchoolActionAreaAndSearch() {
        Scene s = scene();
        String email = "logit-" + UUID.randomUUID() + "@" + s.w().school().getDomain();
        network.createIt(user(s.eaa()), new CreateItRequest(s.w().school().getId(), "Zed Logged", email));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Qatar"));
        LogPage all = network.log(user(s.eaa()), today, today, s.w().school().getId(), "PERSON_ADDED", "Person", "Zed", 0, 50);
        assertThat(all.rows()).hasSize(1);
        assertThat(all.rows().get(0).actorName()).isEqualTo("Lina EAA");
        assertThat(all.rows().get(0).targetName()).isEqualTo("Zed Logged");
        assertThat(all.rows().get(0).schoolName()).isEqualTo(s.w().school().getName());
        assertThat(all.actions()).contains("PERSON_ADDED");
        assertThat(all.areas()).contains("Person");

        assertThat(network.log(user(s.eaa()), today.plusDays(1), null, null, null, null, "Zed", 0, 50).rows()).isEmpty();      // starts tomorrow
        assertThat(network.log(user(s.eaa()), null, today.minusDays(1), null, null, null, "Zed", 0, 50).rows()).isEmpty();     // ended yesterday
        assertThat(network.log(user(s.eaa()), null, null, UUID.randomUUID(), null, null, "Zed", 0, 50).rows()).isEmpty();      // another school
        assertThat(network.log(user(s.eaa()), null, null, null, "PERSON_DELETED", null, "Zed", 0, 50).rows()).isEmpty();       // another action
        assertThatThrownBy(() -> network.log(user(s.w().principal()), null, null, null, null, null, null, 0, 10)).isInstanceOf(ForbiddenException.class);

        // School IT reads the log of their own school only, whatever school they ask for
        Scene other = scene();
        network.createIt(user(other.eaa()), new CreateItRequest(other.w().school().getId(), "Other Person", "other-" + UUID.randomUUID() + "@" + other.w().school().getDomain()));
        LogPage mine = network.log(user(s.w().it()), null, null, other.w().school().getId(), "PERSON_ADDED", null, null, 0, 50);
        assertThat(mine.rows()).isNotEmpty().allMatch(r -> s.w().school().getId().equals(r.schoolId()));
        assertThat(mine.rows()).extracting(r -> r.targetName()).contains("Zed Logged").doesNotContain("Other Person");
    }
}
