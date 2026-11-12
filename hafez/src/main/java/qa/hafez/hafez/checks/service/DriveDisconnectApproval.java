package qa.hafez.hafez.checks.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import qa.hafez.hafez.approvals.ApprovalActions;
import qa.hafez.hafez.approvals.ApprovalHandler;
import qa.hafez.hafez.approvals.model.ApprovalRequest;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.security.CurrentUser;

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
