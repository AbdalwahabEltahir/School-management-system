package qa.hafez.hafez.setup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.organisation.model.DocType;
import qa.hafez.hafez.organisation.model.Org;
import qa.hafez.hafez.organisation.model.Site;
import qa.hafez.hafez.organisation.repository.DocTypeRepository;
import qa.hafez.hafez.organisation.repository.OrgRepository;
import qa.hafez.hafez.organisation.repository.SiteRepository;
import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.people.model.PersonStatus;
import qa.hafez.hafez.people.repository.PersonRepository;
import qa.hafez.hafez.rules.service.RuleService;
import qa.hafez.hafez.rules.service.StarterRules;
import qa.hafez.hafez.shared.model.Role;

/**
 * Creates a new organisation with everything Phase 1 needs to start (proposal 11.1, stage 1):
 * <ol>
 *   <li>the organisation and its head-office site;</li>
 *   <li>the first Leadership person, so somebody can sign in and set up the rest;</li>
 *   <li>the 14 document types with their default sensitivity (FR-8.4);</li>
 *   <li>the ten starter rules R1 – R10, approved and active (section 5.3).</li>
 * </ol>
 * There is no sign-up screen (design principle: no Hafez accounts), so this is the only way the very
 * first person gets in. It is run once at start-up by {@link BootstrapRunner}.
 */
@Service
@RequiredArgsConstructor
public class OrganisationSetupService {

    private final OrgRepository orgs;
    private final SiteRepository sites;
    private final PersonRepository people;
    private final DocTypeRepository docTypes;
    private final RuleService ruleService;

    @Transactional
    public Org createOrganisation(String orgName, String headOfficeName, String headOfficeDomain,
                                  String adminEmail, String adminName) {
        Org org = new Org();
        org.setName(orgName);
        orgs.save(org);

        Site headOffice = new Site();
        headOffice.setOrgId(org.getId());
        headOffice.setName(headOfficeName);
        headOffice.setDomain(headOfficeDomain.toLowerCase());
        headOffice.setHeadOffice(false);   // the first workspace is simply a school
        headOffice.setScored(true);
        sites.save(headOffice);

        Person admin = new Person();
        admin.setOrgId(org.getId());
        admin.setSiteId(headOffice.getId());
        admin.setEmail(adminEmail.toLowerCase());
        admin.setDisplayName(adminName);
        admin.setRole(Role.LEADERSHIP);
        admin.setStatus(PersonStatus.ACTIVE);
        people.save(admin);

        for (StarterRules.DocTypeSeed seed : StarterRules.DOC_TYPES) {
            DocType type = new DocType();
            type.setOrgId(org.getId());
            type.setName(seed.name());
            type.setDefaultSensitivity(seed.sensitivity());
            docTypes.save(type);
        }
        for (StarterRules.Starter starter : StarterRules.all()) {
            ruleService.createStarterRule(org.getId(), starter.code(), starter.draft());
        }
        return org;
    }
}
