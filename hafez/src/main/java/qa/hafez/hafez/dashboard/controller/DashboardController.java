package qa.hafez.hafez.dashboard.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import qa.hafez.hafez.dashboard.dto.DashboardDtos;
import qa.hafez.hafez.dashboard.service.ReportService;
import qa.hafez.hafez.dashboard.service.ScoreService;
import qa.hafez.hafez.issues.dto.IssueDtos;
import qa.hafez.hafez.issues.service.IncidentService;
import qa.hafez.hafez.shared.security.CurrentUser;

/**
 * The numbers screens: the leadership overview, a school's score and trend, and major incidents.
 * Scores belong to schools, never to individuals; leadership sees totals and incidents only.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ScoreService scores;
    private final IncidentService incidents;
    private final ReportService reports;

    /** Leadership overview: six tiles and the schools table. */
    @GetMapping("/overview")
    public ScoreService.NetworkOverview overview(CurrentUser user) {
        return scores.overview(user);
    }

    /** The open urgent issues behind the "Urgent" tile (school, rule and age only: no file names). */
    @GetMapping("/urgent-issues")
    public List<ScoreService.UrgentIssue> urgentIssues(CurrentUser user) {
        return scores.urgentIssues(user);
    }

    /** A school's score, with a daily trend (default 30 days). */
    @GetMapping("/sites/{siteId}/score")
    public DashboardDtos.SiteScoreView score(CurrentUser user, @PathVariable UUID siteId,
                                             @RequestParam(defaultValue = "30") int days) {
        return new DashboardDtos.SiteScoreView(scores.siteScore(user, siteId),
                scores.trend(user, siteId, days).stream().map(DashboardDtos.TrendPoint::from).toList());
    }

    /** "Download weekly report (PDF)": the network report for Leadership, the school report for a principal. */
    @GetMapping("/weekly-report.pdf")
    public ResponseEntity<byte[]> weeklyReport(CurrentUser user) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("hafez-weekly-report.pdf").build().toString())
                .body(reports.weeklyReportPdf(user));
    }

    /** Major incidents: leadership sees every school's, others their own school's. */
    @GetMapping("/incidents")
    public List<IssueDtos.IncidentView> incidents(CurrentUser user) {
        return incidents.list(user).stream().map(IssueDtos.IncidentView::from).toList();
    }

    @PostMapping("/incidents/{incidentId}/resolve")
    public IssueDtos.IncidentView resolve(CurrentUser user, @PathVariable UUID incidentId) {
        return IssueDtos.IncidentView.from(incidents.resolve(user, incidentId));
    }
}
