package qa.sijill.sijill.issues.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.BaseEntity;
import qa.sijill.sijill.shared.model.Severity;
import qa.sijill.sijill.shared.security.AccessScope;

/**
 * An issue is <b>one rule broken by one file</b> (section 5). It is the unit that owners are told about,
 * that coordinators follow up and that only a re-check can close (FR-1.4).
 *
 * <p>At most one <i>open</i> issue exists per (file, rule): enforced by a partial unique index in the
 * migration and by {@code RuleEngineService}. The status workflow is described on
 * {@link IssueStatus} and implemented in {@code IssueService} / {@code IssueWorkflowService}.
 */
@Getter @Setter
@Entity
@Table(name = "issues")
public class Issue extends BaseEntity {

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    /** The exact rule version in force when the issue was raised (FR-5.3). */
    @Column(name = "rule_version_id", nullable = false)
    private UUID ruleVersionId;

    /** The {@code Person} who owns the file; null if the owner is not in the directory (coordinator handles it). */
    @Column(name = "owner_id")
    private UUID ownerId;

    /**
     * The file's subject, copied here so the coordinator's "my subject" query is a single indexed lookup
     * (denormalised on purpose; refreshed on every re-check).
     */
    @Column(name = "subject_id")
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IssueStatus status = IssueStatus.OWNER_NOTIFIED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity = Severity.NORMAL;

    /** What is wrong, in English and Arabic, as found by the latest check. */
    @Column(name = "detail_en", length = 1000)
    private String detailEn;

    @Column(name = "detail_ar", length = 1000)
    private String detailAr;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    /** When the owner's reminder is due (FR-1.5); null once sent or when closed. */
    @Column(name = "reminder_due_at")
    private Instant reminderDueAt;

    /** When the issue becomes "Needs follow-up" if still unresolved: one school day after the reminder. */
    @Column(name = "follow_up_due_at")
    private Instant followUpDueAt;

    /** The owner's note when they pressed "This looks wrong" (required, FR-11.4). */
    @Column(name = "owner_note", length = 1000)
    private String ownerNote;

    public boolean isOpen() {
        return status.isOpen();
    }

    /** What the access policy needs to know about this issue: school, subject and owner. */
    public AccessScope scope() {
        return new AccessScope(getSiteId(), subjectId, ownerId, null);
    }
}
