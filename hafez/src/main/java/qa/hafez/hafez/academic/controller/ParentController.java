package qa.hafez.hafez.academic.controller;

import jakarta.servlet.http.HttpServletRequest;
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

import qa.hafez.hafez.academic.dto.AcademicDtos;
import qa.hafez.hafez.academic.service.ParentPortalService;
import qa.hafez.hafez.shared.security.CurrentUser;

/** The parent portal: the children linked to the signed-in parent, and each child's home screen. */
@RestController
@RequestMapping("/api/parent")
@RequiredArgsConstructor
public class ParentController {

    private final ParentPortalService parents;
    private final qa.hafez.hafez.people.service.InvitationService invitations;

    @GetMapping("/children")
    public List<AcademicDtos.ChildView> children(CurrentUser user) {
        return parents.children(user);
    }

    /** A parent follows one more child with that child's student ID. */
    @PostMapping("/children")
    public List<AcademicDtos.ChildView> addChild(CurrentUser user, @Valid @RequestBody qa.hafez.hafez.people.dto.AuthDtos.RedeemRequest request,
                                                 HttpServletRequest http) {
        invitations.redeemForParent(user, request.code(), http.getRemoteAddr());
        return parents.children(user);
    }

    @GetMapping("/children/{studentId}")
    public AcademicDtos.StudentDashboard child(CurrentUser user, @PathVariable UUID studentId) {
        return parents.child(user, studentId);
    }
}
