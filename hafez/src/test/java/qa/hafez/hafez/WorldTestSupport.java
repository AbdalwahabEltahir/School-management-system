package qa.hafez.hafez;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import qa.hafez.hafez.files.model.FilePermission;
import qa.hafez.hafez.files.model.FileProfile;
import qa.hafez.hafez.files.model.SchoolFile;
import qa.hafez.hafez.files.repository.FilePermissionRepository;
import qa.hafez.hafez.files.repository.FileProfileRepository;
import qa.hafez.hafez.files.repository.SchoolFileRepository;
import qa.hafez.hafez.notifications.model.Notification;
import qa.hafez.hafez.notifications.repository.NotificationRepository;
import qa.hafez.hafez.organisation.model.Org;
import qa.hafez.hafez.organisation.model.Site;
import qa.hafez.hafez.organisation.model.Subject;
import qa.hafez.hafez.organisation.repository.SiteRepository;
import qa.hafez.hafez.organisation.repository.SubjectRepository;
import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.people.model.PersonStatus;
import qa.hafez.hafez.people.repository.PersonRepository;
import qa.hafez.hafez.setup.OrganisationSetupService;
import qa.hafez.hafez.shared.model.NotificationKind;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.model.Sensitivity;
import qa.hafez.hafez.shared.security.CurrentUser;
import qa.hafez.hafez.workspace.AccessLevel;
import qa.hafez.hafez.workspace.DriveType;
import qa.hafez.hafez.workspace.ShareTargetType;

/**
 * Shared set-up for integration tests: a real PostgreSQL, and a fresh organisation ("world") per test with a
 * school and one person of each role, so tests never see each other's data.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "hafez.jobs.enabled=false")
abstract class WorldTestSupport {

    @Autowired protected OrganisationSetupService setup;
    @Autowired protected SiteRepository sites;
    @Autowired protected SubjectRepository subjects;
    @Autowired protected PersonRepository people;
    @Autowired protected SchoolFileRepository files;
    @Autowired protected FilePermissionRepository permissions;
    @Autowired protected FileProfileRepository profiles;
    @Autowired protected NotificationRepository notifications;

    /** One organisation with a school, a subject and one person per role. */
    protected record World(Org org, Site school, Subject maths, Person teacher, Person coordinator, Person principal,
                           Person it, Person leader) {}

    protected World world() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Org org = setup.createOrganisation("Network " + uid, "Head Office", "ho-" + uid + ".test",
                "leader@ho-" + uid + ".test", "Leader One");
        Person leader = people.findByEmailIgnoreCase("leader@ho-" + uid + ".test").orElseThrow();
        Site school = newSchool(org, "School A " + uid, "a-" + uid + ".test");

        Subject maths = new Subject();
        maths.setOrgId(org.getId());
        maths.setSiteId(school.getId());
        maths.setName("Mathematics");
        subjects.save(maths);

        String domain = school.getDomain();
        Person teacher = person(org, school, "teacher@" + domain, "Tariq Teacher", Role.TEACHER, maths);
        Person coordinator = person(org, school, "coord@" + domain, "Carla Coordinator", Role.COORDINATOR, maths);
        Person principal = person(org, school, "principal@" + domain, "Pia Principal", Role.PRINCIPAL, null);
        Person it = person(org, school, "it@" + domain, "Ivan IT", Role.SCHOOL_IT, null);

        maths.setCoordinatorId(coordinator.getId());
        subjects.save(maths);
        school.setPrincipalId(principal.getId());
        school.setCoordinatorId(coordinator.getId());
        sites.save(school);
        return new World(org, school, maths, teacher, coordinator, principal, it, leader);
    }

    protected Site newSchool(Org org, String name, String domain) {
        Site school = new Site();
        school.setOrgId(org.getId());
        school.setName(name);
        school.setDomain(domain);
        return sites.save(school);
    }

    protected Person person(Org org, Site site, String email, String name, Role role, Subject subject) {
        Person p = new Person();
        p.setOrgId(org.getId());
        p.setSiteId(site.getId());
        p.setEmail(email);
        p.setDisplayName(name);
        p.setRole(role);
        p.setStatus(PersonStatus.ACTIVE);
        p.setSubjectId(subject == null ? null : subject.getId());
        return people.save(p);
    }

    protected static CurrentUser user(Person p) {
        return new CurrentUser(p.getId(), p.getOrgId(), p.getSiteId(), p.getRole(), p.getSubjectId(), p.getEmail(),
                p.getDisplayName());
    }

    /** What the classifier would have stored for a file: its type and sensitivity and the personal-data flags. */
    protected record Profile(String docType, Sensitivity sensitivity, boolean student, boolean health) {}

    protected SchoolFile file(World w, String name, Profile profile, DriveType drive, String folder) {
        SchoolFile f = new SchoolFile();
        f.setOrgId(w.org().getId());
        f.setSiteId(w.school().getId());
        f.setWorkspaceFileId("wf-" + UUID.randomUUID());
        f.setName(name);
        f.setOwnerEmail(w.teacher().getEmail());
        f.setDriveType(drive);
        f.setFolderPath(folder);
        f.setSubjectId(w.maths().getId());
        f.setSensitivity(profile.sensitivity());
        files.save(f);

        FileProfile p = new FileProfile();
        p.setOrgId(f.getOrgId());
        p.setSiteId(f.getSiteId());
        p.setFileId(f.getId());
        p.setDocType(profile.docType());
        p.setSubjectId(w.maths().getId());
        p.setSensitivity(profile.sensitivity());
        p.setHasStudentData(profile.student());
        p.setHasHealthData(profile.health());
        p.setConfidence(1.0);
        profiles.save(p);
        return f;
    }

    protected void share(SchoolFile f, ShareTargetType type) {
        FilePermission perm = new FilePermission();
        perm.setOrgId(f.getOrgId());
        perm.setSiteId(f.getSiteId());
        perm.setFileId(f.getId());
        perm.setGrantee(type == ShareTargetType.EXTERNAL_USER ? "outsider@example.com" : null);
        perm.setTargetType(type);
        perm.setAccessLevel(AccessLevel.VIEWER);
        perm.setExternal(type == ShareTargetType.EXTERNAL_USER);
        permissions.save(perm);
    }

    protected void examOn(SchoolFile f, LocalDate date) {
        f.setExamDate(date);
        files.save(f);
    }

    /** How many notifications of a kind a person has. */
    protected long count(Person p, NotificationKind kind) {
        List<Notification> all = notifications.findTop50ByRecipientIdOrderByCreatedAtDesc(p.getId());
        return all.stream().filter(n -> n.getKind() == kind).count();
    }

    protected long total(Person p) {
        return notifications.findTop50ByRecipientIdOrderByCreatedAtDesc(p.getId()).size();
    }
}
