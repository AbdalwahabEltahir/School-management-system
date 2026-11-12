package qa.sijill.sijill.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import qa.sijill.sijill.shared.model.BaseEntity;

/**
 * One row of the tamper-evident activity log (FR-5.2, FR-5.3): who did what, to what, with a
 * before/after description.
 *
 * <p><b>Append-only and hash-chained.</b> Each entry stores the hash of the previous entry of the same
 * organisation ({@link #prevHash}) and its own {@link #hash}; changing or removing any row breaks every
 * later hash, which {@code ActivityLogService.verifyChain} detects. The database also rejects UPDATE
 * and DELETE on this table (trigger in migration V2), and Hibernate treats the entity as
 * {@link Immutable}.
 *
 * <p>The log never holds student names, IDs or health details: callers describe changes in neutral terms.
 */
@Getter @Setter
@Immutable
@Entity
@Table(name = "activity_log")
public class ActivityLogEntry extends BaseEntity {

    /** Position in the organisation's chain, starting at 1 with no gaps. */
    @Column(nullable = false)
    private long seq;

    /** Null when Hafez itself acted (a scheduled check, for example). */
    @Column(name = "actor_id")
    private UUID actorId;

    /** Action code such as {@code ISSUE_OPENED} or {@code RULE_UPDATED}; see {@code ActivityActions}. */
    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    /** Subject of the entity, so coordinators can read "their subject's" part of the log. */
    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "before_state", length = 2000)
    private String beforeState;

    @Column(name = "after_state", length = 2000)
    private String afterState;

    /** Hex SHA-256 of the previous entry; 64 zeros for the first entry. */
    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    /** Hex SHA-256 over this entry's content and {@link #prevHash}. */
    @Column(nullable = false, length = 64)
    private String hash;
}
