package qa.sijill.sijill.workspace;

import java.time.Instant;
import java.util.List;

/**
 * A file as reported by the workspace: metadata and sharing only, never content.
 *
 * @param id          the workspace's own file id
 * @param ownerEmail  the owner's school account
 * @param driveType   shared or personal drive
 * @param folderPath  folder path inside the drive
 * @param checksum    content checksum, used to find duplicates
 * @param trashedAt   null unless deleted/trashed
 * @param permissions the file's sharing grants
 */
public record WorkspaceFile(String id, String name, String ownerEmail, String mimeType, DriveType driveType,
                            String folderPath, String checksum, Instant modifiedAt, Instant trashedAt,
                            List<WorkspacePermission> permissions) {
}
