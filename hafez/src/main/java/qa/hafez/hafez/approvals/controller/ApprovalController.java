package qa.hafez.hafez.approvals.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.hafez.hafez.approvals.model.ApprovalRequest;
import qa.hafez.hafez.approvals.service.ApprovalService;
import qa.hafez.hafez.shared.security.CurrentUser;

/** The approvals inbox: what is waiting for a second person, and what was decided. */
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvals;

    public record ApprovalView(UUID id, String action, String summary, String status, String requestedBy, Instant requestedAt,
                               String decidedBy, Instant decidedAt, String note, boolean canDecide, boolean mine) {}

    @GetMapping
    public List<ApprovalView> list(CurrentUser user) {
        return approvals.list(user).stream().map(r -> view(user, r)).toList();
    }

    @PostMapping("/{id}/approve")
    public ApprovalView approve(CurrentUser user, @PathVariable UUID id) {
        return view(user, approvals.approve(user, id));
    }

    @PostMapping("/{id}/reject")
    public ApprovalView reject(CurrentUser user, @PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        return view(user, approvals.reject(user, id, body == null ? null : body.get("note")));
    }

    @PostMapping("/{id}/cancel")
    public ApprovalView cancel(CurrentUser user, @PathVariable UUID id) {
        return view(user, approvals.cancel(user, id));
    }

    private ApprovalView view(CurrentUser user, ApprovalRequest r) {
        return new ApprovalView(r.getId(), r.getAction(), r.getSummary(), r.getStatus().name(), r.getRequestedByName(),
                r.getCreatedAt(), r.getDecidedByName(), r.getDecidedAt(), r.getDecisionNote(), approvals.canDecide(user, r),
                r.getRequestedBy().equals(user.personId()));
    }
}
