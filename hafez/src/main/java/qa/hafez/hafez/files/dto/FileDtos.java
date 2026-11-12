package qa.hafez.hafez.files.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import qa.hafez.hafez.files.model.FilePermission;
import qa.hafez.hafez.files.model.FileProfile;
import qa.hafez.hafez.files.model.FileSnapshot;
import qa.hafez.hafez.files.model.SchoolFile;
import qa.hafez.hafez.files.service.ClassificationService;
import qa.hafez.hafez.files.service.FileService;
import qa.hafez.hafez.issues.dto.IssueDtos;
import qa.hafez.hafez.shared.model.Sensitivity;

/** Request and response shapes of the files API. */
public final class FileDtos {

    private FileDtos() {}

    public record FileView(UUID id, String name, String driveType, String folderPath, String sensitivity,
                           UUID subjectId, Instant lastCheckedAt, boolean trashed) {
        public static FileView from(SchoolFile f) {
            return new FileView(f.getId(), f.getName(), f.getDriveType().name(), f.getFolderPath(),
                    f.getSensitivity() == null ? null : f.getSensitivity().name(), f.getSubjectId(),
                    f.getLastCheckedAt(), f.isTrashed());
        }
    }

    /** What Hafez understood about a file: masked evidence only, never names or IDs. */
    public record ProfileView(String docType, UUID subjectId, boolean hasStudentData, boolean hasHealthData,
                              String sensitivity, double confidence, boolean needsReview, List<String> evidence) {
        public static ProfileView from(FileProfile p) {
            return p == null ? null : new ProfileView(p.getDocType(), p.getSubjectId(), p.isHasStudentData(),
                    p.isHasHealthData(), p.getSensitivity().name(), p.getConfidence(), p.isNeedsReview(),
                    List.copyOf(p.getEvidence()));
        }
    }

    public record PermissionView(String grantee, String targetType, String accessLevel, boolean external) {
        public static PermissionView from(FilePermission p) {
            return new PermissionView(p.getGrantee(), p.getTargetType().name(), p.getAccessLevel().name(), p.isExternal());
        }
    }

    public record SnapshotView(Instant at, String driveType, String folderPath, int permissionCount) {
        public static SnapshotView from(FileSnapshot s) {
            return new SnapshotView(s.getCreatedAt(), s.getDriveType().name(), s.getFolderPath(), s.getPermissionCount());
        }
    }

    /** The file-details screen (FR-11.3). {@code issues} come from the issues module, filtered by what the user may see. */
    public record FileDetailsView(FileView file, ProfileView profile, List<PermissionView> permissions,
                                  List<SnapshotView> history, List<IssueDtos.IssueView> issues) {
        public static FileDetailsView from(FileService.FileDetails d, List<IssueDtos.IssueView> issues) {
            return new FileDetailsView(FileView.from(d.file()), ProfileView.from(d.profile()),
                    d.permissions().stream().map(PermissionView::from).toList(),
                    d.history().stream().map(SnapshotView::from).toList(), issues);
        }
    }

    /** A file waiting for the coordinator to classify (FR-2.3). */
    public record ReviewItemView(UUID fileId, String docType, String sensitivity, double confidence, List<String> evidence) {
        public static ReviewItemView from(FileProfile p) {
            return new ReviewItemView(p.getFileId(), p.getDocType(), p.getSensitivity().name(), p.getConfidence(),
                    List.copyOf(p.getEvidence()));
        }
    }

    /** The coordinator's answer for an unclear file. */
    public record ClassifyRequest(@NotBlank String docType, UUID subjectId, @NotNull Sensitivity sensitivity,
                                  boolean hasStudentData, boolean hasHealthData, java.time.LocalDate examDate) {
        public ClassificationService.ClassifyCommand toCommand() {
            return new ClassificationService.ClassifyCommand(docType, subjectId, sensitivity, hasStudentData, hasHealthData, examDate);
        }
    }
}
