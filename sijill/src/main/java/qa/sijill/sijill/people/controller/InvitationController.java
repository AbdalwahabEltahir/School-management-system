package qa.sijill.sijill.people.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.people.service.InvitationService;
import qa.sijill.sijill.shared.security.CurrentUser;

/** The school issues and tracks one-time invitation codes. */
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {

    public record IssueRequest(@NotBlank String kind, @NotNull List<UUID> studentIds, String email) {}

    private final InvitationService invitations;

    @GetMapping
    public List<InvitationService.InvitationView> list(CurrentUser user) {
        return invitations.list(user);
    }

    /** The code is returned here once and never again. */
    @PostMapping
    public InvitationService.Issued issue(CurrentUser user, @Valid @RequestBody IssueRequest request) {
        return invitations.issue(user, request.kind(), request.studentIds(), request.email());
    }

    @PostMapping("/{id}/revoke")
    public void revoke(CurrentUser user, @PathVariable UUID id) {
        invitations.revoke(user, id);
    }
}
