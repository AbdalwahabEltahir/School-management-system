package qa.sijill.sijill.workspace;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stage 1 of the pipeline: the <b>read-only</b> connection to a school's workspace (proposal 6).
 *
 * <p>There is one connector per school domain, each with its own read-only service account, behind this
 * one interface so another workspace platform can be added later. The interface deliberately has
 * <b>no write methods</b>: Sijill cannot move, edit, share or delete a file, and the "Test connection"
 * button proves a write is refused (FR-8.2).
 *
 * <p>No implementation is bundled yet. When none is registered the schedulers log a warning and skip
 * their run, so the rest of the application keeps working.
 */
public interface WorkspaceConnector {

    /** Every file of the school domain (the nightly full check, FR-1.1). */
    List<WorkspaceFile> listAllFiles(String domain);

    /** Files changed since a point in time (the change feed, "within minutes", FR-1.1). */
    List<WorkspaceFile> listChanges(String domain, Instant since);

    Optional<WorkspaceFile> getFile(String domain, String workspaceFileId);

    List<WorkspacePermission> getPermissions(String domain, String workspaceFileId);

    /** The directory: people, their groups and whether their account is suspended. */
    List<WorkspacePerson> getPeople(String domain);
}
