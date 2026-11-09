package qa.sijill.sijill.shared.util;

/**
 * Name-free exports (proposal 5.10, design principle 11): downloads and shared reports use initials,
 * never full names. Full names appear on screen only, to permitted roles.
 */
public final class NameMasker {

    private NameMasker() {}

    /**
     * "Ahmed Ali Hassan" becomes "A.A.H.". Works for Arabic names too (first letter of each word).
     * A blank name gives "-".
     */
    public static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "-";
        }
        StringBuilder out = new StringBuilder();
        for (String word : fullName.trim().split("\\s+")) {
            out.appendCodePoint(word.codePointAt(0)).append('.');
        }
        return out.toString();
    }
}
