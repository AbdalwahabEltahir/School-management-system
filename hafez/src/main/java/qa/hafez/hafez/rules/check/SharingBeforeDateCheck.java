package qa.hafez.hafez.rules.check;

import java.util.Set;
import org.springframework.stereotype.Component;

import qa.hafez.hafez.rules.model.CheckType;
import qa.hafez.hafez.workspace.ShareTargetType;

/**
 * Sharing before a date (rule R6: no exam sharing before the exam). A leaked exam is a <b>major
 * incident</b>, so this rule is urgent and reaches leadership.
 *
 * <p>Broken when the file has an exam date that is still in the future and is shared with anyone other
 * than named internal people: a public link, someone outside the network, or all staff.
 */
@Component
public class SharingBeforeDateCheck implements FileCheck {

    private static final Set<ShareTargetType> WIDE = Set.of(
            ShareTargetType.ANYONE_WITH_LINK, ShareTargetType.EXTERNAL_USER, ShareTargetType.ALL_STAFF);

    @Override
    public CheckType type() {
        return CheckType.SHARING_BEFORE_DATE;
    }

    @Override
    public CheckOutcome evaluate(CheckContext ctx) {
        var examDate = ctx.file().getExamDate();
        if (examDate == null || !ctx.today().isBefore(examDate)) {
            return CheckOutcome.ok(); // not an exam, or the exam date has arrived
        }
        boolean sharedWidely = ctx.permissions().stream()
                .anyMatch(p -> WIDE.contains(p.getTargetType()) || p.isExternal());
        return sharedWidely
                ? CheckOutcome.broken("The exam is shared before its date (" + examDate + ").",
                        "الاختبار مشارك قبل موعده (" + examDate + ").")
                : CheckOutcome.ok();
    }
}
