package qa.hafez.hafez.backup.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import qa.hafez.hafez.backup.dto.BackupDtos;
import qa.hafez.hafez.backup.service.BackupService;
import qa.hafez.hafez.backup.service.RestoreService;
import qa.hafez.hafez.shared.dto.JobRunView;
import qa.hafez.hafez.shared.security.CurrentUser;

/**
 * "Settings › Backup" (section 9): backup status, and restore requests. Restores are only <i>tracked</i>
 * here; School IT performs them with the workspace's own tools, so Hafez stays read-only.
 */
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backups;
    private final RestoreService restores;

    /** Recent backup runs of a school: status and last copy. */
    @GetMapping("/sites/{siteId}/runs")
    public List<JobRunView> runs(CurrentUser user, @PathVariable UUID siteId) {
        return backups.recent(user, siteId).stream().map(JobRunView::from).toList();
    }

    @GetMapping("/restores")
    public List<BackupDtos.RestoreView> listRestores(CurrentUser user, @RequestParam(required = false) UUID siteId) {
        return restores.list(user, siteId).stream().map(BackupDtos.RestoreView::from).toList();
    }

    @PostMapping("/restores")
    public BackupDtos.RestoreView request(CurrentUser user, @Valid @RequestBody BackupDtos.RestoreRequestBody body) {
        return BackupDtos.RestoreView.from(restores.request(user, body.toCommand()));
    }

    @PostMapping("/restores/{requestId}/approve")
    public BackupDtos.RestoreView approve(CurrentUser user, @PathVariable UUID requestId) {
        return BackupDtos.RestoreView.from(restores.approve(user, requestId));
    }

    @PostMapping("/restores/{requestId}/reject")
    public BackupDtos.RestoreView reject(CurrentUser user, @PathVariable UUID requestId) {
        return BackupDtos.RestoreView.from(restores.reject(user, requestId));
    }

    /** School IT records that the restore was done. */
    @PostMapping("/restores/{requestId}/complete")
    public BackupDtos.RestoreView complete(CurrentUser user, @PathVariable UUID requestId) {
        return BackupDtos.RestoreView.from(restores.complete(user, requestId));
    }
}
