package qa.hafez.hafez.issues.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import qa.hafez.hafez.issues.dto.IssueDtos;
import qa.hafez.hafez.issues.service.IssueService;
import qa.hafez.hafez.issues.service.IssueWorkflowService;
import qa.hafez.hafez.shared.security.CurrentUser;

/**
 * <b>The coordinator's follow-up screen</b> (FR-11.4): the list "Follow-up" with its filters and the
 * coordinator's answers: keep the rule, help and mark fixed, remind, record a follow-up. Every action is
 * checked against the issue's subject in {@code IssueWorkflowService}, not here.
 */
@RestController
@RequestMapping("/api/follow-up")
@RequiredArgsConstructor
public class FollowUpController {

    private final IssueService issues;
    private final IssueWorkflowService workflow;

    /** Issues for the follow-up list. {@code filter}: NEEDS_ME (default), ALL_OPEN, URGENT, CLOSED. */
    @GetMapping
    public List<IssueDtos.IssueView> list(CurrentUser user,
                                          @RequestParam(defaultValue = "NEEDS_ME") IssueService.Filter filter) {
        return issues.listFor(user, filter).stream().map(IssueDtos.IssueView::from).toList();
    }

    /** The principal's "My school" counts (open, urgent, need follow-up, fixed). */
    @GetMapping("/counts")
    public IssueDtos.CountsView counts(CurrentUser user) {
        return IssueDtos.CountsView.from(issues.counts(user.siteId()));
    }

    /** The owner disagreed and the coordinator keeps the rule. */
    @PostMapping("/{issueId}/keep-rule")
    public IssueDtos.IssueView keepRule(CurrentUser user, @PathVariable UUID issueId) {
        return IssueDtos.IssueView.from(workflow.keepRule(user, issueId));
    }

    /** The coordinator helped and marks it fixed; Hafez re-checks before closing. */
    @PostMapping("/{issueId}/mark-fixed")
    public IssueDtos.FixView markFixed(CurrentUser user, @PathVariable UUID issueId) {
        return IssueDtos.FixView.from(workflow.coordinatorMarksFixed(user, issueId));
    }

    @PostMapping("/{issueId}/remind")
    public IssueDtos.IssueView remind(CurrentUser user, @PathVariable UUID issueId) {
        return IssueDtos.IssueView.from(workflow.remind(user, issueId));
    }

    /** Records that the coordinator followed up. The next check decides. */
    @PostMapping("/{issueId}/follow-up")
    public IssueDtos.IssueView followUp(CurrentUser user, @PathVariable UUID issueId,
                                        @RequestBody(required = false) IssueDtos.FollowUpRequest request) {
        return IssueDtos.IssueView.from(workflow.recordFollowUp(user, issueId, request == null ? null : request.note()));
    }

    /** "Mark all N follow-ups as done". */
    @PostMapping("/mark-all-done")
    public Map<String, Integer> markAllDone(CurrentUser user) {
        return Map.of("marked", workflow.markAllFollowUpsDone(user));
    }
}
