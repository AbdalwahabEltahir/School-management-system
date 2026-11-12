package qa.hafez.hafez.people.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import qa.hafez.hafez.approvals.ApprovalActions;
import qa.hafez.hafez.approvals.ApprovalHandler;
import qa.hafez.hafez.approvals.model.ApprovalRequest;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.security.CurrentUser;

/** Changing a role needs an EAA administrator other than the requester. */
@Component
@RequiredArgsConstructor
class RoleChangeApproval implements ApprovalHandler {

    private final PeopleService people;

    @Override public String action() { return ApprovalActions.ROLE_CHANGE; }

    @Override public Set<Role> approvers() { return Set.of(Role.EAA_ADMIN); }

    @Override public void execute(ApprovalRequest request, CurrentUser approver) {
        people.applyRole(approver, request.getTargetId(), Role.valueOf(request.getPayload()));
    }
}
