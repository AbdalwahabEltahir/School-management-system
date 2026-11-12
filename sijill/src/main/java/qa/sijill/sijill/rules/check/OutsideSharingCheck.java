package qa.sijill.sijill.rules.check;

import org.springframework.stereotype.Component;

import qa.sijill.sijill.rules.model.CheckType;
import qa.sijill.sijill.workspace.ShareTargetType;

/** Sharing with people outside the network (rule R3: Restricted files stay inside). */
@Component
public class OutsideSharingCheck implements FileCheck {

    @Override
    public CheckType type() {
        return CheckType.OUTSIDE_SHARING;
    }

    @Override
    public CheckOutcome evaluate(CheckContext ctx) {
        // Only the count is reported: no e-mail addresses go into issues or logs.
        long outside = ctx.permissions().stream()
                .filter(p -> p.isExternal() || p.getTargetType() == ShareTargetType.EXTERNAL_USER)
                .count();
        return outside > 0
                ? CheckOutcome.broken("The file is shared with " + outside + " person(s) outside the network.",
                        "الملف مشارك مع " + outside + " شخص/أشخاص من خارج الشبكة.")
                : CheckOutcome.ok();
    }
}
