package qa.sijill.sijill.organisation.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import qa.sijill.sijill.approvals.ApprovalActions;
import qa.sijill.sijill.approvals.ApprovalHandler;
import qa.sijill.sijill.approvals.model.ApprovalRequest;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

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
