package qa.sijill.sijill.rules.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.OrgScopedEntity;
import qa.sijill.sijill.shared.model.Sensitivity;
import qa.sijill.sijill.shared.model.Severity;

/**
 * One immutable version of a rule's content (FR-8.1: "Edit creates a new version").
 *
 * <p><b>Scope.</b> A rule applies to a file when the file's document type is in {@link #docTypes}
 * <i>or</i> its sensitivity is in {@link #sensitivities}; if both lists are empty it applies to all
 * files. (This matches the starter table: R2 lists sensitivities only, R4 document types only,
 * R10 lists both.)
 *
 * <p><b>Approval.</b> A version is <i>effective</i> only once {@link #approvedAt} is set. A school-level
 * rule is approved by the School IT admin who saves it. A network-wide rule must be approved by a
 * <i>different</i> School IT admin or a Leadership member (two-person approval, section 4).
 */
@Getter @Setter
@Entity
@Table(name = "rule_versions")
public class RuleVersion extends OrgScopedEntity {

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 200)
    private String name;

    /** "Why it matters" in English – shown to the owner in every message. */
    @Column(name = "why_it_matters", nullable = false, length = 1000)
    private String whyItMatters;

    /** "Why it matters" in Arabic. */
    @Column(name = "why_it_matters_ar", nullable = false, length = 1000)
    private String whyItMattersAr;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, length = 30)
    private CheckType checkType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity = Severity.NORMAL;

    /** School days until the reminder; the editor offers 1, 2, 3 or 5. */
    @Column(name = "reminder_days", nullable = false)
    private int reminderDays = 2;

    /**
     * Optional check parameter. LOCATION: required folder name (blank = must be in a shared drive).
     * INTERNAL_ACCESS: maximum number of named internal grantees (blank = unlimited).
     */
    @Column(length = 200)
    private String parameter;

    @Column(name = "fix_steps_en", nullable = false, length = 2000)
    private String fixStepsEn;

    @Column(name = "fix_steps_ar", nullable = false, length = 2000)
    private String fixStepsAr;

    /** Scope: document type names this rule applies to (empty = no document-type restriction). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rule_version_doc_types", joinColumns = @JoinColumn(name = "rule_version_id"))
    @Column(name = "doc_type", length = 100)
    private Set<String> docTypes = new HashSet<>();

    /** Scope: sensitivities this rule applies to (empty = no sensitivity restriction). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rule_version_sensitivities", joinColumns = @JoinColumn(name = "rule_version_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "sensitivity", length = 20)
    private Set<Sensitivity> sensitivities = new HashSet<>();

    @Column(name = "proposed_by")
    private UUID proposedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    /** Null while the version is waiting for approval; the rule engine ignores unapproved versions. */
    @Column(name = "approved_at")
    private Instant approvedAt;

    public boolean isApproved() {
        return approvedAt != null;
    }
}
