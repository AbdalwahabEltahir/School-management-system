package qa.hafez.hafez.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import qa.hafez.hafez.shared.util.NameMasker;

/** Name-free exports (5.10): initials instead of names, and CSV cells that cannot run as formulas. */
class ExportServiceTest {

    @Test
    void namesBecomeInitials() {
        assertThat(NameMasker.initials("Ahmed Ali Hassan")).isEqualTo("A.A.H.");
        assertThat(NameMasker.initials("  Fatima   Al-Thani ")).isEqualTo("F.A.");
        assertThat(NameMasker.initials("أحمد علي")).isEqualTo("أ.ع.");
        assertThat(NameMasker.initials("")).isEqualTo("-");
        assertThat(NameMasker.initials(null)).isEqualTo("-");
    }

    @Test
    void cellsThatStartLikeFormulasAreNeutralised() {
        assertThat(ExportService.cell("=HYPERLINK(\"http://evil\")")).startsWith("\"'=");
        assertThat(ExportService.cell("+1+1")).isEqualTo("'+1+1");
        assertThat(ExportService.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }

    @Test
    void cellsWithSeparatorsAreQuoted() {
        assertThat(ExportService.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(ExportService.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ExportService.cell(null)).isEmpty();
        assertThat(ExportService.cell(42)).isEqualTo("42");
    }
}
