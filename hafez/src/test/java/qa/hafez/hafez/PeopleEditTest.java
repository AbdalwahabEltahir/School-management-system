package qa.hafez.hafez;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import qa.hafez.hafez.people.service.PeopleService;
import qa.hafez.hafez.shared.exception.ConflictException;
import qa.hafez.hafez.shared.model.Role;

/** School IT can edit a person's name and e-mail, and delete a person who has no records. */
class PeopleEditTest extends WorldTestSupport {

    @Autowired PeopleService peopleService;

    @Test
    void itRenamesAPersonAndChangesTheirEmail() {
        World w = world();
        String email = "renamed@" + w.school().getDomain();

        peopleService.update(user(w.it()), w.teacher().getId(),
                new PeopleService.UpdateCommand(null, null, null, null, "New Name", email));

        var p = people.findById(w.teacher().getId()).orElseThrow();
        assertThat(p.getDisplayName()).isEqualTo("New Name");
        assertThat(p.getEmail()).isEqualTo(email);
    }

    @Test
    void anEmailAnotherPersonHasIsRefused() {
        World w = world();
        assertThatThrownBy(() -> peopleService.update(user(w.it()), w.teacher().getId(),
                new PeopleService.UpdateCommand(null, null, null, null, null, w.coordinator().getEmail())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void itDeletesAPersonWithNoRecords() {
        World w = world();
        var added = peopleService.add(user(w.it()), "temp@" + w.school().getDomain(), "Temp", Role.TEACHER, null);

        peopleService.delete(user(w.it()), added.getId());

        assertThat(people.findById(added.getId())).isEmpty();
    }

    @Test
    void thePrincipalCanReadTheSchoolsPeopleButNotChangeThem() {
        World w = world();

        assertThat(peopleService.list(user(w.principal()))).extracting(qa.hafez.hafez.people.model.Person::getId).contains(w.teacher().getId());
        assertThatThrownBy(() -> peopleService.update(user(w.principal()), w.teacher().getId(),
                new PeopleService.UpdateCommand(null, null, null, null, "Nope", null)))
                .isInstanceOf(qa.hafez.hafez.shared.exception.ForbiddenException.class);
    }

    @Autowired qa.hafez.hafez.people.service.CurrentUserService currentUser;

    @org.junit.jupiter.api.AfterEach
    void clearSecurity() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void aStudentAndAParentSignInWithAnEmailFromAnyDomain() {
        World w = world();
        var student = peopleService.add(user(w.it()), "kid@outside.example", "Kid", Role.STUDENT, null);
        var parent = peopleService.add(user(w.it()), "mum@elsewhere.example", "Mum", Role.PARENT, null, student.getId());

        for (var p : java.util.List.of(student, parent)) {
            var auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                    p.getEmail(), null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + p.getRole())));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            assertThat(currentUser.require().role()).isEqualTo(p.getRole());
        }
    }
}
