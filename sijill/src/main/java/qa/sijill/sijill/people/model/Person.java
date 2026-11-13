package qa.sijill.sijill.people.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.BaseEntity;
import qa.sijill.sijill.shared.model.DigestMode;
import qa.sijill.sijill.shared.model.Language;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;

/**
 * A staff member: the directory record plus School IT overrides (section 7 "people").
 *
 * <p>There are no Hafez passwords: a person signs in with their own school account and is matched to
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

    /** Contact number the person keeps on their own profile (optional). */
    @Column(length = 30)
    private String phone;

    /** Profile photo (small image) and its type; null when none was uploaded. */
    @Column(name = "photo")
    private byte[] photo;

    @Column(name = "photo_type", length = 30)
    private String photoType;

    /** Kinds of alert this person has switched off, comma separated (see {@link NotificationKind#mutable()}). */
    @Column(name = "muted_kinds", nullable = false, length = 200)
    private String mutedKinds = "";

    /** The alert kinds this person has switched off. */
    public java.util.Set<NotificationKind> mutedSet() {
        java.util.Set<NotificationKind> out = java.util.EnumSet.noneOf(NotificationKind.class);
        for (String k : mutedKinds == null ? new String[0] : mutedKinds.split(",")) {
            try {
                if (!k.isBlank()) {
                    out.add(NotificationKind.valueOf(k.trim()));
                }
            } catch (IllegalArgumentException ignored) {
                // an unknown kind from an older version is simply not muted
            }
        }
        return out;
    }

    public void setMuted(java.util.Set<NotificationKind> kinds) {
        this.mutedKinds = kinds.stream().filter(NotificationKind::mutable).map(Enum::name).sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    public boolean hasMuted(NotificationKind kind) {
        return kind.mutable() && mutedSet().contains(kind);
    }

    /** A student's ID, given to them when their account is created. A parent uses it to register and follow the student. */
    @Column(name = "student_code", length = 20)
    private String studentCode;

    @PrePersist
    void assignStudentCode() {
        if (role == Role.STUDENT && studentCode == null) {
            studentCode = StudentCodes.next();
        }
    }

    /** When the staff welcome page was sent ("New: welcome not sent" until set, FR-11.5). */
    @Column(name = "welcome_sent_at")
    private Instant welcomeSentAt;

    /** True if this person may sign in to Hafez. */
    public boolean canSignIn() {
        return !generic && status != PersonStatus.LEFT;
    }
}
