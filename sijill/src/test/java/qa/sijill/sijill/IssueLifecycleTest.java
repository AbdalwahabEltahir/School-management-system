package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import qa.sijill.sijill.audit.service.ActivityLogService;
import qa.sijill.sijill.files.model.FilePermission;
import qa.sijill.sijill.files.model.FileProfile;
import qa.sijill.sijill.files.model.SchoolFile;
import qa.sijill.sijill.files.repository.FilePermissionRepository;
import qa.sijill.sijill.files.repository.FileProfileRepository;
import qa.sijill.sijill.files.repository.SchoolFileRepository;
import qa.sijill.sijill.issues.model.ExceptionGrant;
import qa.sijill.sijill.issues.model.Issue;
import qa.sijill.sijill.issues.model.IssueStatus;
import qa.sijill.sijill.issues.repository.IssueRepository;
import qa.sijill.sijill.issues.service.ExceptionService;
import qa.sijill.sijill.issues.service.IssueService;
import qa.sijill.sijill.issues.service.IssueWorkflowService;
import qa.sijill.sijill.notifications.model.Message;
import qa.sijill.sijill.notifications.repository.MessageRepository;
import qa.sijill.sijill.notifications.repository.NotificationRepository;
import qa.sijill.sijill.organisation.model.Org;
import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.model.Subject;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.organisation.repository.SubjectRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.people.service.PeopleService;
import qa.sijill.sijill.rules.repository.RuleRepository;
import qa.sijill.sijill.rules.service.RuleEngineService;
import qa.sijill.sijill.setup.OrganisationSetupService;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.model.Sensitivity;
import qa.sijill.sijill.shared.model.Severity;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.shared.util.SchoolCalendar;
import qa.sijill.sijill.workspace.AccessLevel;
import qa.sijill.sijill.workspace.DriveType;
import qa.sijill.sijill.workspace.ShareTargetType;

/**
 * The core loop end to end, on a real PostgreSQL: a file breaks rules → issues open → people are told →
 * a re-check closes them; plus who may see and do what, and the tamper-evident log.
 *
 * <p>Each test builds its own organisation ("world") so tests never interfere with each other.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "sijill.jobs.enabled=false")
class IssueLifecycleTest {

    @Autowired OrganisationSetupService setup;
    @Autowired SiteRepository sites;
    @Autowired SubjectRepository subjects;
    @Autowired PersonRepository people;
    @Autowired SchoolFileRepository files;
    @Autowired FilePermissionRepository permissions;
    @Autowired FileProfileRepository profiles;
    @Autowired RuleRepository rules;
    @Autowired IssueRepository issues;
    @Autowired MessageRepository messages;
    @Autowired NotificationRepository notifications;
    @Autowired RuleEngineService engine;
    @Autowired IssueService issueService;
    @Autowired IssueWorkflowService workflow;
    @Autowired ExceptionService exceptionService;
    @Autowired PeopleService peopleService;
    @Autowired ActivityLogService activityLog;
    @Autowired JdbcTemplate jdbc;
    @Autowired qa.sijill.sijill.dashboard.service.ReportService reports;

    // ============================================================ the world

    /** One organisation with a school, a subject and one person per role. */
    private record World(Org org, Site school, Subject maths, Person teacher, Person coordinator, Person principal,
                         Person it, Person leader) {}

    private World world() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Org org = setup.createOrganisation("Network " + uid, "Head Office", "ho-" + uid + ".test",
                "leader@ho-" + uid + ".test", "Leader One");
        Person leader = people.findByEmailIgnoreCase("leader@ho-" + uid + ".test").orElseThrow();

        Site school = new Site();
        school.setOrgId(org.getId());
        school.setName("School A " + uid);
        school.setDomain("a-" + uid + ".test");
        sites.save(school);

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

    private Person person(Org org, Site site, String email, String name, Role role, Subject subject) {
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

    private static CurrentUser user(Person p) {
        return new CurrentUser(p.getId(), p.getOrgId(), p.getSiteId(), p.getRole(), p.getSubjectId(), p.getEmail(), p.getDisplayName());
    }

    /** A Restricted mark book owned by the teacher, in the maths folder (not "Student Records"), with a public link. */
    private SchoolFile exposedMarkBook(World w) {
        SchoolFile f = new SchoolFile();
        f.setOrgId(w.org().getId());
        f.setSiteId(w.school().getId());
        f.setWorkspaceFileId("wf-" + UUID.randomUUID());
        f.setName("Term 1 marks.xlsx");
        f.setOwnerEmail(w.teacher().getEmail());
        f.setDriveType(DriveType.SHARED);
        f.setFolderPath("Maths");
        f.setSubjectId(w.maths().getId());
        f.setSensitivity(Sensitivity.RESTRICTED);
        files.save(f);

        FileProfile profile = new FileProfile();
        profile.setOrgId(f.getOrgId());
        profile.setSiteId(f.getSiteId());
        profile.setFileId(f.getId());
        profile.setDocType("Mark book");
        profile.setSubjectId(w.maths().getId());
        profile.setSensitivity(Sensitivity.RESTRICTED);
        profile.setHasStudentData(true);
        profile.setConfidence(1.0);
        profiles.save(profile);

        FilePermission link = new FilePermission();
        link.setOrgId(f.getOrgId());
        link.setSiteId(f.getSiteId());
        link.setFileId(f.getId());
        link.setTargetType(ShareTargetType.ANYONE_WITH_LINK);
        link.setAccessLevel(AccessLevel.VIEWER);
        permissions.save(link);
        return f;
    }

    /** Issues of a file keyed by the code of the rule they broke (R2, R4, …). */
    private Map<String, Issue> issuesByRule(UUID fileId) {
        return issues.findByFileIdOrderByOpenedAtDesc(fileId).stream().collect(Collectors.toMap(
                i -> rules.findById(i.getRuleId()).orElseThrow().getCode(), Function.identity(), (a, b) -> a));
    }

    // ============================================================ the core loop

    @Test
    void aBrokenFileOpensIssuesTellsPeopleAndAReCheckClosesThem() {
        World w = world();
        SchoolFile file = exposedMarkBook(w);

        // 1. The check finds two broken rules: R2 (public link on a Restricted file) and R4 (not in Student Records).
        engine.evaluateFile(file.getId());
        Map<String, Issue> found = issuesByRule(file.getId());
        assertThat(found.keySet()).containsExactlyInAnyOrder("R2", "R4");

        Issue r2 = found.get("R2");
        assertThat(r2.getSeverity()).isEqualTo(Severity.URGENT);
        assertThat(r2.getStatus()).isEqualTo(IssueStatus.OWNER_NOTIFIED);
        assertThat(r2.getSubjectId()).isEqualTo(w.maths().getId());
        assertThat(found.get("R4").getSeverity()).isEqualTo(Severity.NORMAL);

        // 2. The owner is told by a logged message (Arabic first) and the coordinator is copied because it is urgent.
        List<Message> sent = messages.findByIssueIdOrderByCreatedAtAsc(r2.getId());
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getRecipientId()).isEqualTo(w.teacher().getId());
        assertThat(sent.get(0).getCopyToId()).isEqualTo(w.coordinator().getId());
        assertThat(sent.get(0).getBody()).contains("ما المشكلة").contains("لا يغيّر أي ملف");

        // 3. Bell entries: owner, coordinator (urgent only) and principal (every school notification).
        assertThat(notifications.findTop50ByRecipientIdOrderByCreatedAtDesc(w.teacher().getId())).isNotEmpty();
        assertThat(notifications.findTop50ByRecipientIdOrderByCreatedAtDesc(w.coordinator().getId())).isNotEmpty();
        assertThat(notifications.findTop50ByRecipientIdOrderByCreatedAtDesc(w.principal().getId())).hasSize(2);

        // 4. The owner "fixes" the file in the workspace; only a re-check closes the issues.
        permissions.deleteByFileId(file.getId());
        file.setFolderPath("Student Records/Grade 5");
        files.save(file);
        engine.evaluateFile(file.getId());

        Map<String, Issue> after = issuesByRule(file.getId());
        assertThat(after.get("R2").getStatus()).isEqualTo(IssueStatus.FIXED);
        assertThat(after.get("R4").getStatus()).isEqualTo(IssueStatus.FIXED);
        assertThat(after.get("R2").getClosedAt()).isNotNull();

        // 5. Every step is in the issue's history, and the tamper-evident log is intact.
        var history = issueService.history(user(w.teacher()), r2.getId());
        assertThat(history).extracting(e -> e.getEventType().name()).containsExactly("OPENED", "CLOSED");
        var verification = activityLog.verifyChain(user(w.leader()));
        assertThat(verification.valid()).isTrue();
        assertThat(verification.entriesChecked()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void pressingFixedDoesNotCloseAnIssueThatIsStillBroken() {
        World w = world();
        SchoolFile file = exposedMarkBook(w);
        engine.evaluateFile(file.getId());
        Issue r2 = issuesByRule(file.getId()).get("R2");

        var result = workflow.ownerMarksFixed(user(w.teacher()), r2.getId());

        assertThat(result.fixed()).isFalse();
        assertThat(result.stillWrongEn()).containsIgnoringCase("link");
        assertThat(issues.findById(r2.getId()).orElseThrow().getStatus()).isEqualTo(IssueStatus.OWNER_NOTIFIED);
    }

    @Test
    void anUncertainFileGoesToTheCoordinatorAndNeverToTheOwnerAsAGuess() {
        World w = world();
        SchoolFile file = exposedMarkBook(w);
        profiles.findByFileId(file.getId()).ifPresent(profiles::delete); // the classifier could not read it

        engine.evaluateFile(file.getId());

        Issue r2 = issuesByRule(file.getId()).get("R2");
        assertThat(r2.getStatus()).isEqualTo(IssueStatus.NEEDS_REVIEW);
        assertThat(notifications.findTop50ByRecipientIdOrderByCreatedAtDesc(w.teacher().getId())).isEmpty();
        assertThat(notifications.findTop50ByRecipientIdOrderByCreatedAtDesc(w.coordinator().getId())).isNotEmpty();
        assertThat(messages.findByIssueIdOrderByCreatedAtAsc(r2.getId())).isEmpty();
    }

    // ============================================================ who may see and do what

    @Test
    void peopleSeeOnlyWhatTheirRoleAllows() {
        World w = world();
        SchoolFile file = exposedMarkBook(w);
        engine.evaluateFile(file.getId());
        UUID issueId = issuesByRule(file.getId()).get("R2").getId();

        Person otherTeacher = person(w.org(), w.school(), "other@" + w.school().getDomain(), "Olga Other", Role.TEACHER, w.maths());
        Subject arabic = new Subject();
        arabic.setOrgId(w.org().getId());
        arabic.setSiteId(w.school().getId());
        arabic.setName("Arabic");
        subjects.save(arabic);
        Person arabicCoordinator = person(w.org(), w.school(), "arabic@" + w.school().getDomain(), "Amal Arabic", Role.COORDINATOR, arabic);

        assertThat(issueService.getVisible(user(w.teacher()), issueId)).isNotNull();       // the owner
        assertThat(issueService.getVisible(user(w.coordinator()), issueId)).isNotNull();   // her subject
        assertThat(issueService.getVisible(user(w.principal()), issueId)).isNotNull();     // her school

        // Another teacher, and a coordinator of another subject, are told the issue does not exist.
        assertThatThrownBy(() -> issueService.getVisible(user(otherTeacher), issueId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> issueService.getVisible(user(arabicCoordinator), issueId)).isInstanceOf(NotFoundException.class);

        // School IT and Leadership see counts only, never individual issues.
        assertThatThrownBy(() -> issueService.listFor(user(w.it()), IssueService.Filter.ALL)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> issueService.listFor(user(w.leader()), IssueService.Filter.ALL)).isInstanceOf(ForbiddenException.class);
        assertThat(issueService.counts(w.school().getId()).open()).isEqualTo(2);

        // A teacher's own list holds only their own issues.
        assertThat(issueService.listFor(user(otherTeacher), IssueService.Filter.ALL)).isEmpty();
        assertThat(issueService.listFor(user(w.teacher()), IssueService.Filter.ALL)).hasSize(2);
    }

    @Test
    void theOwnerDisagreesAndTheCoordinatorDecides() {
        World w = world();
        SchoolFile file = exposedMarkBook(w);
        engine.evaluateFile(file.getId());
        UUID issueId = issuesByRule(file.getId()).get("R4").getId();

        // A note is mandatory.
        assertThatThrownBy(() -> workflow.ownerDisagrees(user(w.teacher()), issueId, "  ")).isInstanceOf(BadRequestException.class);
        workflow.ownerDisagrees(user(w.teacher()), issueId, "This is the maths drive, not student records");
        assertThat(issues.findById(issueId).orElseThrow().getStatus()).isEqualTo(IssueStatus.OWNER_DISAGREES);

        // The owner cannot make the coordinator's decision; the coordinator can.
        assertThatThrownBy(() -> workflow.keepRule(user(w.teacher()), issueId)).isInstanceOf(ForbiddenException.class);
        workflow.keepRule(user(w.coordinator()), issueId);
        assertThat(issues.findById(issueId).orElseThrow().getStatus()).isEqualTo(IssueStatus.REMINDER_SENT);
    }

    @Test
    void anAllowedExceptionKeepsAFileQuietUntilItEndsThenTheOwnerIsToldAgain() {
        World w = world();
        SchoolFile file = exposedMarkBook(w);
        engine.evaluateFile(file.getId());
        UUID r4 = issuesByRule(file.getId()).get("R4").getId();

        // A reason is mandatory; only the subject coordinator may allow.
        LocalDate tomorrow = LocalDate.now(SchoolCalendar.ZONE).plusDays(1);
        assertThatThrownBy(() -> exceptionService.grant(user(w.coordinator()), r4, " ", tomorrow)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> exceptionService.grant(user(w.teacher()), r4, "because", tomorrow)).isInstanceOf(ForbiddenException.class);

        ExceptionGrant grant = exceptionService.grant(user(w.coordinator()), r4, "Moved at the end of term", tomorrow);
        assertThat(issues.findById(r4).orElseThrow().getStatus()).isEqualTo(IssueStatus.ALLOWED_EXCEPTION);

        // Still broken, but no new issue is raised while the exception is active.
        engine.evaluateFile(file.getId());
        assertThat(issues.findByFileIdOrderByOpenedAtDesc(file.getId()).stream().filter(Issue::isOpen)
                .map(i -> rules.findById(i.getRuleId()).orElseThrow().getCode())).doesNotContain("R4");

        // Ending the exception re-checks the file at once: the rule applies again and a new issue is opened.
        exceptionService.end(user(w.coordinator()), grant.getId());
        assertThat(issuesByRule(file.getId()).get("R4").isOpen()).isTrue();
    }

    @Test
    void aLeaverRaisesRuleR8AndWhenTheyHaveLeftTheirIssuesMoveToTheCoordinator() {
        World w = world();
        SchoolFile file = exposedMarkBook(w);
        file.setDriveType(DriveType.PERSONAL);
        files.save(file);
        permissions.deleteByFileId(file.getId());

        peopleService.update(user(w.it()), w.teacher().getId(),
                new PeopleService.UpdateCommand(null, PersonStatus.LEAVING, null, null));
        assertThat(issuesByRule(file.getId()).keySet()).contains("R8");

        peopleService.update(user(w.it()), w.teacher().getId(),
                new PeopleService.UpdateCommand(null, PersonStatus.LEFT, null, null));
        List<Issue> open = issues.findByFileIdOrderByOpenedAtDesc(file.getId()).stream().filter(Issue::isOpen).toList();
        assertThat(open).isNotEmpty().allSatisfy(i -> {
            assertThat(i.getOwnerId()).isNull();
            assertThat(i.getStatus()).isEqualTo(IssueStatus.NEEDS_REVIEW);
        });
    }

    // ============================================================ the weekly report

    @Test
    void leadershipAndPrincipalsCanDownloadTheWeeklyReportButOthersCannot() {
        World w = world();
        engine.evaluateFile(exposedMarkBook(w).getId()); // gives the school some open issues to report

        byte[] network = reports.weeklyReportPdf(user(w.leader()));
        byte[] school = reports.weeklyReportPdf(user(w.principal()));
        assertThat(new String(network, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(new String(school, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        assertThatThrownBy(() -> reports.weeklyReportPdf(user(w.teacher()))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> reports.weeklyReportPdf(user(w.coordinator()))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> reports.weeklyReportPdf(user(w.it()))).isInstanceOf(ForbiddenException.class);
    }

    // ============================================================ the log cannot be changed

    @Test
    void theDatabaseRefusesToChangeOrDeleteTheActivityLog() {
        World w = world();
        exposedMarkBook(w);
        engine.evaluateFile(files.findAll().stream().filter(f -> f.getOrgId().equals(w.org().getId())).findFirst().orElseThrow().getId());

        assertThatThrownBy(() -> jdbc.update("update activity_log set action = 'FORGED' where org_id = ?", w.org().getId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from activity_log where org_id = ?", w.org().getId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThat(activityLog.verifyChain(user(w.leader())).valid()).isTrue();
    }
}
