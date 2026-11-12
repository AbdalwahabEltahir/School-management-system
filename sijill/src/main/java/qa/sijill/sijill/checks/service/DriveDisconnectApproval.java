package qa.sijill.sijill.checks.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import qa.sijill.sijill.approvals.ApprovalActions;
import qa.sijill.sijill.approvals.ApprovalHandler;
import qa.sijill.sijill.approvals.model.ApprovalRequest;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/** Disconnecting Google Drive needs a second EAA administrator. */
@Component
@RequiredArgsConstructor
class DriveDisconnectApproval implements ApprovalHandler {

    private final ConnectionService connection;

    @Override public String action() { return ApprovalActions.DRIVE_DISCONNECT; }

    @Override public Set<Role> approvers() { return Set.of(Role.EAA_ADMIN); }

    @Override public void execute(ApprovalRequest request, CurrentUser approver) {
        connection.applyEnabled(approver, request.getTargetId(), false);
    }
}
