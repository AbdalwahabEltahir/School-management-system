package qa.sijill.sijill.people.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.UnauthenticatedException;
import qa.sijill.sijill.shared.model.Role;
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

    private static final Logger log = LoggerFactory.getLogger(CurrentUserService.class);

    private final PersonRepository people;
    private final SiteRepository sites;
    private final AuditPublisher audit;
    private final TransactionTemplate tx;

    /** Members of a school's own Google Workspace sign in without approval once the school's first check has run. */
    @Value("${sijill.signin.auto-provision:true}")
    private boolean autoProvision;

    /** The signed-in person, or an {@link UnauthenticatedException} (HTTP 401) if there is none. */
    @Override
    public CurrentUser require() {
        String email = signedInEmail();

        // Only the school domains (and head office) are accepted.
        String domain = email.substring(email.indexOf('@') + 1);
        Site site = sites.findByDomainIgnoreCase(domain)
                .filter(s -> s.isActive())
                .orElseThrow(() -> new UnauthenticatedException(NOT_SET_UP));

        Person person = people.findByEmailIgnoreCase(email)
                .or(() -> provision(site, email))
                .filter(Person::canSignIn)
                .orElseThrow(() -> new UnauthenticatedException(NOT_SET_UP));

        return new CurrentUser(person.getId(), person.getOrgId(), person.getSiteId(), person.getRole(),
                person.getSubjectId(), person.getEmail(), person.getDisplayName());
    }

    /**
     * A member of the school's own Google Workspace gets an account the first time they sign in, as a Teacher (the
     * least privileged staff role; School IT can change it). It happens only when all of these hold:
     * <ul>
     *   <li>Google says the account belongs to the school's Workspace domain (the {@code hd} claim equals the
     *       school's domain) and the e-mail is verified. A personal Gmail account has no {@code hd}, so a school
     *       whose domain is gmail.com, as in a test setup, never gets open sign-in;</li>
     *   <li>the school's first check has finished, which means its Drive access has been granted;</li>
     *   <li>{@code sijill.signin.auto-provision} is not switched off.</li>
     * </ul>
     */
    private java.util.Optional<Person> provision(Site site, String email) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!autoProvision || site.getFirstCheckAt() == null || !(auth.getPrincipal() instanceof OAuth2User oauth)) {
            return java.util.Optional.empty();
        }
        Object hd = oauth.getAttribute("hd");
        Object verified = oauth.getAttribute("email_verified");
        boolean workspaceMember = hd instanceof String h && h.equalsIgnoreCase(site.getDomain())
                && (Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified)));
        if (!workspaceMember) {
            return java.util.Optional.empty();
        }
        try {
            Person created = tx.execute(status -> {
                Person p = new Person();
                p.setOrgId(site.getOrgId());
                p.setSiteId(site.getId());
                p.setEmail(email.toLowerCase());
                Object name = oauth.getAttribute("name");
                p.setDisplayName(name instanceof String n && !n.isBlank() ? n : email.substring(0, email.indexOf('@')));
                p.setRole(Role.TEACHER);
                p.setStatus(PersonStatus.ACTIVE);
                people.save(p);
                audit.log(p.getOrgId(), p.getSiteId(), ActivityActions.PERSON_ADDED).by(p.getId())
                        .on("Person", p.getId()).change(null, email.toLowerCase() + " as TEACHER (first sign-in)").save();
                return p;
            });
            log.info("Created a Teacher account for {} on first sign-in ({} Workspace)", email, site.getDomain());
            return java.util.Optional.ofNullable(created);
        } catch (DataIntegrityViolationException e) { // two requests at once: the other one created it
            return people.findByEmailIgnoreCase(email);
        }
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
