package qa.hafez.hafez.audit.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.hafez.hafez.audit.model.ActivityLogEntry;

/**
 * Read and append access to the activity log. There are deliberately no update or delete methods;
 * the database trigger would reject them anyway.
 */
public interface ActivityLogRepository extends JpaRepository<ActivityLogEntry, UUID> {

    /** The latest entry of an organisation's chain: its hash is the next entry's {@code prevHash}. */
    Optional<ActivityLogEntry> findFirstByOrgIdOrderBySeqDesc(UUID orgId);

    /** Walks the chain in order (used by chain verification, page by page). */
    List<ActivityLogEntry> findByOrgIdAndSeqGreaterThanOrderBySeqAsc(UUID orgId, long afterSeq, Pageable page);

    // ---- role-scoped reads, newest first: Leadership = org, Principal/IT = site, Coordinator = subject ----
    List<ActivityLogEntry> findByOrgIdOrderBySeqDesc(UUID orgId, Pageable page);

    List<ActivityLogEntry> findBySiteIdOrderBySeqDesc(UUID siteId, Pageable page);

    List<ActivityLogEntry> findBySiteIdAndSubjectIdOrderBySeqDesc(UUID siteId, UUID subjectId, Pageable page);

    List<ActivityLogEntry> findByOrgIdAndActionOrderBySeqDesc(UUID orgId, String action, Pageable page);

    List<ActivityLogEntry> findBySiteIdAndActionOrderBySeqDesc(UUID siteId, String action, Pageable page);

    List<ActivityLogEntry> findBySiteIdAndSubjectIdAndActionOrderBySeqDesc(UUID siteId, UUID subjectId, String action,
                                                                           Pageable page);
}
