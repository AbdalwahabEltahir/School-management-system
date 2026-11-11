package qa.sijill.sijill.academic.controller;

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

import qa.sijill.sijill.academic.dto.AcademicDtos;
import qa.sijill.sijill.academic.service.ParentPortalService;
import qa.sijill.sijill.shared.security.CurrentUser;

/** The parent portal: the children linked to the signed-in parent, and each child's home screen. */
@RestController
@RequestMapping("/api/parent")
@RequiredArgsConstructor
public class ParentController {

    private final ParentPortalService parents;
    private final qa.sijill.sijill.people.service.AccountService accounts;

    @GetMapping("/children")
    public List<AcademicDtos.ChildView> children(CurrentUser user) {
        return parents.children(user);
    }

    /** A parent follows one more child with that child's student ID. */
    @PostMapping("/children")
    public List<AcademicDtos.ChildView> addChild(CurrentUser user, @Valid @RequestBody qa.sijill.sijill.people.dto.AuthDtos.AddChildRequest request,
                                                 HttpServletRequest http) {
        accounts.addChild(user, request.studentCode(), http.getRemoteAddr());
        return parents.children(user);
    }

    @GetMapping("/children/{studentId}")
    public AcademicDtos.StudentDashboard child(CurrentUser user, @PathVariable UUID studentId) {
        return parents.child(user, studentId);
    }
}
