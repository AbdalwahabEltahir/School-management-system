package qa.hafez.hafez.academic.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import qa.hafez.hafez.approvals.ApprovalActions;
import qa.hafez.hafez.approvals.ApprovalHandler;
import qa.hafez.hafez.approvals.model.ApprovalRequest;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.security.CurrentUser;

/** Results are published only after a second person from the school approves the teacher's request. */
@Component
@RequiredArgsConstructor
class MarksPublishApproval implements ApprovalHandler {

    private final MarksService marks;

    @Override public String action() { return ApprovalActions.MARKS_PUBLISH; }

    @Override public Set<Role> approvers() { return Set.of(Role.PRINCIPAL, Role.COORDINATOR, Role.SCHOOL_IT); }

    @Override public boolean mayDecide(CurrentUser approver, ApprovalRequest request) {
        return approver.siteId().equals(request.getSiteId());
    }

    @Override public void execute(ApprovalRequest request, CurrentUser approver) {
        marks.executePublish(approver, request.getTargetId(), Integer.parseInt(request.getPayload()));
    }
}
