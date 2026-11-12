package qa.hafez.hafez.rules.check;

import org.springframework.stereotype.Component;

import qa.hafez.hafez.rules.model.CheckType;

/**
 * The file was deleted or moved to the trash (rule R10: no unapproved deletion of important files).
 * The rule only <i>alerts</i>; recovery is from the trash or the backup vault (proposal 9 and 5.13).
 */
@Component
public class DeletedOrTrashedCheck implements FileCheck {

    @Override
    public CheckType type() {
        return CheckType.DELETED_OR_TRASHED;
    }

    @Override
    public CheckOutcome evaluate(CheckContext ctx) {
        return ctx.file().isTrashed()
                ? CheckOutcome.broken("The file was deleted or moved to the trash.",
                        "تم حذف الملف أو نقله إلى سلة المحذوفات.")
                : CheckOutcome.ok();
    }
}
