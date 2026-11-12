package qa.hafez.hafez.organisation.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import qa.hafez.hafez.approvals.ApprovalActions;
import qa.hafez.hafez.approvals.ApprovalHandler;
import qa.hafez.hafez.approvals.model.ApprovalRequest;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.security.CurrentUser;

/** Removing a school needs a second EAA administrator. */
@Component
@RequiredArgsConstructor
class SiteRemovalApproval implements ApprovalHandler {

    private final SiteService sites;

    @Override public String action() { return ApprovalActions.SITE_REMOVE; }

    @Override public Set<Role> approvers() { return Set.of(Role.EAA_ADMIN); }

    @Override public void execute(ApprovalRequest request, CurrentUser approver) {
        sites.executeRemove(approver, request.getTargetId());
    }
}
