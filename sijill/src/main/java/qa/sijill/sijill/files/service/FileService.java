package qa.sijill.sijill.files.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.files.model.FilePermission;
import qa.sijill.sijill.files.model.FileProfile;
import qa.sijill.sijill.files.model.FileSnapshot;
import qa.sijill.sijill.files.model.SchoolFile;
import qa.sijill.sijill.files.repository.FilePermissionRepository;
import qa.sijill.sijill.files.repository.FileProfileRepository;
import qa.sijill.sijill.files.repository.FileSnapshotRepository;
import qa.sijill.sijill.files.repository.SchoolFileRepository;
import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.security.AccessPolicy;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.workspace.DriveType;
import qa.sijill.sijill.workspace.ShareTargetType;
import qa.sijill.sijill.workspace.WorkspaceConnector;
import qa.sijill.sijill.workspace.WorkspaceFile;
import qa.sijill.sijill.workspace.WorkspacePermission;

/**
 * Files as Sijill knows them: ingesting what the read-only connector reports, and showing each role the
 * files it may see. (Understanding a file — classification — is in {@link ClassificationService}.)
 *
 * <p><b>Never stores content.</b> Only metadata, sharing grants, masked evidence and profiles are kept
 * (section 7). Sijill never writes to the workspace: {@link #refreshFromWorkspace} only <i>reads</i>.
 */
@Service
@RequiredArgsConstructor
public class FileService {

    /** The file-details screen data (FR-11.3): the file, what Sijill understood, sharing and history. */
    public record FileDetails(SchoolFile file, FileProfile profile, List<FilePermission> permissions,
                              List<FileSnapshot> history) {}

    private final SchoolFileRepository files;
    private final FilePermissionRepository permissions;
    private final FileProfileRepository profiles;
    private final FileSnapshotRepository snapshots;
    private final SiteRepository sites;
    private final AccessPolicy policy;
    private final ObjectProvider<WorkspaceConnector> connector;
    private final Clock clock;

    // ================================================================== ingest (stage 1 → database)

    /**
     * Creates or updates a file from what the workspace reported: metadata, location, sharing. The set of
     * sharing grants is replaced as a whole, and a {@link FileSnapshot} is written if location or sharing
     * changed since the last one ("changes between checks", FR-5.1).
     */
    @Transactional
    public SchoolFile upsertFromWorkspace(Site site, WorkspaceFile source) {
        SchoolFile file = files.findByWorkspaceFileId(source.id()).orElseGet(() -> {
            SchoolFile f = new SchoolFile();
            f.setOrgId(site.getOrgId());
            f.setSiteId(site.getId());
            f.setWorkspaceFileId(source.id());
            return f;
        });
        file.setName(source.name());
        file.setOwnerEmail(source.ownerEmail());
        file.setMimeType(source.mimeType());
        file.setDriveType(source.driveType() == null ? DriveType.SHARED : source.driveType());
        file.setFolderPath(source.folderPath());
        file.setChecksum(source.checksum());
        file.setModifiedAt(source.modifiedAt());
        file.setTrashedAt(source.trashedAt());
        files.save(file);

        List<FilePermission> grants = replacePermissions(file, source.permissions());
        writeSnapshotIfChanged(file, grants);
        return file;
    }

    private List<FilePermission> replacePermissions(SchoolFile file, List<WorkspacePermission> source) {
        permissions.deleteByFileId(file.getId());
        List<FilePermission> grants = new ArrayList<>();
        for (WorkspacePermission p : source == null ? List.<WorkspacePermission>of() : source) {
            FilePermission fp = new FilePermission();
            fp.setOrgId(file.getOrgId());
            fp.setSiteId(file.getSiteId());
            fp.setFileId(file.getId());
            fp.setGrantee(p.grantee());
            fp.setTargetType(p.targetType());
            fp.setAccessLevel(p.accessLevel());
            fp.setExternal(p.targetType() == ShareTargetType.EXTERNAL_USER);
            grants.add(fp);
        }
        return permissions.saveAll(grants);
    }

    private void writeSnapshotIfChanged(SchoolFile file, List<FilePermission> grants) {
        String summary = grants.stream()
                .map(g -> g.getTargetType() + ":" + (g.getGrantee() == null ? "" : g.getGrantee().toLowerCase())
                        + ":" + g.getAccessLevel())
                .sorted().collect(Collectors.joining(";"));
        String finalSummary = summary.length() > 2000 ? summary.substring(0, 2000) : summary;
        boolean changed = snapshots.findFirstByFileIdOrderByCreatedAtDesc(file.getId())
                .map(last -> last.getDriveType() != file.getDriveType()
                        || !Objects.equals(last.getFolderPath(), file.getFolderPath())
                        || !Objects.equals(last.getSharingSummary(), finalSummary))
                .orElse(true);
        if (changed) {
            FileSnapshot s = new FileSnapshot();
            s.setOrgId(file.getOrgId());
            s.setSiteId(file.getSiteId());
            s.setFileId(file.getId());
            s.setDriveType(file.getDriveType());
            s.setFolderPath(file.getFolderPath());
            s.setSharingSummary(finalSummary);
            s.setPermissionCount(grants.size());
            snapshots.save(s);
        }
    }

    /**
     * Re-reads one file from the workspace so a re-check judges fresh data ("I've fixed it" triggers a
     * real re-check, FR-11.8). Read-only. Without a connector the stored data is used unchanged. A file
     * the workspace no longer has is recorded as deleted.
     */
    @Transactional
    public void refreshFromWorkspace(UUID fileId) {
        WorkspaceConnector workspace = connector.getIfAvailable();
        if (workspace == null) {
            return;
        }
        SchoolFile file = files.findById(fileId).orElseThrow(() -> new NotFoundException("The file"));
        Site site = sites.findById(file.getSiteId()).orElseThrow();
        workspace.getFile(site.getDomain(), file.getWorkspaceFileId()).ifPresentOrElse(
                source -> upsertFromWorkspace(site, source),
                () -> {
                    if (file.getTrashedAt() == null) {
                        file.setTrashedAt(Instant.now(clock));
                    }
                });
    }

    // ================================================================== reading (role-scoped)

    /** The person's own files: the "My files" screen, available to every role. */
    @Transactional(readOnly = true)
    public List<SchoolFile> ownFiles(CurrentUser user) {
        return files.findByOwnerEmailIgnoreCaseAndTrashedAtIsNull(user.email());
    }

    /**
     * Files a role may browse: a coordinator her subject's, a principal her whole school's (read-only).
     * Teachers see only their own ({@link #ownFiles}); School IT and Leadership see counts, not files.
     */
    @Transactional(readOnly = true)
    public List<SchoolFile> visibleFiles(CurrentUser user) {
        return switch (user.role()) {
            case TEACHER, STUDENT, PARENT -> ownFiles(user);
            case COORDINATOR -> {
                Map<UUID, SchoolFile> merged = new LinkedHashMap<>();
                List<SchoolFile> subjectFiles = user.subjectId() == null
                        ? files.findBySiteIdAndTrashedAtIsNull(user.siteId())
                        : files.findBySiteIdAndSubjectIdAndTrashedAtIsNull(user.siteId(), user.subjectId());
                subjectFiles.forEach(f -> merged.put(f.getId(), f));
                ownFiles(user).forEach(f -> merged.put(f.getId(), f));
                yield new ArrayList<>(merged.values());
            }
            case PRINCIPAL -> files.findBySiteIdAndTrashedAtIsNull(user.siteId());
            case SCHOOL_IT, LEADERSHIP -> throw new ForbiddenException("Your role sees counts only, not individual files.");
        };
    }

    /** One file with its profile, sharing and history. Other people's files are "not found". */
    @Transactional(readOnly = true)
    public FileDetails details(CurrentUser user, UUID fileId) {
        SchoolFile file = requireVisible(user, fileId);
        return new FileDetails(file, profiles.findByFileId(fileId).orElse(null),
                permissions.findByFileId(fileId), snapshots.findByFileIdOrderByCreatedAtDesc(fileId));
    }

    /** The file if the user may see it, otherwise "not found". */
    @Transactional(readOnly = true)
    public SchoolFile requireVisible(CurrentUser user, UUID fileId) {
        SchoolFile file = files.findById(fileId).orElseThrow(() -> new NotFoundException("The file"));
        policy.requireView(user, file.scope(), "The file");
        return file;
    }
}
