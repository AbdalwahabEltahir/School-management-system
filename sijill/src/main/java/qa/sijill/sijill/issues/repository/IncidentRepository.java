package qa.sijill.sijill.issues.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.issues.model.Incident;
import qa.sijill.sijill.issues.model.IncidentType;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    /** Leadership view: incidents across the network. */
    List<Incident> findByOrgIdOrderByDetectedAtDesc(UUID orgId);

    /** Principal view: incidents of one school. */
    List<Incident> findBySiteIdOrderByDetectedAtDesc(UUID siteId);

    /** Prevents raising the same incident twice for the same file while one is still open. */
    boolean existsByFileIdAndTypeAndResolvedAtIsNull(UUID fileId, IncidentType type);

    boolean existsByIssueIdAndTypeAndResolvedAtIsNull(UUID issueId, IncidentType type);

    /** For school-wide incidents that have no file or issue (bulk deletion, lost connection). */
    boolean existsBySiteIdAndTypeAndResolvedAtIsNull(UUID siteId, IncidentType type);
}
