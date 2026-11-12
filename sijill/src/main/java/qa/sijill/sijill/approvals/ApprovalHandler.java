package qa.sijill.sijill.approvals;

import java.util.Set;

import qa.sijill.sijill.approvals.model.ApprovalRequest;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/** Implemented by the module that owns a sensitive action: says who may approve it and carries it out. */
public interface ApprovalHandler {

    /** The action code (see {@link ApprovalActions}). */
    String action();

    /** Roles that may approve; the approver must also be a different person from the requester. */
    Set<Role> approvers();

    /** Whether this approver may decide this particular request (for example, same school). Default: yes. */
    default boolean mayDecide(CurrentUser approver, ApprovalRequest request) {
        return true;
    }

    /** Performs the action. Runs in the approval's transaction; the approver is the acting user. */
    void execute(ApprovalRequest request, CurrentUser approver);
}
