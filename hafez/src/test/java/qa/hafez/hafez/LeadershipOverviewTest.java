package qa.hafez.hafez;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import qa.hafez.hafez.dashboard.service.ScoreService;
import qa.hafez.hafez.dashboard.service.ScoreService.NetworkOverview;
import qa.hafez.hafez.files.model.SchoolFile;
import qa.hafez.hafez.issues.model.Issue;
import qa.hafez.hafez.issues.model.IssueStatus;
import qa.hafez.hafez.issues.repository.IssueRepository;
import qa.hafez.hafez.organisation.model.Site;
import qa.hafez.hafez.organisation.service.SiteService;
import qa.hafez.hafez.rules.repository.RuleRepository;
import qa.hafez.hafez.rules.service.RuleEngineService;
import qa.hafez.hafez.shared.exception.ConflictException;
import qa.hafez.hafez.shared.exception.ForbiddenException;
import qa.hafez.hafez.shared.model.Sensitivity;
import qa.hafez.hafez.workspace.DriveType;
import qa.hafez.hafez.workspace.ShareTargetType;

/**
 * The Leadership overview: the six tiles and the schools table must add up to what the checks found, per
 * school and for the network, and follow the issues as they are fixed.
 */
@TestPropertySource(properties = "hafez.scoring.allow-head-office=true")
class LeadershipOverviewTest extends WorldTestSupport {

    @Autowired ScoreService scores;
    @Autowired RuleEngineService engine;
    @Autowired IssueRepository issues;
    @Autowired RuleRepository rules;
    @Autowired SiteService siteService;

    private static final String RECORDS = "Student Records/Grade 7";

    private Issue issueOf(SchoolFile f, String ruleCode) {
        Map<String, Issue> byRule = issues.findByFileIdOrderByOpenedAtDesc(f.getId()).stream().collect(Collectors.toMap(
                i -> rules.findById(i.getRuleId()).orElseThrow().getCode(), Function.identity(), (a, b) -> a));
        return byRule.get(ruleCode);
    }

    @Test
    void theTilesAddUpToWhatTheChecksFoundAndFollowTheFixes() {
        World a = world();
        Site b = newSchool(a.org(), "School B", "b-" + a.school().getDomain());

        // School A: an urgent public link on a mark book, and a school file in a personal drive (normal)
        SchoolFile marks = file(a, "Marks.xlsx", new Profile("Mark book", Sensitivity.RESTRICTED, true, false), DriveType.SHARED, RECORDS);
        share(marks, ShareTargetType.ANYONE_WITH_LINK);
        SchoolFile worksheet = file(a, "Worksheet.docx", new Profile("Worksheet", Sensitivity.INTERNAL, false, false), DriveType.PERSONAL, "");
        // School B: one clean file (a second world would need its own people; the file only needs to exist)
        World bw = new World(a.org(), b, a.maths(), a.teacher(), a.coordinator(), a.principal(), a.it(), a.leader());
        SchoolFile clean = file(bw, "Policy.docx", new Profile("Policy", Sensitivity.INTERNAL, false, false), DriveType.SHARED, "Policies");
        clean.setSiteId(b.getId());
        files.save(clean);
        profiles.findByFileId(clean.getId()).ifPresent(p -> { p.setSiteId(b.getId()); profiles.save(p); });
        engine.evaluateFile(marks.getId());
        engine.evaluateFile(worksheet.getId());
        engine.evaluateFile(clean.getId());

        NetworkOverview o = scores.overview(user(a.leader()));

        assertThat(o.schools()).extracting(ScoreService.SiteScore::siteName).contains(a.school().getName(), "School B");
        assertThat(o.filesChecked()).isEqualTo(3);
        assertThat(o.open()).isEqualTo(2);
        assertThat(o.urgentOpen()).isEqualTo(1);
        assertThat(o.needFollowUp()).isZero();
        assertThat(o.fixedByStaff()).isZero();
        assertThat(o.personalDriveFiles()).isEqualTo(1);
        var schoolA = o.schools().stream().filter(s -> s.siteId().equals(a.school().getId())).findFirst().orElseThrow();
        assertThat(schoolA.filesChecked()).isEqualTo(2);
        assertThat(schoolA.openNormal()).isEqualTo(1);
        assertThat(schoolA.openUrgent()).isEqualTo(1);
        assertThat(schoolA.score()).as("100 - (1 normal + 3 x 1 urgent) / 2 files x 100, never below 0").isEqualTo(0.0);
        assertThat(o.schools().stream().filter(s -> s.siteId().equals(b.getId())).findFirst().orElseThrow().score()).isEqualTo(100.0);

        // the "Urgent" tile and the urgent list agree: one open urgent issue, named by school and rule, never by file
        assertThat(scores.urgentIssues(user(a.leader()))).hasSize(1).allSatisfy(u -> {
            assertThat(u.siteId()).isEqualTo(a.school().getId());
            assertThat(u.ruleCode()).isEqualTo("R2");
        });
        assertThatThrownBy(() -> scores.urgentIssues(user(a.principal()))).isInstanceOf(ForbiddenException.class);

        // the owner fixes the public link: only a re-check closes it, and the tiles follow
        permissions.deleteByFileId(marks.getId());
        engine.evaluateFile(marks.getId());
        // the coordinator is told the other issue needs a follow-up
        Issue r1 = issueOf(worksheet, "R1");
        r1.setStatus(IssueStatus.NEEDS_FOLLOW_UP);
        issues.save(r1);

        NetworkOverview after = scores.overview(user(a.leader()));
        assertThat(after.urgentOpen()).isZero();
        assertThat(scores.urgentIssues(user(a.leader()))).isEmpty();
        assertThat(after.open()).isEqualTo(1);
        assertThat(after.needFollowUp()).isEqualTo(1);
        assertThat(after.fixedByStaff()).isEqualTo(1);
    }

    @Test
    void theFirstWorkspaceIsJustASchoolAndOnlyLeadershipSeesTheOverview() {
        World w = world();
        Site first = sites.findByOrgIdOrderByName(w.org().getId()).stream().filter(s -> s.getName().equals("Head Office")).findFirst().orElseThrow();
        assertThat(first.isHeadOffice()).isFalse();
        assertThat(first.isScored()).isTrue();
        assertThat(scores.overview(user(w.leader())).schools()).extracting(ScoreService.SiteScore::siteId).contains(first.getId());
        assertThatThrownBy(() -> scores.overview(user(w.principal()))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> scores.overview(user(w.it()))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> scores.overview(user(w.teacher()))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aNetworkWithNoCheckYetSaysSoInsteadOfShowingAPerfectScore() {
        World w = world();
        NetworkOverview o = scores.overview(user(w.leader()));
        assertThat(o.checkedYet()).isFalse();
        assertThat(o.filesChecked()).isZero();
        assertThat(o.open()).isZero();
    }
}
