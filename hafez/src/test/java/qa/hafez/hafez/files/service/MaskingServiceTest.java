package qa.hafez.hafez.files.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Masking runs before any text goes to the AI service (proposal 6, 8.1). */
class MaskingServiceTest {

    private final MaskingService masking = new MaskingService();

    @Test
    void masksQatarPhoneNumbers() {
        assertThat(masking.mask("Call +974 5555 1234 today")).isEqualTo("Call [PHONE] today");
        assertThat(masking.mask("Call 00974-55551234")).isEqualTo("Call [PHONE]");
    }

    @Test
    void masksElevenDigitIdNumbersInWesternAndArabicIndicDigits() {
        assertThat(masking.mask("ID 29850123456")).isEqualTo("ID [ID]");
        assertThat(masking.mask("الرقم ٢٩٨٥٠١٢٣٤٥٦")).isEqualTo("الرقم [ID]");
    }

    @Test
    void masksEmailsAndStudentIds() {
        assertThat(masking.mask("mail ahmed@school-a.example.edu now")).isEqualTo("mail [EMAIL] now");
        assertThat(masking.mask("student S1234567 passed")).isEqualTo("student [STUDENT_ID] passed");
    }

    @Test
    void doesNotTouchOrdinaryNumbersLikeMarks() {
        assertThat(masking.mask("Grade 5, score 87 out of 100")).isEqualTo("Grade 5, score 87 out of 100");
        assertThat(masking.containsIdentifier("Grade 5, score 87")).isFalse();
    }

    @Test
    void reportsWhetherAnIdentifierRemains() {
        assertThat(masking.containsIdentifier("ID 29850123456")).isTrue();
    }

    @Test
    void handlesNullAndEmptyText() {
        assertThat(masking.mask(null)).isNull();
        assertThat(masking.mask("")).isEmpty();
    }
}
