package qa.hafez.hafez.rules.check;

import org.springframework.stereotype.Component;

import qa.hafez.hafez.rules.model.CheckType;
import qa.hafez.hafez.workspace.DriveType;

/**
 * "Where the file is saved" (rules R1 and R4).
 * <ul>
 *   <li>No parameter (R1): the file must be in a school <i>shared</i> drive, not a personal one.</li>
 *   <li>Parameter = a folder name (R4, "Student Records"): the file's folder path must start with it.</li>
 * </ul>
 */
@Component
public class LocationCheck implements FileCheck {

    @Override
    public CheckType type() {
        return CheckType.LOCATION;
    }

    @Override
    public CheckOutcome evaluate(CheckContext ctx) {
        String requiredFolder = ctx.rule().getParameter();

        if (requiredFolder == null || requiredFolder.isBlank()) {
            if (ctx.file().getDriveType() == DriveType.PERSONAL) {
                return CheckOutcome.broken(
                        "The file is saved in a personal drive instead of the school shared drive.",
                        "الملف محفوظ في مساحة شخصية بدلًا من المساحة المشتركة للمدرسة.");
            }
            return CheckOutcome.ok();
        }

        String folder = ctx.file().getFolderPath() == null ? "" : ctx.file().getFolderPath();
        boolean inRequiredFolder = ctx.file().getDriveType() == DriveType.SHARED
                && folder.toLowerCase().startsWith(requiredFolder.trim().toLowerCase());
        if (!inRequiredFolder) {
            return CheckOutcome.broken(
                    "The file is not in the \"" + requiredFolder + "\" folder.",
                    "الملف ليس في مجلد «" + requiredFolder + "».");
        }
        return CheckOutcome.ok();
    }
}
