package qa.hafez.hafez.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import qa.hafez.hafez.dashboard.service.ScoreService.ScoreRating;

/** Proposal 5.7: score = 100 − (open normal + 3 × open urgent) ÷ files checked × 100, never below 0. */
class ScoreServiceTest {

    @Test
    void aSchoolWithNoOpenIssuesScoresOneHundred() {
        assertThat(ScoreService.calculate(0, 0, 500)).isEqualTo(100.0);
    }

    @Test
    void urgentIssuesCountThreeTimes() {
        // 10 normal + 5 urgent over 500 files: (10 + 15) / 500 = 5% -> 95
        assertThat(ScoreService.calculate(10, 5, 500)).isEqualTo(95.0);
    }

    @Test
    void theScoreNeverGoesBelowZero() {
        assertThat(ScoreService.calculate(900, 100, 100)).isEqualTo(0.0);
    }

    @Test
    void noFilesCheckedYetIsNotPenalised() {
        assertThat(ScoreService.calculate(0, 0, 0)).isEqualTo(100.0);
        assertThat(ScoreService.calculate(5, 5, 0)).isEqualTo(100.0);
    }

    @Test
    void ratingsFollowTheProposalThresholds() {
        assertThat(ScoreService.rating(100)).isEqualTo(ScoreRating.GOOD);
        assertThat(ScoreService.rating(85)).isEqualTo(ScoreRating.GOOD);
        assertThat(ScoreService.rating(84.9)).isEqualTo(ScoreRating.WATCH);
        assertThat(ScoreService.rating(70)).isEqualTo(ScoreRating.WATCH);
        assertThat(ScoreService.rating(69.9)).isEqualTo(ScoreRating.NEEDS_ATTENTION);
    }
}
