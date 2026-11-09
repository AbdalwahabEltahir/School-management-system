package qa.sijill.sijill.setup;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import qa.sijill.sijill.organisation.repository.OrgRepository;

/**
 * First start-up: if no organisation exists yet, creates one from configuration so the first
 * Leadership person can sign in. Does nothing once an organisation exists, and nothing at all unless
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

    @Value("${sijill.bootstrap.org-name}") private String orgName;
    @Value("${sijill.bootstrap.head-office-name:Head Office}") private String headOfficeName;
    @Value("${sijill.bootstrap.head-office-domain}") private String headOfficeDomain;
    @Value("${sijill.bootstrap.admin-email}") private String adminEmail;
    @Value("${sijill.bootstrap.admin-name:Administrator}") private String adminName;

    @Override
    public void run(ApplicationArguments args) {
        if (orgs.count() > 0) {
            return;
        }
        setup.createOrganisation(orgName, headOfficeName, headOfficeDomain, adminEmail, adminName);
        log.info("Created organisation '{}' with first Leadership person {}", orgName, adminEmail);
    }
}
