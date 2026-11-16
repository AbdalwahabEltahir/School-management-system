package qa.sijill.sijill.insights;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.insights.InsightsDtos.AchievementRequest;
import qa.sijill.sijill.insights.InsightsDtos.AchievementView;
import qa.sijill.sijill.insights.InsightsDtos.CategoryView;
import qa.sijill.sijill.insights.InsightsDtos.CloseRequest;
import qa.sijill.sijill.insights.InsightsDtos.IndicatorOutcome;
import qa.sijill.sijill.insights.InsightsDtos.IndicatorRequest;
import qa.sijill.sijill.insights.InsightsDtos.InvestigationDetail;
import qa.sijill.sijill.insights.InsightsDtos.InvestigationSummary;
import qa.sijill.sijill.insights.InsightsDtos.MemberRequest;
import qa.sijill.sijill.insights.InsightsDtos.NetworkSummary;
import qa.sijill.sijill.insights.InsightsDtos.NoteRequest;
import qa.sijill.sijill.insights.InsightsDtos.SchoolMetrics;
import qa.sijill.sijill.insights.InsightsDtos.SchoolReport;
import qa.sijill.sijill.insights.InsightsDtos.TeacherRecord;
import qa.sijill.sijill.insights.InsightsDtos.TeacherStanding;
import qa.sijill.sijill.shared.security.CurrentUser;

/** School metrics and reports, school achievements, and teacher performance. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InsightsController {

    private final MetricsService metrics;
    private final AchievementService achievements;
    private final PerformanceService performance;

    // ---- metrics
    @GetMapping("/metrics/network")
    public NetworkSummary network(CurrentUser user) {
        return metrics.network(user);
    }

    @GetMapping("/metrics/school")
    public SchoolMetrics mine(CurrentUser user) {
        return metrics.mine(user);
    }

    @GetMapping("/metrics/report")
    public SchoolReport report(CurrentUser user, @RequestParam(required = false) UUID siteId) {
        return metrics.report(user, siteId);
    }

    // ---- achievements
    @GetMapping("/achievements/template")
    public List<CategoryView> template() {
        return AchievementService.CATEGORIES;
    }

    @GetMapping("/achievements")
    public List<AchievementView> achievements(CurrentUser user, @RequestParam(required = false) UUID siteId) {
        return achievements.forSchool(user, siteId);
    }

    @GetMapping("/achievements/all")
    public List<AchievementView> allAchievements(CurrentUser user) {
        return achievements.all(user);
    }

    @PostMapping("/achievements")
    public AchievementView addAchievement(CurrentUser user, @Valid @RequestBody AchievementRequest request) {
        return achievements.add(user, request);
    }

    @DeleteMapping("/achievements/{id}")
    public void removeAchievement(CurrentUser user, @PathVariable UUID id) {
        achievements.remove(user, id);
    }

    // ---- teacher performance
    @GetMapping("/performance/categories")
    public Map<String, String> categories() {
        return PerformanceService.CATEGORIES;
    }

    @GetMapping("/performance/teachers")
    public List<TeacherStanding> standings(CurrentUser user) {
        return performance.standings(user);
    }

    @GetMapping("/performance/teachers/{teacherId}")
    public TeacherRecord teacher(CurrentUser user, @PathVariable UUID teacherId) {
        return performance.teacher(user, teacherId);
    }

    @PostMapping("/performance/indicators")
    public IndicatorOutcome record(CurrentUser user, @Valid @RequestBody IndicatorRequest request) {
        return performance.record(user, request.teacherId(), request.category(), request.note());
    }

    @GetMapping("/performance/investigations")
    public List<InvestigationSummary> investigations(CurrentUser user) {
        return performance.investigations(user);
    }

    @GetMapping("/performance/investigations/{id}")
    public InvestigationDetail investigation(CurrentUser user, @PathVariable UUID id) {
        return performance.investigation(user, id);
    }

    @PostMapping("/performance/investigations/{id}/notes")
    public InvestigationDetail note(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        return performance.addNote(user, id, request.body());
    }

    @PostMapping("/performance/investigations/{id}/members")
    public InvestigationDetail member(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody MemberRequest request) {
        return performance.addMember(user, id, request.personId());
    }

    @PostMapping("/performance/investigations/{id}/close")
    public InvestigationDetail close(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody CloseRequest request) {
        return performance.close(user, id, request.outcome());
    }
}
