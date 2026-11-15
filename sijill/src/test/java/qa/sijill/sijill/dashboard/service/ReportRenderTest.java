package qa.sijill.sijill.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The PDF drawing is pure: any report, including an empty one, must give a valid PDF. */
class ReportRenderTest {

    private static boolean isPdf(byte[] bytes) {
        return bytes.length > 500 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
                && new String(bytes, bytes.length - 8, 8, StandardCharsets.US_ASCII).contains("%%EOF");
    }

    @Test
    void aFullReportIsAValidPdf() {
        var report = new ReportService.Report("Hafez — Weekly report (network)", "2026-11-03 to 2026-11-09",
                List.of(new String[] {"Files checked", "540"}, new String[] {"Open issues", "12"}),
                List.of("School", "Score", "Rating"),
                List.of(new String[] {"School A", "92", "Good"}, new String[] {"School B", "61", "Needs attention"}),
                List.of("2026-11-05  EXAM_LEAK (open)"), "Downloaded by L.O. | Confidential");
        assertThat(isPdf(ReportService.render(report))).isTrue();
    }

    @Test
    void anArabicReportIsAValidPdfWithTheArabicFontEmbedded() {
        var report = new ReportService.Report("حافظ — التقرير الأسبوعي (الشبكة)", "من 2026-11-03 إلى 2026-11-09",
                List.of(new String[] {"الملفات المفحوصة", "540"}, new String[] {"المدرسة", "Doha Heritage School"}),
                List.of("المدرسة", "الدرجة", "التقييم"),
                List.<String[]>of(new String[] {"School A", "92", "جيد"}),
                List.of("2026-11-05  تسريب اختبار (مفتوحة)"), "نزّله L.O. | الحساسية: سري", true);
        byte[] pdf = ReportService.render(report);
        assertThat(isPdf(pdf)).isTrue();
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("IBMPlexSansArabic");
    }

    @Test
    void anEmptyArabicReportStillRenders() {
        var report = new ReportService.Report("فارغ", "-", List.of(), List.of("أ"), List.of(), List.of(), "تذييل", true);
        assertThat(isPdf(ReportService.render(report))).isTrue();
    }

    @Test
    void anEmptyReportStillRenders() {
        var report = new ReportService.Report("Empty", "-", List.of(), List.of("A"), List.of(), List.of(), "footer");
        assertThat(isPdf(ReportService.render(report))).isTrue();
    }
}
