package qa.sijill.sijill.dashboard.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import qa.sijill.sijill.dashboard.model.HealthScoreSnapshot;
import qa.sijill.sijill.dashboard.service.ScoreService;

/**
 * Response shapes of the dashboard API. The service already returns plain records for scores; only the
 * stored daily snapshot needs a DTO. Scores belong to schools, never to individuals.
 */
public final class DashboardDtos {

    private DashboardDtos() {}

    /** One bar of the daily trend. */
    public record TrendPoint(LocalDate date, double score, long filesChecked, int openNormal, int openUrgent) {
        public static TrendPoint from(HealthScoreSnapshot s) {
            return new TrendPoint(s.getScoreDate(), s.getScore(), s.getFilesChecked(), s.getOpenNormal(), s.getOpenUrgent());
        }
    }

    /** A school's score with its trend. */
    public record SiteScoreView(ScoreService.SiteScore score, List<TrendPoint> trend) {}

    public record SiteRef(UUID id, String name) {}
}
