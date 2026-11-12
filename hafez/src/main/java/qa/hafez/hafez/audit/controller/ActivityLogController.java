package qa.hafez.hafez.audit.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import qa.hafez.hafez.audit.dto.AuditDtos;
import qa.hafez.hafez.audit.service.ActivityLogService;
import qa.hafez.hafez.audit.service.ExportService;
import qa.hafez.hafez.shared.security.CurrentUser;

/**
 * The activity log and name-free exports (FR-5.2, 5.10). What each role sees is decided in
 * {@code ActivityLogService}; every download is masked, and recorded in the log.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ActivityLogController {

    private final ActivityLogService activityLog;
    private final ExportService exports;

    /** The log as the role may see it. {@code action} filters by an action code such as RULE_UPDATED. */
    @GetMapping("/activity-log")
    public List<AuditDtos.LogEntryView> list(CurrentUser user, @RequestParam(required = false) String action,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return activityLog.list(user, action, page, size).stream().map(AuditDtos.LogEntryView::from).toList();
    }

    /** Proves the log has not been tampered with: re-computes the whole hash chain (IT and Leadership). */
    @GetMapping("/activity-log/verify")
    public AuditDtos.VerificationView verify(CurrentUser user) {
        return AuditDtos.VerificationView.from(activityLog.verifyChain(user));
    }

    /** CSV download of the log. Names are initials; School IT may unmask one export with a reason. */
    @GetMapping("/activity-log/download")
    public ResponseEntity<String> download(CurrentUser user, @RequestParam(defaultValue = "false") boolean unmask,
                                           @RequestParam(required = false) String reason) {
        return csv("activity-log.csv", exports.activityLogCsv(user, unmask, reason));
    }

    /** CSV download of the user's issues: always name-free. */
    @GetMapping("/exports/issues.csv")
    public ResponseEntity<String> issues(CurrentUser user) {
        return csv("issues.csv", exports.issuesCsv(user));
    }

    private ResponseEntity<String> csv(String filename, String content) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body("﻿" + content); // BOM so spreadsheet apps open Arabic text correctly
    }
}
