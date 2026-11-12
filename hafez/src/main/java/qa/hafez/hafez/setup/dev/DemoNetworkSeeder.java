package qa.hafez.hafez.setup.dev;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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

import qa.hafez.hafez.dashboard.model.HealthScoreSnapshot;
import qa.hafez.hafez.dashboard.repository.HealthScoreRepository;
import qa.hafez.hafez.dashboard.service.ScoreService;
import qa.hafez.hafez.files.model.FilePermission;
import qa.hafez.hafez.files.model.FileProfile;
import qa.hafez.hafez.files.model.SchoolFile;
import qa.hafez.hafez.files.repository.FilePermissionRepository;
import qa.hafez.hafez.files.repository.FileProfileRepository;
import qa.hafez.hafez.files.repository.SchoolFileRepository;
import qa.hafez.hafez.organisation.model.Org;
import qa.hafez.hafez.organisation.model.Site;
import qa.hafez.hafez.organisation.model.Subject;
import qa.hafez.hafez.organisation.repository.OrgRepository;
import qa.hafez.hafez.organisation.repository.SiteRepository;
import qa.hafez.hafez.organisation.repository.SubjectRepository;
import qa.hafez.hafez.rules.service.RuleEngineService;
import qa.hafez.hafez.shared.model.Sensitivity;
import qa.hafez.hafez.workspace.AccessLevel;
import qa.hafez.hafez.workspace.DriveType;
import qa.hafez.hafez.workspace.ShareTargetType;

/**
 * <b>Demo only.</b> Adds a few invented schools (C, D, E, F) to the network so the leadership dashboard has a realistic
 * spread of Good, Watch and Needs-attention schools, plus a week of history for the trend. Off unless
 * {@code hafez.demo-network=true} (env {@code HAFEZ_DEMO_NETWORK=true}).
 *
 * <p>The files are invented and checked by the real rule engine, so the issues, scores and urgent list are genuine
 * results, not hard-coded numbers. A school that already has files is left alone, so running this twice is safe.
 * The demo schools have their Drive connection switched off so the nightly check never tries to read them.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "hafez.demo-network", havingValue = "true")
@RequiredArgsConstructor
public class DemoNetworkSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoNetworkSeeder.class);

    /** name, domain, clean files, personal-drive contact lists (2 issues each), public-link mark books (3 issues, 1 urgent), trend direction. */
    private record Plan(String name, String domain, int clean, int personal, int publicLink, boolean improving) {}

    private static final Plan[] PLANS = {
            new Plan("School C", "school-c.eaa.test", 24, 1, 0, true),      // Good
            new Plan("School D", "school-d.eaa.test", 20, 3, 0, false),     // Watch
            new Plan("School E", "school-e.eaa.test", 14, 1, 2, true),      // Needs attention, getting better
            new Plan("School F", "school-f.eaa.test", 30, 0, 0, true),      // Good, nothing wrong
    };

    private final OrgRepository orgs;
    private final SiteRepository sites;
    private final SubjectRepository subjects;
    private final SchoolFileRepository files;
    private final FileProfileRepository profiles;
    private final FilePermissionRepository permissions;
    private final HealthScoreRepository snapshots;
    private final RuleEngineService engine;
    private final ScoreService scores;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Org org = orgs.findAll().stream().findFirst().orElse(null);
        if (org == null) {
            log.info("Demo network skipped: no organisation yet.");
            return;
        }
        for (Plan plan : PLANS) {
            Site site = sites.findByOrgIdOrderByName(org.getId()).stream()
                    .filter(s -> s.getName().equalsIgnoreCase(plan.name())).findFirst().orElseGet(() -> newSite(org, plan));
            if (files.countBySiteIdAndTrashedAtIsNull(site.getId()) > 0) {
                continue; // already has data
            }
            site.setScored(true);
            site.setActive(true);
            site.setChecksEnabled(false);
            site.setFirstCheckAt(Instant.now(clock));
            sites.save(site);
            seed(org, site, plan);
            log.info("Demo network: {} added.", plan.name());
        }
    }

    private Site newSite(Org org, Plan plan) {
        Site s = new Site();
        s.setOrgId(org.getId());
        s.setName(plan.name());
        s.setDomain(plan.domain());
        return sites.save(s);
    }

    private void seed(Org org, Site site, Plan plan) {
        Subject subject = new Subject();
        subject.setOrgId(org.getId());
        subject.setSiteId(site.getId());
        subject.setName("General");
        subjects.save(subject);

        String owner = "teacher@" + site.getDomain();
        UUID[] toCheck = new UUID[plan.clean() + plan.personal() + plan.publicLink()];
        int n = 0;
        for (int i = 1; i <= plan.clean(); i++) {
            toCheck[n++] = file(site, subject, owner, "Lesson plan " + i + ".docx", "Academic/General", DriveType.SHARED, "Policy",
                    Sensitivity.INTERNAL, false).getId();
        }
        for (int i = 1; i <= plan.personal(); i++) {
            toCheck[n++] = file(site, subject, owner, "Parent contacts " + i + ".xlsx", null, DriveType.PERSONAL, "Contact list",
                    Sensitivity.RESTRICTED, true).getId();
        }
        for (int i = 1; i <= plan.publicLink(); i++) {
            SchoolFile f = file(site, subject, owner, "Class marks " + i + ".xlsx", "General", DriveType.SHARED, "Mark book",
                    Sensitivity.RESTRICTED, true);
            grant(f);
            toCheck[n++] = f.getId();
        }
        for (UUID id : toCheck) {
            engine.evaluateFile(id);
        }

        scores.snapshotToday(site);
        history(site, plan.improving());
    }

    /** Six earlier days, so the trend line has a shape: older days had a few more (or fewer) open issues. */
    private void history(Site site, boolean improving) {
        LocalDate today = LocalDate.now(clock);
        HealthScoreSnapshot now = snapshots.findBySiteIdAndScoreDate(site.getId(), today).orElse(null);
        if (now == null) {
            return;
        }
        for (int d = 1; d <= 6; d++) {
            int wobble = d / 2 + (d % 2);                       // 1,1,2,2,3,3
            int normal = Math.max(0, now.getOpenNormal() + (improving ? wobble : -wobble));
            int urgent = Math.max(0, now.getOpenUrgent() + (improving && d > 3 ? 1 : 0));
            HealthScoreSnapshot s = new HealthScoreSnapshot();
            s.setOrgId(site.getOrgId());
            s.setSiteId(site.getId());
            s.setScoreDate(today.minusDays(d));
            s.setFilesChecked(now.getFilesChecked());
            s.setOpenNormal(normal);
            s.setOpenUrgent(urgent);
            s.setScore(ScoreService.calculate(normal, urgent, now.getFilesChecked()));
            snapshots.save(s);
        }
    }

    private SchoolFile file(Site site, Subject subject, String owner, String name, String folder, DriveType drive,
                            String docType, Sensitivity sensitivity, boolean studentData) {
        SchoolFile f = new SchoolFile();
        f.setOrgId(site.getOrgId());
        f.setSiteId(site.getId());
        f.setWorkspaceFileId("demo-" + UUID.randomUUID());
        f.setName(name);
        f.setOwnerEmail(owner);
        f.setDriveType(drive);
        f.setFolderPath(folder);
        f.setSubjectId(subject.getId());
        f.setSensitivity(sensitivity);
        files.save(f);

        FileProfile p = new FileProfile();
        p.setOrgId(f.getOrgId());
        p.setSiteId(f.getSiteId());
        p.setFileId(f.getId());
        p.setDocType(docType);
        p.setSubjectId(subject.getId());
        p.setSensitivity(sensitivity);
        p.setHasStudentData(studentData);
        p.setConfidence(0.96);
        p.setModelVersion("demo-seed");
        p.getEvidence().add("Invented demo file");
        profiles.save(p);
        return f;
    }

    private void grant(SchoolFile file) {
        FilePermission p = new FilePermission();
        p.setOrgId(file.getOrgId());
        p.setSiteId(file.getSiteId());
        p.setFileId(file.getId());
        p.setTargetType(ShareTargetType.ANYONE_WITH_LINK);
        p.setAccessLevel(AccessLevel.VIEWER);
        p.setExternal(false);
        permissions.save(p);
    }
}
