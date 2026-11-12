package qa.hafez.hafez.issues.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import qa.hafez.hafez.issues.model.ExceptionGrant;

public interface ExceptionGrantRepository extends JpaRepository<ExceptionGrant, UUID> {

    /** The exception that currently lets a file break a rule, if any. */
    @Query("""
            select e from ExceptionGrant e
            where e.fileId = :fileId and e.ruleId = :ruleId
              and e.endedAt is null and e.endsOn >= :today
            """)
    Optional<ExceptionGrant> findActive(@Param("fileId") UUID fileId, @Param("ruleId") UUID ruleId,
                                        @Param("today") LocalDate today);

    /** Exceptions whose end date has passed but that have not been closed out yet. */
    List<ExceptionGrant> findByEndedAtIsNullAndEndsOnBefore(LocalDate today);

    /** Not-yet-ended exceptions of a school (the Exceptions table, FR-11.4). */
    List<ExceptionGrant> findBySiteIdAndEndedAtIsNullOrderByEndsOnAsc(UUID siteId);

    /** Leadership's Exceptions screen: every school. */
    List<ExceptionGrant> findByOrgIdAndEndedAtIsNullOrderByEndsOnAsc(UUID orgId);
}
