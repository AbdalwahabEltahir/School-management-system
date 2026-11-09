package qa.sijill.sijill.issues.controller;

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

import qa.sijill.sijill.issues.dto.IssueDtos;
import qa.sijill.sijill.issues.service.IssueService;
import qa.sijill.sijill.issues.service.IssueWorkflowService;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * <b>The owner's side of an issue</b> (the teacher's "My files" cards, FR-11.4): read, and the three
 * buttons <i>I've fixed it</i>, <i>This looks wrong</i> and <i>I need help</i>. The coordinator's actions
 * are in {@link FollowUpController}; exceptions in {@link ExceptionController}. Separate controllers keep
 * each role's permission checks easy to see.
 */
@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issues;
    private final IssueWorkflowService workflow;

    /** The issues the user may see. {@code filter}: ALL, ALL_OPEN (default), NEEDS_ME, URGENT, CLOSED. */
    @GetMapping
    public List<IssueDtos.IssueView> list(CurrentUser user,
                                          @RequestParam(defaultValue = "ALL_OPEN") IssueService.Filter filter) {
        return issues.listFor(user, filter).stream().map(IssueDtos.IssueView::from).toList();
    }

    @GetMapping("/{issueId}")
    public IssueDtos.IssueView get(CurrentUser user, @PathVariable UUID issueId) {
        return IssueDtos.IssueView.from(issues.getVisible(user, issueId));
    }

    /** The issue's history, oldest first. */
    @GetMapping("/{issueId}/history")
    public List<IssueDtos.EventView> history(CurrentUser user, @PathVariable UUID issueId) {
        return issues.history(user, issueId).stream().map(IssueDtos.EventView::from).toList();
    }

    /** "I've fixed it": triggers a real re-check; the issue closes only if the check passes. */
    @PostMapping("/{issueId}/fixed")
    public IssueDtos.FixView fixed(CurrentUser user, @PathVariable UUID issueId) {
        return IssueDtos.FixView.from(workflow.ownerMarksFixed(user, issueId));
    }

    /** "This looks wrong": a note is required. */
    @PostMapping("/{issueId}/disagree")
    public IssueDtos.IssueView disagree(CurrentUser user, @PathVariable UUID issueId,
                                        @Valid @RequestBody IssueDtos.DisagreeRequest request) {
        return IssueDtos.IssueView.from(workflow.ownerDisagrees(user, issueId, request.note()));
    }

    /** "I need help". */
    @PostMapping("/{issueId}/help")
    public IssueDtos.IssueView help(CurrentUser user, @PathVariable UUID issueId) {
        return IssueDtos.IssueView.from(workflow.ownerAsksForHelp(user, issueId));
    }
}
