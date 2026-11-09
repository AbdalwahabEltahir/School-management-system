package qa.sijill.sijill.people.model;

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
import qa.sijill.sijill.shared.model.DigestMode;
import qa.sijill.sijill.shared.model.Language;
import qa.sijill.sijill.shared.model.Role;

/**
 * A staff member: the directory record plus School IT overrides (section 7 "people").
 *
 * <p>There are no Sijill passwords: a person signs in with their own school account and is matched to
 * this row by {@link #email}. Everyone has their own account; {@link #generic} accounts can never sign
 * in and rule R9 flags files they own.
 */
@Getter @Setter
@Entity
@Table(name = "people")
public class Person extends BaseEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.TEACHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PersonStatus status = PersonStatus.NEW;

    /** The subject this person teaches or coordinates (null for principals, IT and leadership). */
    @Column(name = "subject_id")
    private UUID subjectId;

    /** A shared/generic account (e.g. "exams@school"): cannot sign in; rule R9 flags files it owns. */
    @Column(nullable = false)
    private boolean generic;

    /** True when School IT changed role, site or status by hand; lasts until the directory changes. */
    @Column(nullable = false)
    private boolean overridden;

    /**
     * The role and status the directory reported at the last sync. An override lasts only until these
     * change: when the directory reports something different, the override is dropped (section 4).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "directory_role", length = 20)
    private Role directoryRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "directory_status", length = 20)
    private PersonStatus directoryStatus;

    /** Message language (FR-11.2 "My preferences"). Arabic first. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Language language = Language.AR;

    /** Per-issue messages or a daily summary (FR-11.2). */
    @Enumerated(EnumType.STRING)
    @Column(name = "digest_mode", nullable = false, length = 20)
    private DigestMode digestMode = DigestMode.PER_ISSUE;

    /** When the staff welcome page was sent ("New: welcome not sent" until set, FR-11.5). */
    @Column(name = "welcome_sent_at")
    private Instant welcomeSentAt;

    /** True if this person may sign in to Sijill. */
    public boolean canSignIn() {
        return !generic && status != PersonStatus.LEFT;
    }
}
