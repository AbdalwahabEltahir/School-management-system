package qa.sijill.sijill.setup.dev;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.organisation.model.Org;
import qa.sijill.sijill.organisation.repository.OrgRepository;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.shared.model.Role;

/**
 * <b>Demo only.</b> Makes sure the network has exactly five demonstration schools (A to E, see {@link DemoSchools}),
 * each with about four months of history. Off unless {@code sijill.demo-network=true} (env {@code SIJILL_DEMO_NETWORK=true}).
 *
 * <p>Schools already in the database count towards the five, in the order they were created: a database that already
 * holds two schools gets C, D and E added; an empty one (the dev seed adds school A first) gets B to E. A database
 * that already holds five or more schools is left alone, so running this twice is safe and real schools are never touched.
 * The demonstration schools have their Drive connection switched off so the nightly check never tries to read them.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "sijill.demo-network", havingValue = "true")
@RequiredArgsConstructor
public class DemoNetworkSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoNetworkSeeder.class);

    private final OrgRepository orgs;
    private final SiteRepository sites;
    private final PersonRepository people;
    private final DemoSchoolBuilder builder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Org org = orgs.findAll().stream().findFirst().orElse(null);
        if (org == null) {
            log.info("Demo network skipped: no organisation yet.");
            return;
        }
        seed(org);
    }

    /** Adds whichever of the five demonstration schools are missing from this organisation. */
    @Transactional
    public void seed(Org org) {
        int existing = (int) sites.findByOrgIdOrderByName(org.getId()).stream().filter(s -> !s.isHeadOffice()).count();
        List<Person> admins = people.findByOrgIdAndRole(org.getId(), Role.EAA_ADMIN).stream()
                .filter(p -> p.getStatus() != PersonStatus.LEFT).toList();
        for (int i = existing; i < DemoSchools.ALL.size(); i++) {
            DemoSchools.Profile profile = DemoSchools.ALL.get(i);
            if (sites.findByDomainIgnoreCase(profile.domain()).isPresent()) {
                continue;
            }
            builder.build(org, profile, admins);
        }
    }
}
