package qa.sijill.sijill.rules.check;

import org.springframework.stereotype.Component;

import qa.sijill.sijill.rules.model.CheckType;

/** The owner is a generic / shared account (rule R9: no files owned by shared accounts; every action must tie to one person). */
@Component
public class GenericOwnerCheck implements FileCheck {

    @Override
    public CheckType type() {
        return CheckType.GENERIC_OWNER;
    }

    @Override
    public CheckOutcome evaluate(CheckContext ctx) {
        if (ctx.owner() != null && ctx.owner().isGeneric()) {
            return CheckOutcome.broken("The file is owned by a shared account, so nobody is personally responsible for it.",
                    "الملف مملوك لحساب مشترك، فلا يوجد شخص مسؤول عنه.");
        }
        return CheckOutcome.ok();
    }
}
