package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import qa.sijill.sijill.files.model.SchoolFile;
import qa.sijill.sijill.issues.model.Incident;
import qa.sijill.sijill.issues.model.IncidentType;
import qa.sijill.sijill.issues.model.Issue;
import qa.sijill.sijill.issues.repository.IncidentRepository;
import qa.sijill.sijill.issues.repository.IssueRepository;
import qa.sijill.sijill.issues.service.IncidentService;
import qa.sijill.sijill.notifications.repository.MessageRepository;
import qa.sijill.sijill.rules.repository.RuleRepository;
import qa.sijill.sijill.rules.service.RuleEngineService;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Sensitivity;
import qa.sijill.sijill.shared.model.Severity;
import qa.sijill.sijill.workspace.DriveType;
import qa.sijill.sijill.workspace.ShareTargetType;

/**
 * Who is told what, for each kind of file and each kind of problem (SRS FR-4.2 table). Severity comes from the
 * rule that is broken, and which rules apply comes from the file's type and sensitivity, so this test walks
 * the realistic combinations and checks the owner, the coordinator, the principal and leadership each time.
 *
 * <p>Leadership hears about every urgent issue (an exam leak or important deletion through its incident, once);
 * otherwise only about major incidents.
 */
class NotificationMatrixTest extends WorldTestSupport {

    @Autowired RuleEngineService engine;
    @Autowired IssueRepository issues;
    @Autowired RuleRepository rules;
    @Autowired IncidentRepository incidents;
    @Autowired IncidentService incidentService;
    @Autowired MessageRepository messages;

    private static final String RECORDS = "Student Records/Grade 7";

    private Map<String, Issue> issuesOf(SchoolFile f) {
        return issues.findByFileIdOrderByOpenedAtDesc(f.getId()).stream().collect(Collectors.toMap(
                i -> rules.findById(i.getRuleId()).orElseThrow().getCode(), Function.identity(), (a, b) -> a));
    }

    // ------------------------------------------------------------------ urgent, Restricted student or health data

    @Test
    void publicLinkOnAMarkBookIsUrgentAndAlertsEveryoneIncludingLeadership() {
        World w = world();
        SchoolFile f = file(w, "Term 1 marks.xlsx", new Profile("Mark book", Sensitivity.RESTRICTED, true, false),
                DriveType.SHARED, RECORDS);
        share(f, ShareTargetType.ANYONE_WITH_LINK);

        engine.evaluateFile(f.getId());

        Map<String, Issue> found = issuesOf(f);
        assertThat(found.keySet()).containsExactly("R2");
        assertThat(found.get("R2").getSeverity()).isEqualTo(Severity.URGENT);
        assertThat(count(w.teacher(), NotificationKind.ISSUE)).isEqualTo(1);
        assertThat(count(w.coordinator(), NotificationKind.ISSUE)).isEqualTo(1);
        assertThat(count(w.principal(), NotificationKind.ISSUE)).isEqualTo(1);
        assertThat(count(w.leader(), NotificationKind.INCIDENT)).isEqualTo(1);
        assertThat(messages.findByIssueIdOrderByCreatedAtAsc(found.get("R2").getId()))
                .singleElement().satisfies(m -> assertThat(m.getCopyToId()).isEqualTo(w.coordinator().getId()));
    }

    @Test
    void outsideSharingOfAContactListIsUrgentAndAlertsLeadership() {
        World w = world();
        // the classifier marks every student-record type as student data, even without the word "student" in the text
        SchoolFile f = file(w, "Parent contacts.docx", new Profile("Contact list", Sensitivity.RESTRICTED, true, false),
                DriveType.SHARED, RECORDS);
        share(f, ShareTargetType.EXTERNAL_USER);

        engine.evaluateFile(f.getId());

        assertThat(issuesOf(f).keySet()).containsExactly("R3");
        assertThat(issuesOf(f).get("R3").getSeverity()).isEqualTo(Severity.URGENT);
        assertThat(count(w.leader(), NotificationKind.INCIDENT)).isEqualTo(1);
    }

    @Test
    void healthDataAloneIsEnoughToAlertLeadership() {
        World w = world();
        SchoolFile f = file(w, "Allergies.xlsx", new Profile("Medical record", Sensitivity.RESTRICTED, false, true),
                DriveType.SHARED, RECORDS);
        share(f, ShareTargetType.ANYONE_WITH_LINK);

        engine.evaluateFile(f.getId());

        assertThat(issuesOf(f).get("R2").getSeverity()).isEqualTo(Severity.URGENT);
        assertThat(count(w.leader(), NotificationKind.INCIDENT)).isEqualTo(1);
    }

    @Test
    void aRestrictedFileWithoutPersonalDataIsUrgentAndLeadershipIsAlertedToo() {
        World w = world();
        SchoolFile f = file(w, "Budget.xlsx", new Profile("Other", Sensitivity.RESTRICTED, false, false),
                DriveType.SHARED, "Admin");
        share(f, ShareTargetType.ANYONE_WITH_LINK);

        engine.evaluateFile(f.getId());

        assertThat(issuesOf(f).get("R2").getSeverity()).isEqualTo(Severity.URGENT);
        assertThat(count(w.teacher(), NotificationKind.ISSUE)).isEqualTo(1);
        assertThat(count(w.coordinator(), NotificationKind.ISSUE)).isEqualTo(1);
        // every urgent issue reaches Leadership, by school and rule, never by file name
        assertThat(count(w.leader(), NotificationKind.INCIDENT)).isEqualTo(1);
        assertThat(total(w.leader())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ rules that do not apply to the file type

    @Test
    void aPublicLinkOnAnInternalPolicyRaisesNothingAndTellsNobody() {
        World w = world();
        SchoolFile f = file(w, "Attendance policy.docx", new Profile("Policy", Sensitivity.INTERNAL, false, false),
                DriveType.SHARED, "Policies");
        share(f, ShareTargetType.ANYONE_WITH_LINK);

        engine.evaluateFile(f.getId());

        assertThat(issuesOf(f)).isEmpty();
        assertThat(total(w.teacher()) + total(w.coordinator()) + total(w.principal()) + total(w.leader())).isZero();
    }

    @Test
    void anOutsideShareOfAConfidentialExamWithoutAnExamDateIsNotRestrictedSoR3StaysQuiet() {
        World w = world();
        SchoolFile f = file(w, "Maths exam.docx", new Profile("Exam", Sensitivity.CONFIDENTIAL, false, false),
                DriveType.SHARED, "Exams");
        share(f, ShareTargetType.EXTERNAL_USER);

        engine.evaluateFile(f.getId());

        assertThat(issuesOf(f).keySet()).doesNotContain("R3", "R6"); // R3 is Restricted only; R6 needs the exam date
    }

    // ------------------------------------------------------------------ normal (not urgent)

    @Test
    void aSchoolFileInAPersonalDriveIsNormalSoOnlyTheOwnerAndThePrincipalHearAboutIt() {
        World w = world();
        SchoolFile f = file(w, "Fractions worksheet.docx", new Profile("Worksheet", Sensitivity.INTERNAL, false, false),
                DriveType.PERSONAL, "");

        engine.evaluateFile(f.getId());

        Map<String, Issue> found = issuesOf(f);
        assertThat(found.keySet()).containsExactly("R1");
        assertThat(found.get("R1").getSeverity()).isEqualTo(Severity.NORMAL);
        assertThat(count(w.teacher(), NotificationKind.ISSUE)).isEqualTo(1);
        assertThat(total(w.coordinator())).as("normal issues only appear in her list, not as a push").isZero();
        assertThat(count(w.principal(), NotificationKind.ISSUE)).isEqualTo(1);
        assertThat(total(w.leader())).isZero();
        assertThat(messages.findByIssueIdOrderByCreatedAtAsc(found.get("R1").getId()))
                .singleElement().satisfies(m -> assertThat(m.getCopyToId()).isNull());
    }

    @Test
    void aMarkBookOutsideStudentRecordsIsNormalWhenNotShared() {
        World w = world();
        SchoolFile f = file(w, "Marks.xlsx", new Profile("Mark book", Sensitivity.RESTRICTED, true, false),
                DriveType.SHARED, "Maths");

        engine.evaluateFile(f.getId());

        assertThat(issuesOf(f).keySet()).contains("R4");
        assertThat(issuesOf(f).get("R4").getSeverity()).isEqualTo(Severity.NORMAL);
        assertThat(total(w.leader())).isZero();
    }

    // ------------------------------------------------------------------ major incidents

    @Test
    void anExamSharedBeforeItsDateIsAnExamLeakAlertingOwnerCoordinatorPrincipalAndLeadership() {
        World w = world();
        SchoolFile f = file(w, "Grade 9 exam.pdf", new Profile("Exam", Sensitivity.CONFIDENTIAL, false, false),
                DriveType.SHARED, "Exams");
        examOn(f, LocalDate.now().plusDays(7));
        share(f, ShareTargetType.ANYONE_WITH_LINK);

        engine.evaluateFile(f.getId());

        Map<String, Issue> found = issuesOf(f);
        assertThat(found.keySet()).containsExactlyInAnyOrder("R2", "R6");
        assertThat(found.get("R6").getSeverity()).isEqualTo(Severity.URGENT);
        List<Incident> raised = incidents.findBySiteIdOrderByDetectedAtDesc(w.school().getId());
        assertThat(raised).extracting(Incident::getType).containsExactly(IncidentType.EXAM_LEAK);
        for (var person : List.of(w.teacher(), w.coordinator(), w.principal())) {
            assertThat(count(person, NotificationKind.INCIDENT)).as(person.getRole().name()).isEqualTo(1);
        }
        // the public link breaks two rules: the exam leak (an incident) and R2 (an urgent issue), so Leadership hears both
        assertThat(count(w.leader(), NotificationKind.INCIDENT)).isEqualTo(2);
        assertThat(total(w.leader())).isEqualTo(2);
    }

    @Test
    void anExamAfterItsDateIsNoLongerALeak() {
        World w = world();
        SchoolFile f = file(w, "Old exam.pdf", new Profile("Exam", Sensitivity.CONFIDENTIAL, false, false),
                DriveType.SHARED, "Exams");
        examOn(f, LocalDate.now().minusDays(1));
        share(f, ShareTargetType.ANYONE_WITH_LINK);

        engine.evaluateFile(f.getId());

        assertThat(issuesOf(f).keySet()).containsExactly("R2"); // public link on Confidential, but no exam leak
        assertThat(incidents.findBySiteIdOrderByDetectedAtDesc(w.school().getId())).isEmpty();
    }

    @Test
    void deletingAPolicyIsAnImportantDeletionAlertingEveryone() {
        World w = world();
        SchoolFile f = file(w, "Assessment policy.docx", new Profile("Policy", Sensitivity.INTERNAL, false, false),
                DriveType.SHARED, "Policies");
        f.setTrashedAt(java.time.Instant.now());
        files.save(f);

        engine.evaluateFile(f.getId());

        assertThat(issuesOf(f).get("R10").getSeverity()).isEqualTo(Severity.URGENT);
        assertThat(incidents.findBySiteIdOrderByDetectedAtDesc(w.school().getId()))
                .extracting(Incident::getType).containsExactly(IncidentType.IMPORTANT_DELETION);
        for (var person : List.of(w.teacher(), w.coordinator(), w.principal(), w.leader())) {
            assertThat(count(person, NotificationKind.INCIDENT)).as(person.getRole().name()).isEqualTo(1);
        }
    }

    @Test
    void aLostConnectionAlertsPrincipalLeadershipAndSchoolItButNotTeachersOrCoordinators() {
        World w = world();

        incidentService.connectionLost(w.org().getId(), w.school().getId());

        assertThat(count(w.principal(), NotificationKind.INCIDENT)).isEqualTo(1);
        assertThat(count(w.leader(), NotificationKind.INCIDENT)).isEqualTo(1);
        assertThat(count(w.it(), NotificationKind.INCIDENT)).isEqualTo(1);
        assertThat(total(w.teacher())).isZero();
        assertThat(total(w.coordinator())).isZero();
    }

    @Test
    void anUrgentIssueStillOpenAfterADayAlertsPrincipalAndLeadershipOnly() {
        World w = world();
        SchoolFile f = file(w, "Marks.xlsx", new Profile("Mark book", Sensitivity.RESTRICTED, true, false),
                DriveType.SHARED, RECORDS);
        share(f, ShareTargetType.ANYONE_WITH_LINK);
        engine.evaluateFile(f.getId());
        Issue r2 = issuesOf(f).get("R2");
        long teacherBefore = total(w.teacher());
        long coordinatorBefore = total(w.coordinator());

        incidentService.urgentUnresolved(r2);

        assertThat(incidents.findBySiteIdOrderByDetectedAtDesc(w.school().getId()))
                .extracting(Incident::getType).contains(IncidentType.URGENT_UNRESOLVED);
        assertThat(total(w.teacher())).as("the owner gets the reminder, not an incident alert").isEqualTo(teacherBefore);
        assertThat(total(w.coordinator())).as("the coordinator gets the follow-up, not an incident alert")
                .isEqualTo(coordinatorBefore);
        assertThat(count(w.principal(), NotificationKind.INCIDENT)).isEqualTo(1);
        assertThat(count(w.leader(), NotificationKind.INCIDENT)).isEqualTo(2); // the exposed-Restricted alert and this one
    }
}
