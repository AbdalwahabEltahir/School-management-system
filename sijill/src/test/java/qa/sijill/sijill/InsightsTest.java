package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import qa.sijill.sijill.insights.AchievementService;
import qa.sijill.sijill.insights.InsightsDtos.AchievementRequest;
import qa.sijill.sijill.insights.InsightsDtos.IndicatorOutcome;
import qa.sijill.sijill.insights.InsightsDtos.Member;
import qa.sijill.sijill.insights.MetricsService;
import qa.sijill.sijill.insights.PerformanceService;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;

/** School achievements, school metrics, and the teacher performance rule (3 indicators = 1 warning, 3 warnings = investigation). */
class InsightsTest extends WorldTestSupport {

    @Autowired PerformanceService performance;
    @Autowired AchievementService achievements;
    @Autowired MetricsService metrics;

    @Test
    void threeIndicatorsMakeAWarningAndThreeWarningsOpenAnInvestigation() {
        World w = world();
        var head = user(w.principal());
        IndicatorOutcome o = null;
        for (int i = 1; i <= 2; i++) {
            o = performance.record(head, w.teacher().getId(), "PLANNING", "Weekly plan missing " + i);
        }
        assertThat(o.pendingIndicators()).isEqualTo(2);
        assertThat(o.warningIssued()).isFalse();
        o = performance.record(head, w.teacher().getId(), "MARKING", "Marks late");
        assertThat(o.warningIssued()).isTrue();                           // the third indicator makes the first warning
        assertThat(o.warnings()).isEqualTo(1);
        assertThat(o.pendingIndicators()).isZero();
        assertThat(o.investigationId()).isNull();
        assertThat(notifications.findAll().stream().filter(n -> n.getRecipientId().equals(w.teacher().getId())
                && n.getKind() == NotificationKind.SECURITY)).isNotEmpty();  // the teacher is told

        for (int i = 0; i < 6; i++) {
            o = performance.record(head, w.teacher().getId(), "DELIVERY", "Lesson not delivered " + i);
        }
        assertThat(o.warnings()).isEqualTo(3);
        assertThat(o.investigationId()).isNotNull();                      // the third warning opens an investigation by itself
        final java.util.UUID invId = o.investigationId();
        var inv = performance.investigation(head, invId);
        assertThat(inv.members()).extracting(Member::personId).contains(w.principal().getId(), w.coordinator().getId())
                .doesNotContain(w.teacher().getId());                      // the head teacher and the subject coordinator
        assertThat(inv.indicators()).hasSize(9);
        assertThat(inv.canManage()).isTrue();

        // the coordinator is involved and can add a note; School IT is not involved and cannot see it
        assertThat(performance.investigations(user(w.coordinator()))).hasSize(1);
        performance.addNote(user(w.coordinator()), o.investigationId(), "Spoke with the teacher.");
        assertThatThrownBy(() -> performance.investigation(user(w.it()), invId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> performance.close(user(w.coordinator()), invId, "x")).isInstanceOf(ForbiddenException.class);

        // the head teacher adds School IT, then closes with an outcome
        performance.addMember(head, o.investigationId(), w.it().getId());
        assertThat(performance.investigation(user(w.it()), o.investigationId()).notes()).hasSize(2);
        performance.close(head, o.investigationId(), "Support plan agreed.");
        assertThat(performance.investigation(head, o.investigationId()).summary().status()).isEqualTo("CLOSED");

        // only the head teacher records indicators
        assertThatThrownBy(() -> performance.record(user(w.coordinator()), w.teacher().getId(), "OTHER", "x")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> performance.standings(user(w.teacher()))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void schoolsFillInTheirAchievementsAndLeadershipSeesEverySchool() {
        World w = world();
        var lead = person(w.org(), sites.findById(w.leader().getSiteId()).orElseThrow(), "lead-" + java.util.UUID.randomUUID() + "@ho.test",
                "Network Leader", qa.sijill.sijill.shared.model.Role.LEADERSHIP, null);
        var a = achievements.add(user(w.principal()), new AchievementRequest("FACILITIES", "Opened a VR room", "20 headsets", "All grade 7",
                LocalDate.now().minusDays(3), "https://example.com/vr"));
        assertThat(a.points()).isEqualTo(20);
        var assistant = person(w.org(), w.school(), "pa-" + java.util.UUID.randomUUID() + "@" + w.school().getDomain(), "Pat Assistant",
                qa.sijill.sijill.shared.model.Role.PRINCIPAL_ASSISTANT, null);
        achievements.add(user(assistant), new AchievementRequest("COMPETITION", "Robotics prize", "", "", LocalDate.now(), null));
        assertThatThrownBy(() -> achievements.add(user(w.coordinator()), new AchievementRequest("OTHER", "x", "", "", LocalDate.now(), null)))
                .isInstanceOf(ForbiddenException.class);   // the coordinator no longer records achievements
        assertThatThrownBy(() -> achievements.add(user(w.teacher()), new AchievementRequest("OTHER", "x", "", "", LocalDate.now(), null)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> achievements.add(user(w.principal()), new AchievementRequest("OTHER", "x", "", "", LocalDate.now(), "http://insecure")))
                .isInstanceOf(qa.sijill.sijill.shared.exception.BadRequestException.class);

        assertThat(achievements.forSchool(user(w.principal()), null)).hasSize(2);
        assertThat(achievements.all(user(lead))).hasSize(2);
        assertThatThrownBy(() -> achievements.all(user(w.principal()))).isInstanceOf(ForbiddenException.class);

        var report = metrics.report(user(lead), w.school().getId());
        assertThat(report.metrics().achievements()).isEqualTo(2);
        assertThat(report.metrics().achievementPoints()).isEqualTo(35);
        assertThat(report.months()).hasSize(12);
        assertThat(metrics.network(user(lead)).schools()).extracting(m -> m.siteId()).contains(w.school().getId());
        assertThatThrownBy(() -> metrics.network(user(w.principal()))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> metrics.network(user(w.leader()))).isInstanceOf(ForbiddenException.class);   // EAA IT does not see grades
        assertThatThrownBy(() -> metrics.report(user(w.it()), w.school().getId())).isInstanceOf(ForbiddenException.class);
        achievements.remove(user(w.principal()), a.id());
        assertThat(achievements.forSchool(user(lead), w.school().getId())).hasSize(1);
    }
}
