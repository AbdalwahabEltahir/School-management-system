package qa.sijill.sijill.rules.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.rules.dto.RuleDtos;
import qa.sijill.sijill.rules.service.RuleQueryService;
import qa.sijill.sijill.rules.service.RuleService;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Rules (FR-8.1): everyone can read the active rules ("The rules" page); School IT and Leadership manage
 * them. Network-wide changes need two-person approval, enforced in {@code RuleService}.
 */
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService rules;
    private final RuleQueryService queries;

    @GetMapping
    public List<RuleDtos.RuleView> list(CurrentUser user) {
        return queries.list(user).stream().map(RuleDtos.RuleView::from).toList();
    }

    @GetMapping("/{ruleId}/versions")
    public List<RuleDtos.VersionView> versions(CurrentUser user, @PathVariable UUID ruleId) {
        return queries.versions(user, ruleId).stream().map(RuleDtos.VersionView::from).toList();
    }

    /** How many open issues deleting the rule would close (for the "use Pause instead" warning). */
    @GetMapping("/{ruleId}/open-issues")
    public Map<String, Long> openIssues(CurrentUser user, @PathVariable UUID ruleId) {
        return Map.of("openIssues", queries.openIssueCount(user, ruleId));
    }

    @PostMapping
    public RuleDtos.RuleView create(CurrentUser user, @Valid @RequestBody RuleDtos.CreateRuleRequest request) {
        return RuleDtos.RuleView.from(rules.create(user, request.draft(), request.networkWide(), request.turnOn()));
    }

    /** Edit = add a new version. A network-wide edit waits for approval. */
    @PutMapping("/{ruleId}")
    public RuleDtos.RuleView update(CurrentUser user, @PathVariable UUID ruleId, @Valid @RequestBody RuleService.Draft draft) {
        return RuleDtos.RuleView.from(rules.update(user, ruleId, draft));
    }

    /** Second person approves a proposed network-wide version. */
    @PostMapping("/{ruleId}/versions/{versionId}/approve")
    public RuleDtos.RuleView approve(CurrentUser user, @PathVariable UUID ruleId, @PathVariable UUID versionId) {
        return RuleDtos.RuleView.from(rules.approveVersion(user, ruleId, versionId));
    }

    @PostMapping("/{ruleId}/pause")
    public RuleDtos.RuleView pause(CurrentUser user, @PathVariable UUID ruleId) {
        return RuleDtos.RuleView.from(rules.pause(user, ruleId));
    }

    @PostMapping("/{ruleId}/resume")
    public RuleDtos.RuleView resume(CurrentUser user, @PathVariable UUID ruleId) {
        return RuleDtos.RuleView.from(rules.resume(user, ruleId));
    }

    @DeleteMapping("/{ruleId}")
    public void delete(CurrentUser user, @PathVariable UUID ruleId) {
        rules.delete(user, ruleId);
    }

    /** Live preview while editing: files that would break the rule, and the message the owner would read. */
    @PostMapping("/preview")
    public RuleDtos.PreviewView preview(CurrentUser user, @Valid @RequestBody RuleService.Draft draft) {
        return RuleDtos.PreviewView.from(queries.preview(user, draft));
    }
}
