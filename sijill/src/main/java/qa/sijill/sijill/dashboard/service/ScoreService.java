package qa.sijill.sijill.dashboard.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.dashboard.model.HealthScoreSnapshot;
import qa.sijill.sijill.dashboard.repository.HealthScoreRepository;
import qa.sijill.sijill.files.repository.SchoolFileRepository;
import qa.sijill.sijill.issues.model.IssueStatus;
import qa.sijill.sijill.issues.repository.IssueRepository;
import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.model.Severity;
import qa.sijill.sijill.shared.security.AccessPolicy;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.workspace.DriveType;

/**
 * The file health score and the leadership overview (proposal 5.7, FR-11.6).
 *
 * <pre>
 *   score = 100 − (open normal + 3 × open urgent) ÷ files checked × 100        (never below 0)
 *   Good 85+ · Watch 70–84 · Needs attention below 70
 * </pre>
 * Closed issues and allowed exceptions are excluded (they are not open). <b>Scores belong to schools,
 * never to individuals</b> (design principle 3): there is no per-person score anywhere.
 */
@Service
@RequiredArgsConstructor
public class ScoreService {

    public enum ScoreRating { GOOD, WATCH, NEEDS_ATTENTION }

    /** One school's current score and the numbers behind it. */
    public record SiteScore(UUID siteId, String siteName, double score, ScoreRating rating, long filesChecked,
                            long openNormal, long openUrgent, long needFollowUp, long fixed) {}

    /** The leadership overview: six tiles and the schools table (FR-11.6). */
    public record NetworkOverview(boolean checkedYet, long filesChecked, long open, long urgentOpen,
                                  long needFollowUp, long fixedByStaff, long personalDriveFiles,
                                  List<SiteScore> schools) {}

    private final qa.sijill.sijill.rules.repository.RuleVersionRepository ruleVersions;
    private final qa.sijill.sijill.rules.repository.RuleRepository rules;
    private final SiteRepository sites;
    private final SchoolFileRepository files;
    private final IssueRepository issues;
    private final HealthScoreRepository snapshots;
    private final AccessPolicy policy;
    private final Clock clock;

    // ================================================================== the formula (pure)

    /** The score formula. With no files checked yet the score is 100 (nothing is wrong). */
    public static double calculate(long openNormal, long openUrgent, long filesChecked) {
        if (filesChecked <= 0) {
            return 100.0;
        }
        double score = 100.0 - ((openNormal + 3.0 * openUrgent) / filesChecked) * 100.0;
        return Math.max(0.0, score);
    }

    public static ScoreRating rating(double score) {
        if (score >= 85) {
            return ScoreRating.GOOD;
        }
        return score >= 70 ? ScoreRating.WATCH : ScoreRating.NEEDS_ATTENTION;
    }

    // ================================================================== reading

    /** A school's score: the principal and School IT of that school, or Leadership for any school. */
    @Transactional(readOnly = true)
    public SiteScore siteScore(CurrentUser user, UUID siteId) {
        Site site = sites.findById(siteId).orElseThrow(() -> new NotFoundException("The school"));
        if (!user.isAny(Role.PRINCIPAL, Role.SCHOOL_IT, Role.LEADERSHIP)) {
            throw new ForbiddenException("The school score is not available for your role.");
        }
        policy.requireSiteAccess(user, siteId, site.getOrgId());
        return scoreOf(site);
    }

    /** The daily trend bars for the last {@code days} days. */
    @Transactional(readOnly = true)
    public List<HealthScoreSnapshot> trend(CurrentUser user, UUID siteId, int days) {
        siteScore(user, siteId); // same access rules
        return snapshots.findBySiteIdAndScoreDateAfterOrderByScoreDateAsc(
                siteId, LocalDate.now(clock).minusDays(Math.max(days, 1)));
    }

    /** Leadership overview: totals across all scored schools, never individual issues. */
    @Transactional(readOnly = true)
    public NetworkOverview overview(CurrentUser user) {
        policy.requireRole(user, Role.LEADERSHIP);
        List<SiteScore> schools = new ArrayList<>();
        long filesChecked = 0, open = 0, urgent = 0, followUp = 0, fixed = 0, personal = 0;
        boolean checkedYet = false;
        for (Site site : sites.findByOrgIdAndScoredTrueAndActiveTrueOrderByName(user.orgId())) {
            SiteScore s = scoreOf(site);
            schools.add(s);
            filesChecked += s.filesChecked();
            open += s.openNormal() + s.openUrgent();
            urgent += s.openUrgent();
            followUp += s.needFollowUp();
            fixed += s.fixed();
            personal += files.countBySiteIdAndDriveTypeAndTrashedAtIsNull(site.getId(), DriveType.PERSONAL);
            checkedYet |= site.getFirstCheckAt() != null;
        }
        return new NetworkOverview(checkedYet, filesChecked, open, urgent, followUp, fixed, personal, schools);
    }

    /** An open urgent issue as Leadership may see it: which school and rule, never the file name or contents. */
    public record UrgentIssue(UUID issueId, UUID siteId, String siteName, String ruleCode, String ruleName,
                              String status, java.time.Instant openedAt) {}

    /**
     * The open urgent issues behind the overview's "Urgent" tile, so the tile and the list always agree. Incidents are a
     * separate, rarer thing (an exam leak, a deletion, a lost connection, an urgent issue left a whole school day).
     */
    @Transactional(readOnly = true)
    public List<UrgentIssue> urgentIssues(CurrentUser user) {
        policy.requireRole(user, Role.LEADERSHIP);
        List<UrgentIssue> out = new ArrayList<>();
        for (Site site : sites.findByOrgIdAndScoredTrueAndActiveTrueOrderByName(user.orgId())) {
            for (var i : issues.findBySiteIdAndSeverityAndStatusInOrderByOpenedAtDesc(site.getId(), Severity.URGENT, IssueStatus.OPEN)) {
                var v = ruleVersions.findById(i.getRuleVersionId()).orElse(null);
                var r = rules.findById(i.getRuleId()).orElse(null);
                out.add(new UrgentIssue(i.getId(), site.getId(), site.getName(), r == null ? "" : r.getCode(), v == null ? "" : v.getName(),
                        i.getStatus().name(), i.getOpenedAt()));
            }
        }
        return out;
    }

    // ================================================================== daily snapshot

    /** Stores (or refreshes) today's score for a school, for the trend bars. Called after every check. */
    @Transactional
    public void snapshotToday(Site site) {
        SiteScore s = scoreOf(site);
        LocalDate today = LocalDate.now(clock);
        HealthScoreSnapshot snap = snapshots.findBySiteIdAndScoreDate(site.getId(), today).orElseGet(() -> {
            HealthScoreSnapshot n = new HealthScoreSnapshot();
            n.setOrgId(site.getOrgId());
            n.setSiteId(site.getId());
            n.setScoreDate(today);
            return n;
        });
        snap.setScore(s.score());
        snap.setFilesChecked((int) s.filesChecked());
        snap.setOpenNormal((int) s.openNormal());
        snap.setOpenUrgent((int) s.openUrgent());
        snapshots.save(snap);
    }

    private SiteScore scoreOf(Site site) {
        long filesChecked = files.countBySiteIdAndTrashedAtIsNull(site.getId());
        long openNormal = issues.countBySiteIdAndStatusInAndSeverity(site.getId(), IssueStatus.OPEN, Severity.NORMAL);
        long openUrgent = issues.countBySiteIdAndStatusInAndSeverity(site.getId(), IssueStatus.OPEN, Severity.URGENT);
        double score = calculate(openNormal, openUrgent, filesChecked);
        return new SiteScore(site.getId(), site.getName(), score, rating(score), filesChecked, openNormal, openUrgent,
                issues.countBySiteIdAndStatus(site.getId(), IssueStatus.NEEDS_FOLLOW_UP),
                issues.countBySiteIdAndStatus(site.getId(), IssueStatus.FIXED));
    }
}
