package qa.hafez.hafez;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import qa.hafez.hafez.people.service.CurrentUserService;
import qa.hafez.hafez.shared.exception.UnauthenticatedException;
import qa.hafez.hafez.shared.model.Role;

/**
 * Members of a school's own Google Workspace sign in without approval, but only when Google says they belong to the
 * school's domain, the e-mail is verified, and the school's first check (its Drive access) has run.
 */
class WorkspaceSignInTest extends WorldTestSupport {

    @Autowired CurrentUserService currentUser;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(String email, String hostedDomain, boolean verified) {
        Map<String, Object> attrs = new java.util.HashMap<>();
        attrs.put("sub", email);
        attrs.put("email", email);
        attrs.put("name", "New Colleague");
        attrs.put("email_verified", verified);
        if (hostedDomain != null) {
            attrs.put("hd", hostedDomain);
        }
        var principal = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, "sub");
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));
    }

    private void firstCheckDone(World w) {
        w.school().setFirstCheckAt(Instant.now());
        sites.save(w.school());
    }

    @Test
    void aWorkspaceMemberSignsInDirectlyAsATeacherOnceTheFirstCheckHasRun() {
        World w = world();
        firstCheckDone(w);
        String email = "new.colleague@" + w.school().getDomain();
        signIn(email, w.school().getDomain(), true);

        var user = currentUser.require();

        assertThat(user.role()).isEqualTo(Role.TEACHER);
        assertThat(user.siteId()).isEqualTo(w.school().getId());
        assertThat(people.findByEmailIgnoreCase(email)).get().satisfies(p -> {
            assertThat(p.getDisplayName()).isEqualTo("New Colleague");
            assertThat(p.canSignIn()).isTrue();
        });
        assertThat(currentUser.require().personId()).isEqualTo(user.personId()); // the second sign-in finds the same account
    }

    @Test
    void nobodyIsCreatedBeforeTheFirstCheckHasRun() {
        World w = world();
        signIn("early@" + w.school().getDomain(), w.school().getDomain(), true);
        assertThatThrownBy(() -> currentUser.require()).isInstanceOf(UnauthenticatedException.class);
        assertThat(people.findByEmailIgnoreCase("early@" + w.school().getDomain())).isEmpty();
    }

    @Test
    void anAccountThatGoogleDoesNotPlaceInTheWorkspaceIsRefused() {
        World w = world();
        firstCheckDone(w);
        String email = "outsider@" + w.school().getDomain();
        signIn(email, null, true);                                            // no hosted-domain claim: a personal account
        assertThatThrownBy(() -> currentUser.require()).isInstanceOf(UnauthenticatedException.class);
        signIn(email, "another-workspace.test", true);                         // a different Workspace
        assertThatThrownBy(() -> currentUser.require()).isInstanceOf(UnauthenticatedException.class);
        signIn(email, w.school().getDomain(), false);                          // e-mail not verified
        assertThatThrownBy(() -> currentUser.require()).isInstanceOf(UnauthenticatedException.class);
        assertThat(people.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void aPersonSchoolItAlreadyAddedKeepsTheirRole() {
        World w = world();
        firstCheckDone(w);
        signIn(w.coordinator().getEmail(), w.school().getDomain(), true);
        assertThat(currentUser.require().role()).isEqualTo(Role.COORDINATOR);
    }
}
