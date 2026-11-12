package qa.sijill.sijill.files.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.BaseEntity;
import qa.sijill.sijill.workspace.DriveType;

/**
 * A point-in-time picture of a file's location and sharing (FR-5.1 "changes between checks").
 * A snapshot is only written when something changed since the previous one, so the history stays small.
 * The snapshot time is {@link #getCreatedAt()}.
 */
@Getter @Setter
@Entity
@Table(name = "file_snapshots")
public class FileSnapshot extends BaseEntity {

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "drive_type", nullable = false, length = 20)
    private DriveType driveType;

    @Column(name = "folder_path", length = 1000)
    private String folderPath;

    /** Canonical one-line summary of the grants, e.g. {@code ANYONE_WITH_LINK:VIEWER;INTERNAL_USER:a@x:EDITOR}. */
    @Column(name = "sharing_summary", length = 2000)
    private String sharingSummary;

    @Column(name = "permission_count", nullable = false)
    private int permissionCount;
}
