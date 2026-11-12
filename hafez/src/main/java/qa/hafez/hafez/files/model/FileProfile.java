package qa.hafez.hafez.files.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.hafez.hafez.organisation.model.DocType;
import qa.hafez.hafez.shared.model.BaseEntity;
import qa.hafez.hafez.shared.model.Sensitivity;

/**
 * What Hafez understood about a file (FR-1.2): type, subject, student/health data, sensitivity,
 * confidence and <b>masked</b> evidence. Never holds names, IDs or health details (section 7).
 *
 * <p>Produced by the classifier (stage 3 of the pipeline) or set by a coordinator, who answers
 * "Needs review" items and thereby retrains the checker (FR-2.3).
 */
@Getter @Setter
@Entity
@Table(name = "file_profiles")
public class FileProfile extends BaseEntity {

    @Column(name = "file_id", nullable = false, unique = true)
    private UUID fileId;

    /** Name of the {@link DocType}, e.g. "Mark book". */
    @Column(name = "doc_type", length = 100)
    private String docType;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "has_student_data", nullable = false)
    private boolean hasStudentData;

    @Column(name = "has_health_data", nullable = false)
    private boolean hasHealthData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Sensitivity sensitivity = Sensitivity.INTERNAL;

    /** Classifier confidence, 0.0 – 1.0. Below the configured threshold the file goes to "Needs review". */
    @Column(nullable = false)
    private double confidence;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    /** True for unreadable or low-confidence files: a person decides (design principle 4). */
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    /** Set when a coordinator classified the file by hand. */
    @Column(name = "classified_by")
    private UUID classifiedBy;

    /** Masked evidence lines, e.g. "A table of 28 [NAME] entries next to term marks". */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "file_profile_evidence", joinColumns = @JoinColumn(name = "file_profile_id"))
    @Column(name = "evidence", length = 500)
    private List<String> evidence = new ArrayList<>();
}
