package qa.hafez.hafez.dashboard.service;

import qa.hafez.hafez.shared.config.Brand;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.dashboard.model.HealthScoreSnapshot;
import qa.hafez.hafez.issues.model.Incident;
import qa.hafez.hafez.issues.service.IncidentService;
import qa.hafez.hafez.shared.events.ActivityActions;
import qa.hafez.hafez.shared.events.AuditPublisher;
import qa.hafez.hafez.shared.exception.ForbiddenException;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.security.CurrentUser;
import qa.hafez.hafez.shared.util.NameMasker;
import qa.hafez.hafez.shared.util.SchoolCalendar;

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
 * <p>Text is English for now. Arabic needs an embedded right-to-left font and text shaping, which is not
 * bundled yet; the numbers and layout are language-neutral.
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
                         List<String[]> tableRows, List<String> incidents, String footer) {}

    private final ScoreService scores;
    private final IncidentService incidents;
    private final AuditPublisher audit;
    private final Clock clock;

    /** Builds and returns the user's weekly report as PDF bytes. Principals and Leadership only. */
    @Transactional
    public byte[] weeklyReportPdf(CurrentUser user) {
        if (!user.role().canDownloadWeeklyReport()) { // same rule /api/me uses to show or hide the button
            throw new ForbiddenException("The weekly report is available to principals and Leadership.");
        }
        Report report = user.is(Role.LEADERSHIP) ? networkReport(user) : schoolReport(user);
        audit.log(user.orgId(), user.siteId(), ActivityActions.EXPORT_CREATED).by(user)
                .on("Report", null).change(null, "weekly-report").save();
        return render(report);
    }

    // ================================================================== building the data

    private Report networkReport(CurrentUser user) {
        ScoreService.NetworkOverview o = scores.overview(user);
        List<String[]> summary = List.of(
                new String[] {"Files checked", String.valueOf(o.filesChecked())},
                new String[] {"Open issues", String.valueOf(o.open())},
                new String[] {"Urgent and open", String.valueOf(o.urgentOpen())},
                new String[] {"Need follow-up", String.valueOf(o.needFollowUp())},
                new String[] {"Fixed by staff", String.valueOf(o.fixedByStaff())},
                new String[] {"School files in personal drives", String.valueOf(o.personalDriveFiles())});
        List<String[]> rows = o.schools().stream().map(s -> new String[] {s.siteName(),
                String.format("%.0f", s.score()), label(s.rating()), String.valueOf(s.openNormal() + s.openUrgent()),
                String.valueOf(s.openUrgent()), String.valueOf(s.needFollowUp())}).toList();
        return new Report(Brand.en() + " — Weekly report (network)", period(), summary,
                List.of("School", "Score", "Rating", "Open", "Urgent", "Follow-up"), rows,
                weekIncidents(user), footer(user));
    }

    private Report schoolReport(CurrentUser user) {
        ScoreService.SiteScore s = scores.siteScore(user, user.siteId());
        List<String[]> summary = List.of(
                new String[] {"School", s.siteName()},
                new String[] {"File health score", String.format("%.0f (%s)", s.score(), label(s.rating()))},
                new String[] {"Files checked", String.valueOf(s.filesChecked())},
                new String[] {"Open issues", String.valueOf(s.openNormal() + s.openUrgent())},
                new String[] {"Urgent and open", String.valueOf(s.openUrgent())},
                new String[] {"Need follow-up", String.valueOf(s.needFollowUp())},
                new String[] {"Fixed by staff", String.valueOf(s.fixed())});
        List<String[]> trend = scores.trend(user, user.siteId(), 7).stream().map(this::trendRow).toList();
        return new Report(Brand.en() + " — Weekly report (" + s.siteName() + ")", period(), summary,
                List.of("Date", "Score", "Files", "Open normal", "Open urgent"), trend, weekIncidents(user), footer(user));
    }

    private String[] trendRow(HealthScoreSnapshot t) {
        return new String[] {t.getScoreDate().toString(), String.format("%.0f", t.getScore()),
                String.valueOf(t.getFilesChecked()), String.valueOf(t.getOpenNormal()), String.valueOf(t.getOpenUrgent())};
    }

    /** Major incidents of the last seven days, as short plain lines (no file names, no people). */
    private List<String> weekIncidents(CurrentUser user) {
        Instant since = Instant.now(clock).minusSeconds(7 * 24 * 3600L);
        return incidents.list(user).stream().filter(i -> i.getDetectedAt().isAfter(since))
                .map((Incident i) -> i.getDetectedAt().atZone(SchoolCalendar.ZONE).toLocalDate() + "  "
                        + i.getType() + (i.getResolvedAt() == null ? " (open)" : " (resolved)")).toList();
    }

    private String period() {
        LocalDate today = LocalDate.now(clock);
        return today.minusDays(6) + " to " + today;
    }

    private String footer(CurrentUser user) {
        return "Downloaded by " + NameMasker.initials(user.name()) + " at " + Instant.now(clock)
                + " | Sensitivity: Confidential | School-level numbers only; no personal data";
    }

    private String label(ScoreService.ScoreRating rating) {
        return switch (rating) {
            case GOOD -> "Good";
            case WATCH -> "Watch";
            case NEEDS_ATTENTION -> "Needs attention";
        };
    }

    // ================================================================== drawing the PDF

    /** Draws the report as an A4 PDF. Pure: the same report always gives a valid PDF. */
    static byte[] render(Report r) {
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
