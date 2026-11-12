package qa.hafez.hafez.dashboard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

import qa.hafez.hafez.shared.model.BaseEntity;

/**
 * A school's file-health score for one day, stored daily for the trend bars (section 5.7).
 * Scores belong to schools, <b>never to individuals</b> (design principle 3).
 */
@Getter @Setter
@Entity
@Table(name = "health_scores")
public class HealthScoreSnapshot extends BaseEntity {

    @Column(name = "score_date", nullable = false)
    private LocalDate scoreDate;

    /** 0 – 100, see {@code ScoreService.calculate}. */
    @Column(nullable = false)
    private double score;

    @Column(name = "files_checked", nullable = false)
    private int filesChecked;

    @Column(name = "open_normal", nullable = false)
    private int openNormal;

    @Column(name = "open_urgent", nullable = false)
    private int openUrgent;
}
