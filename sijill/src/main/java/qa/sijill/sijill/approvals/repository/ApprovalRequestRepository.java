package qa.sijill.sijill.approvals.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.approvals.model.ApprovalRequest;
import qa.sijill.sijill.approvals.model.ApprovalStatus;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    List<ApprovalRequest> findTop100ByOrgIdOrderByCreatedAtDesc(UUID orgId);

    List<ApprovalRequest> findTop100BySiteIdOrderByCreatedAtDesc(UUID siteId);

    Optional<ApprovalRequest> findFirstByActionAndTargetIdAndStatus(String action, UUID targetId, ApprovalStatus status);
}
