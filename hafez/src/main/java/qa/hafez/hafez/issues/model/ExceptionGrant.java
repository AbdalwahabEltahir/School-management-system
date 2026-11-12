package qa.hafez.hafez.issues.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.hafez.hafez.shared.model.BaseEntity;

/**
 * An allowed exception: a coordinator decided that one file may break one rule until a given date
 * (FR-2.5). Always has a reason and an end date. When it ends — on its date or manually — the rule
 * applies again and the owner is told.
 *
 * <p>Named {@code ExceptionGrant} (table {@code exceptions}) to avoid clashing with {@link Exception}.
 */
@Getter @Setter
@Entity
@Table(name = "exceptions")
public class ExceptionGrant extends BaseEntity {

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    /** The file's subject, copied here so coordinators' queries need no join to files (denormalised on purpose). */
    @Column(name = "subject_id")
    private UUID subjectId;

    /** The issue that was closed as "Allowed exception". */
    @Column(name = "issue_id")
    private UUID issueId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    /** The last day the exception is valid. */
    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    /** Set when the exception was ended (by date or by a person). */
    @Column(name = "ended_at")
    private Instant endedAt;

    /** Null when it ended by date. */
    @Column(name = "ended_by")
    private UUID endedBy;

    public boolean isActive() {
        return endedAt == null;
    }
}
