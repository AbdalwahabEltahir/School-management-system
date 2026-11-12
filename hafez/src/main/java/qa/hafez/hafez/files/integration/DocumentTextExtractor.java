package qa.hafez.hafez.files.integration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * Turns a document's bytes into plain text, in memory (stage 2 "reader"). Handles plain text and CSV, PDF,
 * Word (.docx), Excel (.xlsx) and PowerPoint (.pptx). A scanned PDF has no text layer and yields an empty
 * string: the caller then sends the file to "Needs review" (no OCR yet).
 */
final class DocumentTextExtractor {

    /** Never read more than this much text from one file. */
    static final int MAX_CHARS = 200_000;
    private static final int MAX_PDF_PAGES = 60;

    private DocumentTextExtractor() {}

    /** @throws IOException if the file is damaged or in a format that cannot be read */
    static String extract(String mimeType, String fileName, byte[] data) throws IOException {
        String type = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String text;
        if (type.startsWith("text/") || name.endsWith(".txt") || name.endsWith(".csv")) {
            text = new String(data, StandardCharsets.UTF_8);
        } else if (type.equals("application/pdf") || name.endsWith(".pdf")) {
            text = pdf(data);
        } else if (type.contains("wordprocessingml") || name.endsWith(".docx")) {
            text = word(data);
        } else if (type.contains("spreadsheetml") || name.endsWith(".xlsx")) {
            text = excel(data);
        } else if (type.contains("presentationml") || name.endsWith(".pptx")) {
            text = powerPoint(data);
        } else {
            throw new IOException("Unsupported file type: " + (type.isEmpty() ? name : type));
        }
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }

    private static String pdf(byte[] data) throws IOException {
        try (PDDocument doc = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(MAX_PDF_PAGES);
            return stripper.getText(doc);
        }
    }

    private static String word(byte[] data) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private static String excel(byte[] data) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(data));
             XSSFExcelExtractor extractor = new XSSFExcelExtractor(book)) {
            extractor.setIncludeSheetNames(false);
            return extractor.getText();
        }
    }

    private static String powerPoint(byte[] data) throws IOException {
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(data));
             SlideShowExtractor<?, ?> extractor = new SlideShowExtractor<>(show)) {
            return extractor.getText();
        }
    }
}
