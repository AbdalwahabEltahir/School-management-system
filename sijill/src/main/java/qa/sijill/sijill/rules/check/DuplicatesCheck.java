package qa.sijill.sijill.rules.check;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import qa.sijill.sijill.files.model.SchoolFile;
import qa.sijill.sijill.files.repository.SchoolFileRepository;
import qa.sijill.sijill.rules.model.CheckType;

/**
 * Duplicate copies (rule R7: one official copy per policy).
 *
 * <p>Files with the same checksum in the same school are copies. The earliest one is the official copy;
 * every later copy is flagged, so exactly one file per group stays clean.
 */
@Component
@RequiredArgsConstructor
public class DuplicatesCheck implements FileCheck {

    private final SchoolFileRepository files;

    @Override
    public CheckType type() {
        return CheckType.DUPLICATES;
    }

    @Override
    public CheckOutcome evaluate(CheckContext ctx) {
        SchoolFile file = ctx.file();
        if (file.getChecksum() == null || file.getChecksum().isBlank() || file.isTrashed()) {
            return CheckOutcome.ok();
        }
        boolean hasEarlierCopy = files.existsEarlierCopy(
                file.getSiteId(), file.getChecksum(), file.getId(), file.getCreatedAt());
        return hasEarlierCopy
                ? CheckOutcome.broken("Another copy of this file already exists; only one official copy should be kept.",
                        "توجد نسخة أخرى من هذا الملف؛ يجب الاحتفاظ بنسخة رسمية واحدة فقط.")
                : CheckOutcome.ok();
    }
}
