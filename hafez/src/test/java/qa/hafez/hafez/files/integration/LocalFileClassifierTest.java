package qa.hafez.hafez.files.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import qa.hafez.hafez.files.integration.FileClassifier.Classification;
import qa.hafez.hafez.files.service.MaskingService;
import qa.hafez.hafez.shared.model.Sensitivity;

class LocalFileClassifierTest {

    private final MaskingService masking = new MaskingService();

    @Test
    void markBookWithStudentIdsIsRestrictedStudentData() {
        String text = "Grade 7B marks - Term 1. Total out of 100. Assessment scores.\n"
                + "S1234567 Student A 88\nS1234568 Student B 91\n";
        Classification c = LocalFileClassifier.analyse("Grade7B_Term1_Marks.xlsx", text, masking);
        assertThat(c.docType()).isEqualTo("Mark book");
        assertThat(c.sensitivity()).isEqualTo(Sensitivity.RESTRICTED);
        assertThat(c.hasStudentData()).isTrue();
        assertThat(c.confidence()).isGreaterThanOrEqualTo(0.8);
        assertThat(c.evidence()).anyMatch(e -> e.contains("2 student ID"));
    }

    @Test
    void evidenceNeverQuotesTheDocument() {
        String text = "Parent contact list. Guardian mobile phone emergency. Ahmed Al-Thani +974 5512 3456 29850123456";
        Classification c = LocalFileClassifier.analyse("contacts.docx", text, masking);
        String all = String.join(" ", c.evidence());
        assertThat(all).doesNotContain("Ahmed").doesNotContain("5512").doesNotContain("29850123456");
        assertThat(c.sensitivity()).isEqualTo(Sensitivity.RESTRICTED); // Qatar ID found
    }

    @Test
    void healthWordsRaiseAPolicyToRestricted() {
        Classification c = LocalFileClassifier.analyse("policy.docx",
                "Attendance policy. Purpose and scope. Responsibilities. Students with diabetes or an allergy need a plan.", masking);
        assertThat(c.docType()).isEqualTo("Policy");
        assertThat(c.hasHealthData()).isTrue();
        assertThat(c.sensitivity()).isEqualTo(Sensitivity.RESTRICTED); // can only be raised, never lowered
    }

    @Test
    void plainPolicyStaysInternalAndConfident() {
        Classification c = LocalFileClassifier.analyse("Attendance_Policy_v2.1.docx",
                "Attendance policy. Purpose. Scope. Responsibilities. Procedure for compliance.", masking);
        assertThat(c.docType()).isEqualTo("Policy");
        assertThat(c.sensitivity()).isEqualTo(Sensitivity.INTERNAL);
        assertThat(c.confidence()).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void arabicDocumentIsRecognised() {
        Classification c = LocalFileClassifier.analyse("تقرير.docx", "تقرير سلوك الطالب: حادثة انضباط ومخالفة سلوك", masking);
        assertThat(c.docType()).isEqualTo("Behaviour record");
    }

    @Test
    void unclearDocumentHasLowConfidenceSoItGoesToReview() {
        Classification c = LocalFileClassifier.analyse("notes_final_v3.docx", "hello world, some random notes", masking);
        assertThat(c.docType()).isEqualTo("Other");
        assertThat(c.confidence()).isLessThan(0.8);
    }

    @Test
    void aContactListIsStudentDataEvenWithoutTheWordStudent() {
        Classification c = LocalFileClassifier.analyse("contacts.docx",
                "Parent contact list. Guardian mobile phone and emergency contacts.", masking);
        assertThat(c.docType()).isEqualTo("Contact list");
        assertThat(c.hasStudentData()).isTrue(); // so a leak of it alerts leadership (Restricted student data)
        assertThat(c.sensitivity()).isEqualTo(Sensitivity.RESTRICTED);
    }

    @Test
    void anExamPaperThatMentionsMarksAndGradesIsStillAnExam() {
        // the real case: "Grade 9 ... exam" with "total marks" in the text was wrongly taken for a mark book
        Classification c = LocalFileClassifier.analyse("Problem 2 - BIG - Grade 9 Science exam public link (School B).pdf",
                "Grade 9 Science, Term 1. Time allowed: 1 hour. Answer all questions. Question 1 (5 marks). Total marks: 40. Score out of 40.", masking);
        assertThat(c.docType()).isEqualTo("Exam");
        assertThat(c.sensitivity()).isEqualTo(Sensitivity.CONFIDENTIAL);
        // while a real mark book with a grade and term in its name stays a mark book
        assertThat(LocalFileClassifier.analyse("Grade8A_Term1_Marks.pdf", "Student marks. Total: 36. Score 33 out of 40. Assessment 1.", masking).docType())
                .isEqualTo("Mark book");
    }

    @Test
    void theExamDateIsReadFromAnExamPaperInEveryCommonFormat() {
        assertThat(LocalFileClassifier.analyse("e.docx", "Grade 9 exam. Time allowed 2 hours. Date: 15 December 2026. Answer all questions.", masking)
                .examDate()).isEqualTo(java.time.LocalDate.of(2026, 12, 15));
        assertThat(LocalFileClassifier.analyse("e.docx", "Science examination, question 1. Exam date 2026-12-03", masking)
                .examDate()).isEqualTo(java.time.LocalDate.of(2026, 12, 3));
        assertThat(LocalFileClassifier.analyse("e.docx", "Examination, answer all. Date 03/12/2026", masking)
                .examDate()).isEqualTo(java.time.LocalDate.of(2026, 12, 3));
        assertThat(LocalFileClassifier.analyse("e.docx", "اختبار الرياضيات أسئلة الزمن ساعتان التاريخ ٢٠٢٦-١٢-١٥", masking)
                .examDate()).isEqualTo(java.time.LocalDate.of(2026, 12, 15));
    }

    @Test
    void anExamWithoutADateAndOtherDocumentsHaveNoExamDate() {
        assertThat(LocalFileClassifier.analyse("e.docx", "Exam. Time allowed 2 hours. Answer all questions.", masking).examDate()).isNull();
        assertThat(LocalFileClassifier.analyse("p.docx", "Attendance policy. Purpose. Scope. Review date 2026-12-15", masking).examDate()).isNull();
        assertThat(LocalFileClassifier.analyse("e.docx", "Exam. Answer all questions. Date 45/13/2026", masking).examDate()).isNull(); // impossible date
    }

    @Test
    void readsTextFromAWordFile() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Lesson plan: learning objective and plenary");
            doc.write(out);
        }
        String text = DocumentTextExtractor.extract("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "plan.docx", out.toByteArray());
        assertThat(text).contains("Lesson plan");
    }

    @Test
    void readsPlainTextAndRejectsUnknownTypes() throws Exception {
        assertThat(DocumentTextExtractor.extract("text/csv", "a.csv", "a,b".getBytes(StandardCharsets.UTF_8))).isEqualTo("a,b");
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> DocumentTextExtractor.extract("image/png", "x.png", new byte[]{1, 2, 3}));
    }
}
