package qa.sijill.sijill.people.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.shared.exception.UnauthenticatedException;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.shared.security.CurrentUserProvider;

/**
 * Resolves "who is calling" (proposal section 4, "Identity and accounts").
 *
 * <p>Staff sign in with their own school account through OpenID Connect; Sijill has no passwords and
 * no sign-up. The account's e-mail is matched to a row in {@code people}. Anything that does not match
 * is refused with the standard message, and generic (shared) accounts can never sign in.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService implements CurrentUserProvider {

    static final String NOT_SET_UP = "Your account is not set up in Sijill yet. Contact your school IT.";

    private final PersonRepository people;
    private final SiteRepository sites;

    /** The signed-in person, or an {@link UnauthenticatedException} (HTTP 401) if there is none. */
    @Override
    public CurrentUser require() {
        String email = signedInEmail();

        // Only the school domains (and head office) are accepted.
        String domain = email.substring(email.indexOf('@') + 1);
        sites.findByDomainIgnoreCase(domain)
                .filter(s -> s.isActive())
                .orElseThrow(() -> new UnauthenticatedException(NOT_SET_UP));

        Person person = people.findByEmailIgnoreCase(email)
                .filter(Person::canSignIn)
                .orElseThrow(() -> new UnauthenticatedException(NOT_SET_UP));

        return new CurrentUser(person.getId(), person.getOrgId(), person.getSiteId(), person.getRole(),
                person.getSubjectId(), person.getEmail(), person.getDisplayName());
    }

    /** Extracts the e-mail from the Spring Security context (OIDC attribute, or username for dev logins). */
    private String signedInEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthenticatedException("Please sign in with your school account.");
        }
        String email = auth.getPrincipal() instanceof OAuth2User oauth
                ? oauth.getAttribute("email")
                : auth.getName();
        if (email == null || !email.contains("@")) {
            throw new UnauthenticatedException(NOT_SET_UP);
        }
        return email;
    }
}
