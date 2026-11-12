package qa.hafez.hafez.files.integration;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import qa.hafez.hafez.files.model.SchoolFile;
import qa.hafez.hafez.files.service.MaskingService;
import qa.hafez.hafez.organisation.model.Site;
import qa.hafez.hafez.organisation.repository.SiteRepository;
import qa.hafez.hafez.shared.model.Sensitivity;
import qa.hafez.hafez.workspace.WorkspaceConnector;
import qa.hafez.hafez.workspace.WorkspaceContent;

/**
 * Stages 2 and 3 without an AI service: reads the document <b>in memory</b>, runs the hard checks and
 * guesses the document type from keywords (English and Arabic).
 *
 * <ul>
 *   <li><b>Hard checks</b> (Qatar ID numbers, +974 phones, student IDs, health words) can only
 *       <i>raise</i> sensitivity, never lower it.</li>
 *   <li><b>Document type</b>: the type whose keywords appear most wins. Few matches, or a close second,
 *       means low confidence, and the file goes to "Needs review" so a person decides.</li>
 *   <li>A file that cannot be read (scan, damaged, too large) returns empty, so it goes to
 *       "Needs review" and is never reported fine.</li>
 *   <li>The text is discarded afterwards. The evidence lines are generic (counts and matched
 *       keywords), never excerpts, names or numbers from the document.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "hafez.google.drive.enabled", havingValue = "true")
public class LocalFileClassifier implements FileClassifier {

    private static final Logger log = LoggerFactory.getLogger(LocalFileClassifier.class);
    /** Bumped when the rules change, so the next check re-reads files labelled by an older version. */
    public static final String MODEL_VERSION = "local-rules-2";

    /** Keywords per document type. Names must match the starter document types of the organisation. */
    private static final Map<String, List<String>> KEYWORDS = new LinkedHashMap<>();
    static {
        KEYWORDS.put("Mark book", List.of("marks", "mark sheet", "mark book", "gradebook", "score", "total", "out of", "assessment",
                "درجات", "علامات", "الدرجة", "المجموع", "كشف"));
        KEYWORDS.put("Student report", List.of("report card", "progress report", "teacher comment", "student report", "تقرير", "بطاقة", "شهادة"));
        KEYWORDS.put("Behaviour record", List.of("behaviour", "behavior", "incident", "detention", "discipline", "misconduct",
                "سلوك", "انضباط", "حادثة", "مخالفة"));
        KEYWORDS.put("Medical record", List.of("allergy", "medical", "medication", "doctor", "diagnosis", "nurse", "clinic",
                "حساسية", "طبي", "دواء", "تشخيص", "الممرض"));
        KEYWORDS.put("Special-needs record", List.of("iep", "special needs", "learning support", "accommodation", "individual education",
                "احتياجات خاصة", "حاجات خاصة", "دعم تعليمي"));
        KEYWORDS.put("Contact list", List.of("contact", "guardian", "parent", "mobile", "phone", "emergency", "address",
                "ولي أمر", "هاتف", "جوال", "اتصال", "طوارئ"));
        KEYWORDS.put("Exam", List.of("exam", "examination", "time allowed", "answer all", "question 1", "invigilat",
                "اختبار", "امتحان", "أسئلة", "الزمن"));
        KEYWORDS.put("Policy", List.of("policy", "procedure", "scope", "purpose", "responsibilities", "compliance",
                "سياسة", "إجراءات", "الغرض", "المسؤوليات"));
        KEYWORDS.put("Lesson plan", List.of("lesson plan", "learning objective", "starter", "plenary", "learning outcome", "resources",
                "خطة الدرس", "أهداف التعلم", "نواتج التعلم", "الدرس"));
        KEYWORDS.put("Worksheet", List.of("worksheet", "exercise", "name:", "activity", "complete the", "ورقة عمل", "تمرين", "نشاط"));
        KEYWORDS.put("Circular", List.of("circular", "dear parents", "notice", "we would like to inform", "تعميم", "أولياء الأمور", "نحيطكم علما"));
        KEYWORDS.put("Meeting minutes", List.of("minutes", "attendees", "agenda", "action items", "apologies", "محضر", "اجتماع", "جدول الأعمال", "الحضور"));
        KEYWORDS.put("Timetable", List.of("timetable", "period 1", "period 2", "schedule", "الجدول", "الحصة", "حصة"));
    }
    /** The starter document types' default sensitivity (kept here so this module does not depend on rules). */
    private static final Map<String, Sensitivity> DEFAULT_SENSITIVITY = Map.ofEntries(
            Map.entry("Mark book", Sensitivity.RESTRICTED), Map.entry("Student report", Sensitivity.RESTRICTED),
            Map.entry("Behaviour record", Sensitivity.RESTRICTED), Map.entry("Medical record", Sensitivity.RESTRICTED),
            Map.entry("Special-needs record", Sensitivity.RESTRICTED), Map.entry("Contact list", Sensitivity.RESTRICTED),
            Map.entry("Exam", Sensitivity.CONFIDENTIAL), Map.entry("Policy", Sensitivity.INTERNAL),
            Map.entry("Lesson plan", Sensitivity.INTERNAL), Map.entry("Worksheet", Sensitivity.INTERNAL),
            Map.entry("Circular", Sensitivity.PUBLIC), Map.entry("Meeting minutes", Sensitivity.INTERNAL),
            Map.entry("Timetable", Sensitivity.PUBLIC), Map.entry("Other", Sensitivity.INTERNAL));
    private static final List<String> HEALTH_WORDS = List.of("allergy", "allergic", "medication", "diagnosis", "asthma", "diabetes",
            "epilep", "حساسية", "دواء", "تشخيص", "ربو", "سكري", "صرع");
    private static final List<String> STUDENT_WORDS = List.of("student", "pupil", "طالب", "طلاب", "طالبة");

    private final ObjectProvider<WorkspaceConnector> connector;
    private final SiteRepository sites;
    private final MaskingService masking;

    public LocalFileClassifier(ObjectProvider<WorkspaceConnector> connector, SiteRepository sites, MaskingService masking) {
        this.connector = connector;
        this.sites = sites;
        this.masking = masking;
    }

    @Override
    public Optional<Classification> classify(SchoolFile file) {
        WorkspaceConnector workspace = connector.getIfAvailable();
        Site site = sites.findById(file.getSiteId()).orElse(null);
        if (workspace == null || site == null) {
            return Optional.empty();
        }
        try {
            Optional<WorkspaceContent> content = workspace.readContent(site.getDomain(), file.getWorkspaceFileId());
            if (content.isEmpty()) {
                return Optional.empty();
            }
            String text = DocumentTextExtractor.extract(content.get().mimeType(), file.getName(), content.get().data());
            if (text == null || text.isBlank()) {
                return Optional.empty(); // a scan or an empty document: a person decides
            }
            return Optional.of(analyse(file.getName(), text, masking));
        } catch (Exception e) { // unreadable, damaged, unsupported: never stop the run, never guess
            log.warn("Could not read file {}: {}", file.getWorkspaceFileId(), e.getMessage());
            return Optional.empty();
        }
    }

    /** The decision itself, separate from reading so it can be tested alone. */
    static Classification analyse(String fileName, String text, MaskingService masking) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String haystack = (name + "\n" + text).toLowerCase(Locale.ROOT);
        String masked = masking.mask(text);
        int studentIds = count(masked, "[STUDENT_ID]");
        int qatarIds = count(masked, "[ID]");
        int phones = count(masked, "[PHONE]");
        boolean health = HEALTH_WORDS.stream().anyMatch(haystack::contains);

        // document type: the one whose keywords appear most
        // A word in the file NAME counts three times: "...Science exam.pdf" is an exam even if its text also says "total marks".
        String best = "Other";
        List<String> bestHits = List.of();
        int bestScore = 0;
        int second = 0;
        for (Map.Entry<String, List<String>> e : KEYWORDS.entrySet()) {
            List<String> hits = e.getValue().stream().filter(haystack::contains).toList();
            int score = hits.size() + 2 * (int) hits.stream().filter(name::contains).count();
            if (score > bestScore) {
                second = bestScore;
                best = e.getKey();
                bestHits = hits;
                bestScore = score;
            } else if (score > second) {
                second = score;
            }
        }
        double confidence = switch (bestScore) {
            case 0 -> 0.4;
            case 1 -> 0.55;
            case 2 -> 0.75;
            case 3 -> 0.85;
            default -> 0.92;
        };
        if (bestScore > 0 && bestScore - second <= 1) {
            confidence -= 0.15; // a close second: not sure
        }

        // hard checks raise sensitivity, never lower it
        Sensitivity sensitivity = defaultSensitivity(best);
        // a student-record type (marks, reports, behaviour, medical, special needs, contacts) is about students
        // by definition, so rule alerts to leadership do not depend on the text also saying "student"
        boolean studentData = studentIds > 0 || qatarIds > 0 || isStudentRecord(best);
        if (studentIds > 0 || qatarIds > 0 || health || phones >= 5) {
            sensitivity = Sensitivity.RESTRICTED;
        }

        List<String> evidence = new ArrayList<>();
        evidence.add(bestHits.isEmpty() ? "No words that point to a known document type were found."
                : "Looks like a " + best.toLowerCase(Locale.ROOT) + ": matched " + bestHits.size() + " typical word(s), e.g. "
                        + String.join(", ", bestHits.stream().limit(4).toList()) + ".");
        if (studentIds > 0) {
            evidence.add("Contains " + studentIds + " student ID number(s).");
        }
        if (qatarIds > 0) {
            evidence.add("Contains " + qatarIds + " Qatar ID number(s).");
        }
        if (phones > 0) {
            evidence.add("Contains " + phones + " phone number(s).");
        }
        if (health) {
            evidence.add("Contains health-related words.");
        }
        LocalDate examDate = "Exam".equals(best) ? extractExamDate(text) : null;
        if (examDate != null) {
            evidence.add("An exam date was found: " + examDate + ".");
        }
        return new Classification(best, studentData, health, sensitivity, Math.max(0, confidence), evidence, MODEL_VERSION,
                examDate);
    }

    private static final Pattern ISO_DATE = Pattern.compile("(?<!\\d)(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)");
    private static final Pattern DAY_FIRST_DATE = Pattern.compile("(?<!\\d)(\\d{1,2})[-/.](\\d{1,2})[-/.](20\\d{2})(?!\\d)");
    private static final Pattern DAY_MONTH_NAME = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?,?\\s+(20\\d{2})",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> MONTHS = List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct",
            "nov", "dec");

    /**
     * The first date printed in an exam paper's opening text (ISO, day-first numeric, or "15 December 2026";
     * Arabic-Indic digits are accepted). Null when there is none, so rule R6 stays quiet instead of guessing.
     */
    static LocalDate extractExamDate(String text) {
        String head = text.length() > 3000 ? text.substring(0, 3000) : text;
        StringBuilder western = new StringBuilder(head.length());
        for (char c : head.toCharArray()) { // Arabic-Indic digits to 0-9
            western.append(c >= '\u0660' && c <= '\u0669' ? (char) ('0' + (c - '\u0660')) : c);
        }
        String t = western.toString();
        LocalDate best = null;
        int bestAt = Integer.MAX_VALUE;
        Matcher m = ISO_DATE.matcher(t);
        while (m.find()) {
            LocalDate d = date(m.group(1), m.group(2), m.group(3));
            if (d != null && m.start() < bestAt) { best = d; bestAt = m.start(); }
        }
        m = DAY_FIRST_DATE.matcher(t);
        while (m.find()) {
            LocalDate d = date(m.group(3), m.group(2), m.group(1));
            if (d != null && m.start() < bestAt) { best = d; bestAt = m.start(); }
        }
        m = DAY_MONTH_NAME.matcher(t);
        while (m.find()) {
            int month = MONTHS.indexOf(m.group(2).toLowerCase(Locale.ROOT)) + 1;
            LocalDate d = date(m.group(3), String.valueOf(month), m.group(1));
            if (d != null && m.start() < bestAt) { best = d; bestAt = m.start(); }
        }
        return best;
    }

    private static LocalDate date(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static Sensitivity defaultSensitivity(String docType) {
        return DEFAULT_SENSITIVITY.getOrDefault(docType, Sensitivity.INTERNAL);
    }

    private static boolean isStudentRecord(String docType) {
        return List.of("Mark book", "Student report", "Behaviour record", "Medical record", "Special-needs record", "Contact list")
                .contains(docType);
    }

    private static int count(String text, String token) {
        int n = 0;
        for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + token.length())) {
            n++;
        }
        return n;
    }
}
