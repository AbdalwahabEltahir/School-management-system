package qa.hafez.hafez.files.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.hafez.hafez.shared.model.BaseEntity;
import qa.hafez.hafez.shared.model.Sensitivity;
import qa.hafez.hafez.shared.security.AccessScope;
import qa.hafez.hafez.workspace.DriveType;

/**
 * Hafez's record of one file in a school workspace: <b>metadata only, never content</b> (section 7).
 *
 * <p>The row is created and refreshed by {@code FileService.upsertFromWorkspace} from the read-only
 * connector. Sharing grants live in {@link FilePermission}; what Hafez understood about the file lives
 * in {@link FileProfile}.
 */
@Getter @Setter
@Entity
@Table(name = "files")
public class SchoolFile extends BaseEntity {

    /** The file's ID in the school workspace (unique across the system). */
    @Column(name = "workspace_file_id", nullable = false, unique = true)
    private String workspaceFileId;

    @Column(nullable = false, length = 500)
    private String name;

    /** E-mail of the file's owner in the workspace; matched to a {@code Person}. */
    @Column(name = "owner_email")
    private String ownerEmail;

    /** Effective sensitivity, copied from the {@link FileProfile} whenever the profile changes. */
    @Enumerated(EnumType.STRING)
    private Sensitivity sensitivity;

    @Enumerated(EnumType.STRING)
    @Column(name = "drive_type", nullable = false, length = 20)
    private DriveType driveType = DriveType.SHARED;

    /** Folder path inside the drive, e.g. {@code Student Records/Grade 5}. Used by the location check. */
    @Column(name = "folder_path", length = 1000)
    private String folderPath;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    /** Content checksum from the workspace; identical checksums are duplicate copies (rule R7). */
    @Column(length = 128)
    private String checksum;

    /** The subject this file belongs to; decides which coordinator sees it. */
    @Column(name = "subject_id")
    private UUID subjectId;

    /** For exams: the exam date used by rule R6 (sharing before the exam). */
    @Column(name = "exam_date")
    private LocalDate examDate;

    @Column(name = "modified_at")
    private Instant modifiedAt;

    /** Set when the file is deleted or trashed in the workspace (rule R10, bulk-deletion alerts). */
    @Column(name = "trashed_at")
    private Instant trashedAt;

    /** When the rules were last evaluated for this file. */
    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    public boolean isTrashed() {
        return trashedAt != null;
    }

    /** What the access policy needs to know about this file: school, subject and owner. */
    public AccessScope scope() {
        return new AccessScope(getSiteId(), subjectId, null, ownerEmail);
    }
}
