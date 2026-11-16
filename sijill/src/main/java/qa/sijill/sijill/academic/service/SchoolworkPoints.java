package qa.sijill.sijill.academic.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.academic.model.PointSource;

/**
 * Points for schoolwork. Each homework, quiz or assessment counts once per student (the entry keeps what it was for):
 * <ul>
 *   <li>homework marked as done: {@value #HOMEWORK} points;</li>
 *   <li>a quiz taken: 2 points for taking it plus up to 8 for the right answers;</li>
 *   <li>a published exam or assessment mark: up to 10 points, one for every 10% of the mark.</li>
 * </ul>
 * Exam points follow what the student can see: they appear when a result is published and go when it is unpublished.
 */
@Component
@RequiredArgsConstructor
public class SchoolworkPoints {

    public static final int HOMEWORK = 5;

    private final JdbcTemplate jdbc;
    private final EntityManager em;

    /** Gives (or corrects) the points a student earned for one piece of schoolwork. */
    @Transactional
    public void award(UUID orgId, UUID siteId, UUID studentId, PointSource source, UUID refId, int amount, String reason) {
        jdbc.update("INSERT INTO point_entries (org_id, site_id, student_id, amount, reason, source, awarded_on, ref_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, current_date, ?) "
                        + "ON CONFLICT (student_id, source, ref_id) WHERE ref_id IS NOT NULL DO UPDATE SET amount = EXCLUDED.amount, reason = EXCLUDED.reason",
                orgId, siteId, studentId, amount, cut(reason), source.name(), refId);
    }

    /** Takes back one student's points for a piece of schoolwork (homework un-ticked). */
    @Transactional
    public void revoke(UUID studentId, PointSource source, UUID refId) {
        jdbc.update("DELETE FROM point_entries WHERE student_id = ? AND source = ? AND ref_id = ?", studentId, source.name(), refId);
    }

    /** Takes back everyone's points for a piece of schoolwork that was removed. */
    @Transactional
    public void revokeAll(PointSource source, UUID refId) {
        jdbc.update("DELETE FROM point_entries WHERE source = ? AND ref_id = ?", source.name(), refId);
    }

    /** Points for a quiz: 2 for taking it, plus up to 8 for the right answers. */
    public static int quizPoints(int score, int total) {
        return 2 + (total == 0 ? 0 : Math.round(8f * score / total));
    }

    /**
     * Brings a section's exam points in line with what its students can see: a submitted mark of an assessment that is
     * published on its own or belongs to a published term earns points; anything else does not.
     */
    @Transactional
    public void syncExams(UUID sectionId) {
        em.flush();   // publication changes made through JPA in this transaction must be visible to the queries below
        String visible = "SELECT m.org_id, m.site_id, m.student_id, a.id AS ref, a.name, m.mark_value, a.max_mark "
                + "FROM marks m JOIN assessments a ON a.id = m.assessment_id "
                + "WHERE a.section_id = ? AND a.submitted_at IS NOT NULL AND NOT m.absent AND m.mark_value IS NOT NULL "
                + "AND (a.published_at IS NOT NULL OR EXISTS (SELECT 1 FROM result_publications rp WHERE rp.section_id = a.section_id AND rp.term = a.term))";
        jdbc.update("WITH vis AS (" + visible + ") DELETE FROM point_entries pe WHERE pe.source = 'EXAM' "
                + "AND pe.ref_id IN (SELECT id FROM assessments WHERE section_id = ?) "
                + "AND NOT EXISTS (SELECT 1 FROM vis WHERE vis.student_id = pe.student_id AND vis.ref = pe.ref_id)", sectionId, sectionId);
        jdbc.update("WITH vis AS (" + visible + ") INSERT INTO point_entries (org_id, site_id, student_id, amount, reason, source, awarded_on, ref_id) "
                + "SELECT org_id, site_id, student_id, LEAST(10, GREATEST(0, round(10 * mark_value / max_mark)))::int, "
                + "left(name || ' (' || round(100 * mark_value / max_mark) || '%)', 200), 'EXAM', current_date, ref FROM vis "
                + "ON CONFLICT (student_id, source, ref_id) WHERE ref_id IS NOT NULL DO UPDATE SET amount = EXCLUDED.amount, reason = EXCLUDED.reason", sectionId);
    }

    private static String cut(String s) {
        return s.length() > 200 ? s.substring(0, 199) + "…" : s;
    }
}
