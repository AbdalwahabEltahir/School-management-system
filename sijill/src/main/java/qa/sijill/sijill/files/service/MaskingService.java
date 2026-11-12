package qa.sijill.sijill.files.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Masks personal data in text <b>before</b> it goes anywhere else, above all before it reaches the AI
 * service (proposal 6 "masking always on", 8.1 "Student data to AI"). A security control kept in its own
 * small class so it can be reviewed and tested alone.
 *
 * <p>Pattern-based masking catches structured identifiers: Qatar ID numbers, +974 phone numbers, e-mail
 * addresses and student IDs. Names cannot be found reliably with patterns; the classifier replaces
 * detected names with {@code [NAME]} itself, and the evidence lines it returns must already be masked
 * (see {@code FileClassifier}). Logs and issue texts never contain the originals.
 */
@Service
public class MaskingService {

    /** Qatar ID card number: 11 digits, Western or Arabic-Indic. */
    private static final Pattern QATAR_ID = Pattern.compile("(?<![\\d٠-٩])[\\d٠-٩]{11}(?![\\d٠-٩])");
    /** Qatar phone numbers: +974 / 00974 followed by 8 digits, with optional spaces or dashes. */
    private static final Pattern PHONE = Pattern.compile("(?:\\+|00)?974[\\s-]?[\\d٠-٩]{4}[\\s-]?[\\d٠-٩]{4}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    /** Student ID: "S" or "STU" followed by 5 – 9 digits (the schools' format is to be confirmed). */
    private static final Pattern STUDENT_ID = Pattern.compile("\\b(?:STU|S)[-]?\\d{5,9}\\b", Pattern.CASE_INSENSITIVE);

    /** Returns the text with identifiers replaced by placeholders such as {@code [ID]} and {@code [PHONE]}. */
    public String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = EMAIL.matcher(text).replaceAll("[EMAIL]");
        out = PHONE.matcher(out).replaceAll("[PHONE]");
        out = STUDENT_ID.matcher(out).replaceAll("[STUDENT_ID]");
        out = QATAR_ID.matcher(out).replaceAll("[ID]");
        return out;
    }

    /** True if the text still contains a recognisable identifier (a last-line check before sending to AI). */
    public boolean containsIdentifier(String text) {
        return text != null && !text.equals(mask(text));
    }
}
