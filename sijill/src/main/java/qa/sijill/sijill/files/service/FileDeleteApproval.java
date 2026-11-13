package qa.sijill.sijill.files.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import qa.sijill.sijill.approvals.ApprovalActions;
import qa.sijill.sijill.approvals.ApprovalHandler;
import qa.sijill.sijill.approvals.model.ApprovalRequest;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/** Deleting a file from the register needs a second person: the principal, a coordinator or School IT of the same school. */
@Component
@RequiredArgsConstructor
class FileDeleteApproval implements ApprovalHandler {

    private final FileService files;

    @Override public String action() { return ApprovalActions.FILE_DELETE; }

    @Override public Set<Role> approvers() { return Set.of(Role.PRINCIPAL, Role.COORDINATOR, Role.SCHOOL_IT); }

    @Override public boolean mayDecide(CurrentUser approver, ApprovalRequest request) {
        return approver.siteId().equals(request.getSiteId());
    }

    @Override public void execute(ApprovalRequest request, CurrentUser approver) {
        files.executeDelete(approver, request.getTargetId());
    }
}
