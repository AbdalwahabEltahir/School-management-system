package qa.sijill.sijill.setup;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.repository.OrgRepository;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.shared.model.Role;

/**
 * First start-up: if no organisation exists yet, creates one from configuration so the first
 * EAA administrator can sign in. Does nothing once an organisation exists, and nothing at all unless
 * {@code sijill.bootstrap.enabled=true}:
 * <pre>
 * sijill:
 *   bootstrap:
 *     enabled: true
 *     org-name: Example School Network
 *     head-office-name: Head Office
 *     head-office-domain: example.edu
 *     admin-email: first.leader@example.edu
 *     admin-name: First Leader
 * </pre>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sijill.bootstrap.enabled", havingValue = "true")
public class BootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);

    private final OrgRepository orgs;
    private final OrganisationSetupService setup;
    private final SiteRepository sites;
    private final PersonRepository people;
    private final Environment environment;

    /** An additional person to create at start-up (local testing): {@code sijill.bootstrap.extra-people}. */
    public record ExtraPerson(String email, String name, Role role) {
    }

    @Value("${sijill.bootstrap.org-name}") private String orgName;
    @Value("${sijill.bootstrap.head-office-name:Head Office}") private String headOfficeName;
    @Value("${sijill.bootstrap.head-office-domain}") private String headOfficeDomain;
    @Value("${sijill.bootstrap.admin-email}") private String adminEmail;
    @Value("${sijill.bootstrap.admin-name:Administrator}") private String adminName;

    @Override
    public void run(ApplicationArguments args) {
        if (orgs.count() == 0) {
            setup.createOrganisation(orgName, headOfficeName, headOfficeDomain, adminEmail, adminName);
            log.info("Created organisation '{}' with first Leadership person {}", orgName, adminEmail);
        }
        // Earlier versions made the first account a Leadership person; Leadership is read-only now, so promote it.
        people.findByEmailIgnoreCase(adminEmail).filter(p -> p.getRole() == Role.LEADERSHIP).ifPresent(p -> {
            p.setRole(Role.EAA_ADMIN);
            people.save(p);
            log.info("Promoted {} to EAA administrator", adminEmail);
        });
        addExtraPeople();
    }

    /** Adds each configured extra person to the head-office site unless that e-mail already exists. */
    private void addExtraPeople() {
        List<ExtraPerson> extras = Binder.get(environment)
                .bind("sijill.bootstrap.extra-people", Bindable.listOf(ExtraPerson.class)).orElse(List.of());
        if (extras.isEmpty()) {
            return;
        }
        Site site = sites.findByDomainIgnoreCase(headOfficeDomain).orElse(null);
        if (site == null) {
            log.warn("Extra people not added: no site with domain {}", headOfficeDomain);
            return;
        }
        for (ExtraPerson extra : extras) {
            if (people.findByEmailIgnoreCase(extra.email()).isPresent()) {
                continue;
            }
            Person person = new Person();
            person.setOrgId(site.getOrgId());
            person.setSiteId(site.getId());
            person.setEmail(extra.email().toLowerCase());
            person.setDisplayName(extra.name() != null ? extra.name() : extra.email());
            person.setRole(extra.role() != null ? extra.role() : Role.TEACHER);
            person.setStatus(PersonStatus.ACTIVE);
            people.save(person);
            log.info("Added {} as {}", person.getEmail(), person.getRole());
        }
    }
}
