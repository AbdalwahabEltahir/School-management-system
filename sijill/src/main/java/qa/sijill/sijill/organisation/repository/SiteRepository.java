package qa.sijill.sijill.organisation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.organisation.model.Site;

public interface SiteRepository extends JpaRepository<Site, UUID> {

    List<Site> findByOrgIdOrderByName(UUID orgId);

    /** Sites that appear in scores and the network overview (head office is unscored). */
    List<Site> findByOrgIdAndScoredTrueAndActiveTrueOrderByName(UUID orgId);

    Optional<Site> findByDomainIgnoreCase(String domain);
}
