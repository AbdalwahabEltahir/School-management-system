package qa.sijill.sijill.rules.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.rules.model.RuleVersion;

public interface RuleVersionRepository extends JpaRepository<RuleVersion, UUID> {

    List<RuleVersion> findByRuleIdOrderByVersionNumberDesc(UUID ruleId);

    /** The version the rule engine uses: the newest <i>approved</i> one. */
    Optional<RuleVersion> findFirstByRuleIdAndApprovedAtIsNotNullOrderByVersionNumberDesc(UUID ruleId);

    /** The newest version, approved or not; used to number the next version. */
    Optional<RuleVersion> findFirstByRuleIdOrderByVersionNumberDesc(UUID ruleId);
}
