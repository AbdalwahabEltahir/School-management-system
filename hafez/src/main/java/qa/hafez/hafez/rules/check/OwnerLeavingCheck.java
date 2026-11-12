package qa.hafez.hafez.rules.check;

import org.springframework.stereotype.Component;

import qa.hafez.hafez.people.model.PersonStatus;
import qa.hafez.hafez.rules.model.CheckType;
import qa.hafez.hafez.workspace.DriveType;

/** The owner is leaving (rule R8: leavers move school files first). Broken for school files in a leaver's personal drive. */
@Component
public class OwnerLeavingCheck implements FileCheck {

    @Override
    public CheckType type() {
        return CheckType.OWNER_LEAVING;
    }

    @Override
    public CheckOutcome evaluate(CheckContext ctx) {
        // A person who has already left still counts: their school files are still in a personal drive.
        boolean ownerLeaving = ctx.owner() != null
                && (ctx.owner().getStatus() == PersonStatus.LEAVING || ctx.owner().getStatus() == PersonStatus.LEFT);
        if (ownerLeaving && ctx.file().getDriveType() == DriveType.PERSONAL) {
            return CheckOutcome.broken("The owner is leaving and this school file is still in their personal drive.",
                    "المالك سيغادر وهذا الملف المدرسي ما زال في مساحته الشخصية.");
        }
        return CheckOutcome.ok();
    }
}
