package qa.sijill.sijill.dashboard.service;

import qa.sijill.sijill.shared.config.Brand;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.dashboard.model.HealthScoreSnapshot;
import qa.sijill.sijill.issues.model.Incident;
import qa.sijill.sijill.issues.service.IncidentService;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.shared.util.ArabicLabels;
import qa.sijill.sijill.shared.util.NameMasker;
import qa.sijill.sijill.shared.util.SchoolCalendar;

/**
 * The weekly report as a PDF (FR-11.6 "Download weekly report (PDF)", proposal 5.4 "Weekly report").
 *
 * <ul>
 *   <li><b>Leadership</b> gets the network report: totals, a table of every scored school, and the week's
 *       major incidents.</li>
 *   <li><b>A principal</b> gets her own school's report: score, counts, the daily trend and the week's incidents.</li>
 * </ul>
 * Reports contain <b>school-level numbers only</b>: no person's name, no file name, no student data
 * (design principles 3 and 11). The footer says who downloaded it, when, and its sensitivity, and the
 * download is audited like every export.
 *
 * <p>The report comes in English or Arabic (the language the person is looking at). Arabic is drawn right to left
 * with the bundled IBM Plex Sans Arabic font (resources/fonts, SIL Open Font License), which also has Latin letters
 * for school names.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8);

    /** A finished report, independent of how it is drawn (so rendering is easy to test). */
    public record Report(String title, String period, List<String[]> summary, List<String> tableHeader,
                         List<String[]> tableRows, List<String> incidents, String footer, boolean arabic) {

        /** An English report. */
        public Report(String title, String period, List<String[]> summary, List<String> tableHeader,
                      List<String[]> tableRows, List<String> incidents, String footer) {
            this(title, period, summary, tableHeader, tableRows, incidents, footer, false);
        }
    }

    private final ScoreService scores;
    private final IncidentService incidents;
    private final AuditPublisher audit;
    private final Clock clock;

    /** Builds and returns the user's weekly report as PDF bytes, in English. Principals and Leadership only. */
    @Transactional
    public byte[] weeklyReportPdf(CurrentUser user) {
        return weeklyReportPdf(user, false);
    }

    /** As {@link #weeklyReportPdf(CurrentUser)}; {@code arabic} draws the report in Arabic, right to left. */
    @Transactional
    public byte[] weeklyReportPdf(CurrentUser user, boolean arabic) {
        if (!user.role().canDownloadWeeklyReport()) { // same rule /api/me uses to show or hide the button
            throw new ForbiddenException("The weekly report is available to principals and Leadership.");
        }
        Words w = new Words(arabic);
        Report report = user.isNetwork() ? networkReport(user, w) : schoolReport(user, w);
        audit.log(user.orgId(), user.siteId(), ActivityActions.EXPORT_CREATED).by(user)
                .on("Report", null).change(null, "weekly-report" + (arabic ? " (ar)" : "")).save();
        return render(report);
    }

    /** Picks the English or the Arabic wording. */
    private record Words(boolean arabic) {
        String t(String en, String ar) {
            return arabic ? ar : en;
        }

        /** English keeps ISO dates; Arabic writes day/month/year, which stays in order inside right-to-left text. */
        String date(LocalDate d) {
            return arabic ? AR_DATE.format(d) : d.toString();
        }
    }

    private static final DateTimeFormatter AR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter AR_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ================================================================== building the data

    private Report networkReport(CurrentUser user, Words w) {
        ScoreService.NetworkOverview o = scores.overview(user);
        List<String[]> summary = List.of(
                new String[] {w.t("Files checked", "الملفات المفحوصة"), String.valueOf(o.filesChecked())},
                new String[] {w.t("Open issues", "المشكلات المفتوحة"), String.valueOf(o.open())},
                new String[] {w.t("Urgent and open", "العاجلة المفتوحة"), String.valueOf(o.urgentOpen())},
                new String[] {w.t("Need follow-up", "تحتاج متابعة"), String.valueOf(o.needFollowUp())},
                new String[] {w.t("Fixed by staff", "أصلحها الموظفون"), String.valueOf(o.fixedByStaff())},
                new String[] {w.t("School files in personal drives", "ملفات مدرسية في مساحات شخصية"), String.valueOf(o.personalDriveFiles())});
        List<String[]> rows = o.schools().stream().map(s -> new String[] {s.siteName(),
                String.format("%.0f", s.score()), label(s.rating(), w), String.valueOf(s.openNormal() + s.openUrgent()),
                String.valueOf(s.openUrgent()), String.valueOf(s.needFollowUp())}).toList();
        return new Report(w.t(Brand.en() + " — Weekly report (network)", Brand.ar() + " — التقرير الأسبوعي (الشبكة)"),
                period(w), summary,
                List.of(w.t("School", "المدرسة"), w.t("Score", "الدرجة"), w.t("Rating", "التقييم"), w.t("Open", "مفتوحة"),
                        w.t("Urgent", "عاجلة"), w.t("Follow-up", "متابعة")), rows,
                weekIncidents(user, w), footer(user, w), w.arabic());
    }

    private Report schoolReport(CurrentUser user, Words w) {
        ScoreService.SiteScore s = scores.siteScore(user, user.siteId());
        List<String[]> summary = List.of(
                new String[] {w.t("School", "المدرسة"), s.siteName()},
                new String[] {w.t("File health score", "درجة سلامة الملفات"), String.format("%.0f (%s)", s.score(), label(s.rating(), w))},
                new String[] {w.t("Files checked", "الملفات المفحوصة"), String.valueOf(s.filesChecked())},
                new String[] {w.t("Open issues", "المشكلات المفتوحة"), String.valueOf(s.openNormal() + s.openUrgent())},
                new String[] {w.t("Urgent and open", "العاجلة المفتوحة"), String.valueOf(s.openUrgent())},
                new String[] {w.t("Need follow-up", "تحتاج متابعة"), String.valueOf(s.needFollowUp())},
                new String[] {w.t("Fixed by staff", "أصلحها الموظفون"), String.valueOf(s.fixed())});
        List<String[]> trend = scores.trend(user, user.siteId(), 7).stream().map(t -> trendRow(t, w)).toList();
        return new Report(w.t(Brand.en() + " — Weekly report (" + s.siteName() + ")", Brand.ar() + " — التقرير الأسبوعي (" + s.siteName() + ")"),
                period(w), summary,
                List.of(w.t("Date", "التاريخ"), w.t("Score", "الدرجة"), w.t("Files", "الملفات"), w.t("Open normal", "مفتوحة عادية"),
                        w.t("Open urgent", "مفتوحة عاجلة")), trend, weekIncidents(user, w), footer(user, w), w.arabic());
    }

    private String[] trendRow(HealthScoreSnapshot t, Words w) {
        return new String[] {w.date(t.getScoreDate()), String.format("%.0f", t.getScore()),
                String.valueOf(t.getFilesChecked()), String.valueOf(t.getOpenNormal()), String.valueOf(t.getOpenUrgent())};
    }

    /** Major incidents of the last seven days, as short plain lines (no file names, no people). */
    private List<String> weekIncidents(CurrentUser user, Words w) {
        Instant since = Instant.now(clock).minusSeconds(7 * 24 * 3600L);
        return incidents.list(user).stream().filter(i -> i.getDetectedAt().isAfter(since))
                .map((Incident i) -> w.date(i.getDetectedAt().atZone(SchoolCalendar.ZONE).toLocalDate()) + "  "
                        + (w.arabic() ? ArabicLabels.of(i.getType()) : i.getType())
                        + (i.getResolvedAt() == null ? w.t(" (open)", " (مفتوحة)") : w.t(" (resolved)", " (تم حلها)"))).toList();
    }

    private String period(Words w) {
        LocalDate today = LocalDate.now(clock);
        return w.arabic() ? "من " + w.date(today.minusDays(6)) + " إلى " + w.date(today) : today.minusDays(6) + " to " + today;
    }

    private String footer(CurrentUser user, Words w) {
        if (w.arabic()) {
            // initials without their final dot: inside right-to-left text a trailing dot would jump to the front (".S.A")
            return "نزّله " + NameMasker.initials(user.name()).replaceAll("\\.$", "") + " في "
                    + AR_TIME.format(Instant.now(clock).atZone(SchoolCalendar.ZONE)) + " | الحساسية: سري | أرقام على مستوى المدرسة فقط؛ لا بيانات شخصية";
        }
        return "Downloaded by " + NameMasker.initials(user.name()) + " at " + Instant.now(clock)
                + " | Sensitivity: Confidential | School-level numbers only; no personal data";
    }

    private String label(ScoreService.ScoreRating rating, Words w) {
        return switch (rating) {
            case GOOD -> w.t("Good", "جيد");
            case WATCH -> w.t("Watch", "تحت المراقبة");
            case NEEDS_ATTENTION -> w.t("Needs attention", "يحتاج اهتماماً");
        };
    }

    // ================================================================== drawing the PDF

    /** Draws the report as an A4 PDF. Pure: the same report always gives a valid PDF. */
    static byte[] render(Report r) {
        return r.arabic() ? renderArabic(r) : renderEnglish(r);
    }

    private static byte[] renderEnglish(Report r) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph(r.title(), TITLE));
            doc.add(new Paragraph("Week: " + r.period(), BODY));
            doc.add(new Paragraph(" "));

            PdfPTable summary = new PdfPTable(new float[] {3, 1});
            summary.setWidthPercentage(100);
            for (String[] row : r.summary()) {
                summary.addCell(cell(row[0], BODY, Element.ALIGN_LEFT));
                summary.addCell(cell(row[1], HEADING, Element.ALIGN_RIGHT));
            }
            doc.add(summary);

            doc.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(r.tableHeader().size());
            table.setWidthPercentage(100);
            r.tableHeader().forEach(h -> table.addCell(cell(h, HEADING, Element.ALIGN_LEFT)));
            for (String[] row : r.tableRows()) {
                for (String value : row) {
                    table.addCell(cell(value, BODY, Element.ALIGN_LEFT));
                }
            }
            doc.add(table);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Major incidents this week", HEADING));
            if (r.incidents().isEmpty()) {
                doc.add(new Paragraph("None.", BODY));
            } else {
                r.incidents().forEach(line -> addLine(doc, line));
            }
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(r.footer(), SMALL));
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not build the weekly report", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    /**
     * The same report in Arabic. Only table cells can run right to left (and join Arabic letters), so every line is
     * a one-cell table without borders, and the tables themselves run right to left (first column on the right).
     */
    private static byte[] renderArabic(Report r) {
        Font title = new Font(arabicFont(true), 18);
        Font heading = new Font(arabicFont(true), 12);
        Font body = new Font(arabicFont(false), 10);
        Font small = new Font(arabicFont(false), 8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(rtlLine(r.title(), title, 6));
            doc.add(rtlLine("الأسبوع: " + r.period(), body, 14));

            PdfPTable summary = rtlTable(new float[] {3, 1});
            for (String[] row : r.summary()) {
                summary.addCell(rtlCell(row[0], body, Element.ALIGN_LEFT));
                summary.addCell(rtlCell(row[1], heading, Element.ALIGN_RIGHT));
            }
            doc.add(summary);
            doc.add(rtlLine(" ", body, 6));

            PdfPTable table = rtlTable(null, r.tableHeader().size());
            r.tableHeader().forEach(h -> table.addCell(rtlCell(h, heading, Element.ALIGN_LEFT)));
            for (String[] row : r.tableRows()) {
                for (String value : row) {
                    table.addCell(rtlCell(value, body, Element.ALIGN_LEFT));
                }
            }
            doc.add(table);
            doc.add(rtlLine(" ", body, 6));

            doc.add(rtlLine("الحوادث المهمة هذا الأسبوع", heading, 4));
            if (r.incidents().isEmpty()) {
                doc.add(rtlLine("لا توجد.", body, 0));
            } else {
                for (String line : r.incidents()) {
                    doc.add(rtlLine(line, body, 0));
                }
            }
            doc.add(rtlLine(" ", body, 6));
            doc.add(rtlLine(r.footer(), small, 0));
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not build the weekly report", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private static PdfPTable rtlTable(float[] widths) {
        return rtlTable(widths, 0);
    }

    private static PdfPTable rtlTable(float[] widths, int columns) {
        PdfPTable t = widths != null ? new PdfPTable(widths) : new PdfPTable(columns);
        t.setWidthPercentage(100);
        t.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        return t;
    }

    /** One line of right-to-left text across the page (a borderless one-cell table). */
    private static PdfPTable rtlLine(String text, Font font, float spaceAfter) {
        PdfPTable t = rtlTable(null, 1);
        PdfPCell c = rtlCell(text, font, Element.ALIGN_LEFT);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingLeft(0);
        c.setPaddingRight(0);
        t.addCell(c);
        t.setSpacingAfter(spaceAfter);
        return t;
    }

    /** A right-to-left cell. In a right-to-left cell ALIGN_LEFT puts the text at the start, which is the right. */
    private static PdfPCell rtlCell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        return c;
    }

    private static BaseFont regular;
    private static BaseFont bold;

    /** The bundled Arabic font, loaded once. */
    private static synchronized BaseFont arabicFont(boolean isBold) {
        try {
            if (regular == null) {
                regular = load("IBMPlexSansArabic-Regular.ttf");
                bold = load("IBMPlexSansArabic-Bold.ttf");
            }
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("The Arabic font for the report could not be loaded", e);
        }
        return isBold ? bold : regular;
    }

    private static BaseFont load(String name) throws IOException, DocumentException {
        try (InputStream in = ReportService.class.getResourceAsStream("/fonts/" + name)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException("Missing font /fonts/" + name));
            }
            return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        }
    }

    private static void addLine(Document doc, String line) {
        try {
            doc.add(new Paragraph(line, BODY));
        } catch (DocumentException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PdfPCell cell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        return c;
    }
}
