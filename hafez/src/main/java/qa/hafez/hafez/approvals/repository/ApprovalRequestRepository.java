package qa.hafez.hafez.approvals.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.hafez.hafez.approvals.model.ApprovalRequest;
import qa.hafez.hafez.approvals.model.ApprovalStatus;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    List<ApprovalRequest> findTop100ByOrgIdOrderByCreatedAtDesc(UUID orgId);

    List<ApprovalRequest> findTop100BySiteIdOrderByCreatedAtDesc(UUID siteId);

    Optional<ApprovalRequest> findFirstByActionAndTargetIdAndStatus(String action, UUID targetId, ApprovalStatus status);
}
