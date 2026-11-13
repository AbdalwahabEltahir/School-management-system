package qa.sijill.sijill.files.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.files.dto.FileDtos;
import qa.sijill.sijill.files.service.ClassificationService;
import qa.sijill.sijill.files.service.FileService;
import qa.sijill.sijill.issues.dto.IssueDtos;
import qa.sijill.sijill.issues.service.IssueService;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Files as the screens show them: "My files", the files a role may browse, file details and the
 * coordinator's review queue. Hafez never changes a file; the only write here is the coordinator's
 * classification of an unclear file (stored in Hafez's own database).
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService files;
    private final ClassificationService classification;
    private final IssueService issues;

    /** The files the user's role may browse (coordinator: her subject's; principal: the school's). */
    @GetMapping
    public List<FileDtos.FileView> browse(CurrentUser user) {
        return files.visibleFiles(user).stream().map(FileDtos.FileView::from).toList();
    }

    /** "My files": the person's own files, for every role. */
    @GetMapping("/mine")
    public List<FileDtos.FileView> mine(CurrentUser user) {
        return files.ownFiles(user).stream().map(FileDtos.FileView::from).toList();
    }

    /** File details: what Hafez understood, sharing, history and the issues the user may see. */
    @GetMapping("/{fileId}")
    public FileDtos.FileDetailsView details(CurrentUser user, @PathVariable UUID fileId) {
        FileService.FileDetails details = files.details(user, fileId);
        List<IssueDtos.IssueView> fileIssues = issues.forFile(user, fileId).stream().map(IssueDtos.IssueView::from).toList();
        return FileDtos.FileDetailsView.from(details, fileIssues);
    }

    /** Files waiting for the coordinator to classify (FR-2.3). */
    @GetMapping("/review-queue")
    public List<FileDtos.ReviewItemView> reviewQueue(CurrentUser user) {
        return classification.reviewQueue(user).stream().map(FileDtos.ReviewItemView::from).toList();
    }

    /** The coordinator answers an unclear file; it is re-checked straight away. */
    /** Asks to delete a file from the register. Waits for a second person's approval. */
    @org.springframework.web.bind.annotation.PostMapping("/{fileId}/delete-request")
    public void requestDelete(CurrentUser user, @PathVariable UUID fileId) {
        files.requestDelete(user, fileId);
    }

    @PutMapping("/{fileId}/classification")
    public FileDtos.ProfileView classify(CurrentUser user, @PathVariable UUID fileId,
                                         @Valid @RequestBody FileDtos.ClassifyRequest request) {
        return FileDtos.ProfileView.from(classification.classifyManually(user, fileId, request.toCommand()));
    }
}
