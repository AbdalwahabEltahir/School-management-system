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
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.issues.dto.IssueDtos;
import qa.sijill.sijill.issues.service.ExceptionService;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Allowed exceptions (FR-2.5): the Exceptions table, allowing an exception on an issue, and ending one.
 * Only the subject's coordinator may allow; the coordinator or School IT may end (checked in the service).
 */
@RestController
@RequestMapping("/api/exceptions")
@RequiredArgsConstructor
public class ExceptionController {

    private final ExceptionService exceptions;

    /** Active exceptions the user's role may see. */
    @GetMapping
    public List<IssueDtos.ExceptionView> list(CurrentUser user) {
        return exceptions.listActive(user).stream().map(IssueDtos.ExceptionView::from).toList();
    }

    /** Allow an exception on an issue: a reason and an end date are mandatory. */
    @PostMapping("/issues/{issueId}")
    public IssueDtos.ExceptionView allow(CurrentUser user, @PathVariable UUID issueId,
                                         @Valid @RequestBody IssueDtos.ExceptionRequest request) {
        return IssueDtos.ExceptionView.from(exceptions.grant(user, issueId, request.reason(), request.endsOn()));
    }

    /** End an exception now; the rule applies again and the file is re-checked. */
    @PostMapping("/{exceptionId}/end")
    public void end(CurrentUser user, @PathVariable UUID exceptionId) {
        exceptions.end(user, exceptionId);
    }
}
