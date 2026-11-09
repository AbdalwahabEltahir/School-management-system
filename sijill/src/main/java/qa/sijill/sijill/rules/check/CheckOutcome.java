package qa.sijill.sijill.rules.check;

/**
 * Result of a {@link FileCheck}: either fine, or broken with a plain-words description in English and
 * Arabic ("what is wrong"). The description goes into the issue and into the message to the owner.
 * It must never contain student names, IDs or health details (section 7).
 */
public record CheckOutcome(boolean broken, String detailEn, String detailAr) {

    private static final CheckOutcome OK = new CheckOutcome(false, null, null);

    public static CheckOutcome ok() {
        return OK;
    }

    public static CheckOutcome broken(String detailEn, String detailAr) {
        return new CheckOutcome(true, detailEn, detailAr);
    }
}
