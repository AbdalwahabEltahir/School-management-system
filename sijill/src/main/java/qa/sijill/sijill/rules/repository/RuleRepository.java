package qa.sijill.sijill.rules.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import qa.sijill.sijill.rules.model.Rule;
import qa.sijill.sijill.rules.model.RuleStatus;

public interface RuleRepository extends JpaRepository<Rule, UUID> {

    /** Used to number rules added after the ten starter rules (R11, R12, …). */
    long countByOrgId(UUID orgId);

    /** Network-wide rules plus the given school's own rules, in one given status. */
    @Query("""
            select r from Rule r
            where r.orgId = :orgId and r.status = :status
              and (r.siteId is null or r.siteId = :siteId)
            order by r.code
            """)
    List<Rule> findApplicable(@Param("orgId") UUID orgId, @Param("siteId") UUID siteId,
                              @Param("status") RuleStatus status);

    /** Every non-deleted rule visible to a school (network-wide + its own), for the rule list. */
    @Query("""
            select r from Rule r
            where r.orgId = :orgId and r.status <> RuleStatus.DELETED
              and (r.siteId is null or r.siteId = :siteId)
            order by r.code
            """)
    List<Rule> findVisible(@Param("orgId") UUID orgId, @Param("siteId") UUID siteId);
}
