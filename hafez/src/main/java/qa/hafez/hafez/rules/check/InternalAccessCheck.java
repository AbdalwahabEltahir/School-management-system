package qa.hafez.hafez.rules.check;

import org.springframework.stereotype.Component;

import qa.hafez.hafez.rules.model.CheckType;
import qa.hafez.hafez.workspace.AccessLevel;
import qa.hafez.hafez.workspace.ShareTargetType;

/**
 * "Who inside the network can open it" (rule R5: only the right people open Restricted files).
 *
 * <p>Broken when the file is open to all staff of the domain, or when the rule's parameter is a number
 * and more named internal people than that can open the file (the owner is not counted).
 */
@Component
public class InternalAccessCheck implements FileCheck {

    @Override
    public CheckType type() {
        return CheckType.INTERNAL_ACCESS;
    }

    @Override
    public CheckOutcome evaluate(CheckContext ctx) {
        boolean allStaff = ctx.permissions().stream()
                .anyMatch(p -> p.getTargetType() == ShareTargetType.ALL_STAFF);
        if (allStaff) {
            return CheckOutcome.broken("The file is open to all staff of the school.",
                    "الملف متاح لجميع العاملين في المدرسة.");
        }

        Integer max = parseLimit(ctx.rule().getParameter());
        if (max != null) {
            long named = ctx.permissions().stream()
                    .filter(p -> p.getTargetType() == ShareTargetType.INTERNAL_USER)
                    .filter(p -> p.getAccessLevel() != AccessLevel.OWNER)
                    .count();
            if (named > max) {
                return CheckOutcome.broken(
                        named + " people can open the file; the limit for this rule is " + max + ".",
                        "يمكن لـ " + named + " أشخاص فتح الملف، والحد المسموح به لهذه القاعدة هو " + max + ".");
            }
        }
        return CheckOutcome.ok();
    }

    /** The parameter is optional; anything that is not a whole number means "no limit". */
    private Integer parseLimit(String parameter) {
        if (parameter == null || parameter.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(parameter.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
