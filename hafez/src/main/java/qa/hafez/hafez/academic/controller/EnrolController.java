package qa.hafez.hafez.academic.controller;

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
import qa.hafez.hafez.academic.service.EnrolmentRequestService;
import qa.hafez.hafez.shared.security.CurrentUser;

/** Getting a place in a class: students and parents ask, the coordinator confirms. */
@RestController
@RequestMapping("/api/enrol")
@RequiredArgsConstructor
public class EnrolController {

    private final EnrolmentRequestService enrol;

    @GetMapping("/classes")
    public List<AcademicDtos.ClassOption> classes(CurrentUser user) {
        return enrol.classes(user);
    }

    @GetMapping("/status")
    public AcademicDtos.StudentStatus status(CurrentUser user) {
        return enrol.status(user);
    }

    @PostMapping("/requests")
    public void request(CurrentUser user, @RequestBody AcademicDtos.ClassRequest request) {
        enrol.request(user, request.studentId(), request.sectionId());
    }

    @GetMapping("/requests")
    public List<AcademicDtos.PendingRequest> pending(CurrentUser user) {
        return enrol.pending(user);
    }

    @PostMapping("/requests/{id}/approve")
    public void approve(CurrentUser user, @PathVariable UUID id) {
        enrol.decide(user, id, true);
    }

    @PostMapping("/requests/{id}/decline")
    public void decline(CurrentUser user, @PathVariable UUID id) {
        enrol.decide(user, id, false);
    }

    @GetMapping("/unenrolled")
    public List<AcademicDtos.UnenrolledStudent> unenrolled(CurrentUser user) {
        return enrol.unenrolled(user);
    }
}
