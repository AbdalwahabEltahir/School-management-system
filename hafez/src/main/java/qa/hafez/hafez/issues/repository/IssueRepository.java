package qa.hafez.hafez.issues.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import qa.hafez.hafez.issues.model.Issue;
import qa.hafez.hafez.issues.model.IssueStatus;
import qa.hafez.hafez.shared.model.Severity;

public interface IssueRepository extends JpaRepository<Issue, UUID> {

    // ---- the rule engine: find "the" open issue of a (file, rule) pair ----
    Optional<Issue> findFirstByFileIdAndRuleIdAndStatusIn(UUID fileId, UUID ruleId, Collection<IssueStatus> statuses);

    List<Issue> findByFileIdOrderByOpenedAtDesc(UUID fileId);

    List<Issue> findByRuleIdAndStatusIn(UUID ruleId, Collection<IssueStatus> statuses);

    // ---- role-scoped lists (see AccessPolicy for who may call which) ----
    /** Teacher: their own issues. */
    List<Issue> findByOwnerIdOrderByOpenedAtDesc(UUID ownerId);

    /** Coordinator: all issues of her subject in her school. */
    List<Issue> findBySiteIdAndSubjectIdOrderByOpenedAtDesc(UUID siteId, UUID subjectId);

    /** Principal: all issues of her school. */
    List<Issue> findBySiteIdOrderByOpenedAtDesc(UUID siteId);

    // ---- background jobs ----
    /** Issues whose owner reminder is due. */
    List<Issue> findByStatusAndReminderDueAtBefore(IssueStatus status, Instant now);

    /** Issues that should become "Needs follow-up". */
    List<Issue> findByStatusAndFollowUpDueAtBefore(IssueStatus status, Instant now);

    /** Urgent issues still open after one school day (leadership alert). */
    List<Issue> findBySeverityAndStatusInAndOpenedAtBefore(Severity severity, Collection<IssueStatus> statuses, Instant before);

    /** A leaver's open issues, which move to the coordinator. */
    List<Issue> findByOwnerIdAndStatusIn(UUID ownerId, Collection<IssueStatus> statuses);

    // ---- counts for the score and overview ----
    long countBySiteIdAndStatusInAndSeverity(UUID siteId, Collection<IssueStatus> statuses, Severity severity);

    long countBySiteIdAndStatusIn(UUID siteId, Collection<IssueStatus> statuses);

    long countBySiteIdAndStatus(UUID siteId, IssueStatus status);

    List<Issue> findBySiteIdAndSeverityAndStatusInOrderByOpenedAtDesc(UUID siteId, Severity severity, Collection<IssueStatus> statuses);

    /** Open-issue counts for every school at once: rows of (siteId, severity, count). */
    @Query("""
            select i.siteId, i.severity, count(i) from Issue i
            where i.orgId = :orgId and i.status in :open
            group by i.siteId, i.severity
            """)
    List<Object[]> countOpenBySiteAndSeverity(@Param("orgId") UUID orgId, @Param("open") Collection<IssueStatus> open);
}
