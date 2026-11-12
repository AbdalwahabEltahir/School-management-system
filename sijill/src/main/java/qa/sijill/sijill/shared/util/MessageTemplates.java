package qa.sijill.sijill.shared.util;

import qa.sijill.sijill.shared.config.Brand;
import qa.sijill.sijill.shared.model.Language;

/**
 * Builds the text of messages to staff (proposal 5.4 and wording rules 10.3).
 *
 * <p>Every issue message says <b>what is wrong, why it matters, how to fix it, and that Hafez changed
 * nothing</b>. Staff never see words such as "violation", "non-compliant", "escalated" or
 * "audit failure". Pure functions with no Spring dependencies, so they are easy to read and test.
 */
public final class MessageTemplates {

    private static final String DIVIDER = "\n\n――――――――――――――――\n\n";

    private MessageTemplates() {}

    /** A ready-to-send subject and body. */
    public record Text(String subject, String body) {}

    /** The issue message in the recipient's language ({@code BOTH} = Arabic first, then English). */
    public static Text issue(Language language, String fileName, String detailEn, String detailAr,
                             String whyEn, String whyAr, String stepsEn, String stepsAr) {
        Text ar = issueArabic(fileName, detailAr, whyAr, stepsAr);
        Text en = issueEnglish(fileName, detailEn, whyEn, stepsEn);
        return switch (language) {
            case AR -> ar;
            case EN -> en;
            case BOTH -> new Text(ar.subject() + " | " + en.subject(), ar.body() + DIVIDER + en.body());
        };
    }

    /** The reminder sent after the rule's reminder days (FR-1.5). */
    public static Text reminder(Language language, String fileName) {
        Text ar = new Text("تذكير: ملف ما زال يحتاج إلى انتباهك — " + fileName,
                "هذا تذكير لطيف: الملف «" + fileName + "» ما زال بحاجة إلى تصحيح. "
                        + "افتح الملف في " + Brand.ar() + " لترى الخطوات، ثم اضغط «أصلحته». " + Brand.ar() + " لا يغيّر أي ملف.");
        Text en = new Text("Reminder: a file still needs your attention — " + fileName,
                "A friendly reminder: the file \"" + fileName + "\" still needs a fix. "
                        + "Open it in " + Brand.en() + " to see the steps, then press \"I've fixed it\". " + Brand.en() + " changes no files.");
        return pick(language, ar, en);
    }

    /** Told to the owner when "I've fixed it" was pressed but the re-check still finds the problem (FR-2.6). */
    public static Text stillBroken(Language language, String fileName, String detailEn, String detailAr) {
        Text ar = new Text("ما زال الملف بحاجة إلى تصحيح — " + fileName,
                "أعدنا فحص الملف «" + fileName + "» ولم يتغيّر ما يلي: " + detailAr
                        + "\nراجع الخطوات في " + Brand.ar() + " ثم أعد المحاولة.");
        Text en = new Text("The file still needs a fix — " + fileName,
                "We re-checked \"" + fileName + "\" and this is still the case: " + detailEn
                        + "\nPlease review the steps in " + Brand.en() + " and try again.");
        return pick(language, ar, en);
    }

    private static Text issueArabic(String file, String detail, String why, String steps) {
        return new Text("ملف يحتاج إلى انتباهك — " + file,
                "الملف: " + file
                        + "\n\nما المشكلة: " + detail
                        + "\n\nلماذا يهم: " + why
                        + "\n\nكيف تصلحها:\n" + steps
                        + "\n\nملاحظة: " + Brand.ar() + " (" + Brand.en() + ") لا يغيّر أي ملف، أنت من يجري التصحيح. "
                        + "بعد الانتهاء اضغط «أصلحته» ليعيد النظام فحص الملف.");
    }

    private static Text issueEnglish(String file, String detail, String why, String steps) {
        return new Text("A file needs your attention — " + file,
                "File: " + file
                        + "\n\nWhat is wrong: " + detail
                        + "\n\nWhy it matters: " + why
                        + "\n\nHow to fix it:\n" + steps
                        + "\n\nNote: " + Brand.en() + " changed nothing — you make the fix. "
                        + "When you are done, press \"I've fixed it\" and " + Brand.en() + " will re-check the file.");
    }

    private static Text pick(Language language, Text ar, Text en) {
        return switch (language) {
            case AR -> ar;
            case EN -> en;
            case BOTH -> new Text(ar.subject() + " | " + en.subject(), ar.body() + DIVIDER + en.body());
        };
    }

    /** Picks the Arabic, English or combined variant of a short in-app title. */
    public static String title(Language language, String ar, String en) {
        return switch (language) {
            case AR -> ar;
            case EN -> en;
            case BOTH -> ar + " | " + en;
        };
    }
}
