package qa.sijill.sijill.files.integration;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import qa.sijill.sijill.files.model.SchoolFile;
import qa.sijill.sijill.shared.model.Sensitivity;

/**
 * Stages 2 and 3 of the pipeline: the reader and the "understanding" step (proposal 6).
 *
 * <p>An implementation reads the file <b>in memory</b>, masks names and IDs, runs the hard checks
 * (ID numbers, +974 phone numbers, student IDs, health words — which can only <i>raise</i> sensitivity)
 * and asks the AI service for a profile. The text is discarded afterwards: Sijill stores metadata and
 * masked evidence only (section 7). Returning {@link Optional#empty()} means the file could not be
 * read; it then goes to "Needs review" so a person decides — a Restricted file is never reported fine.
 *
 * <p>No implementation is bundled yet; without one, files stay unclassified and rules that depend on
 * document type or sensitivity route to "Needs review".
 */
public interface FileClassifier {

    Optional<Classification> classify(SchoolFile file);

    /**
     * The classifier's answer.
     *
     * @param evidence masked evidence lines, e.g. "A table of 28 [NAME] entries next to term marks"
     * @param examDate the date printed on an exam paper, or null; rule R6 needs it to catch an early leak
     */
    record Classification(String docType, boolean hasStudentData, boolean hasHealthData, Sensitivity sensitivity,
                          double confidence, List<String> evidence, String modelVersion, LocalDate examDate) {
    }
}
