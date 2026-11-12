package qa.sijill.sijill.rules.check;

import org.springframework.stereotype.Component;

import qa.sijill.sijill.rules.model.CheckType;
import qa.sijill.sijill.workspace.ShareTargetType;

/** "Anyone with the link" sharing (rule R2: no public links on sensitive files). */
@Component
public class PublicLinkCheck implements FileCheck {

    @Override
    public CheckType type() {
        return CheckType.PUBLIC_LINK;
    }

    @Override
    public CheckOutcome evaluate(CheckContext ctx) {
        boolean publicLink = ctx.permissions().stream()
                .anyMatch(p -> p.getTargetType() == ShareTargetType.ANYONE_WITH_LINK);
        return publicLink
                ? CheckOutcome.broken("The file can be opened by anyone who has the link.",
                        "يمكن لأي شخص لديه الرابط فتح الملف.")
                : CheckOutcome.ok();
    }
}
