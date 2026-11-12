package qa.hafez.hafez.academic.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.hafez.hafez.academic.dto.AcademicDtos;
import qa.hafez.hafez.academic.service.PortalService;
import qa.hafez.hafez.shared.security.CurrentUser;

/** The home screens of the teacher portal and the student portal. */
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalController {

    private final PortalService portal;

    @GetMapping("/teacher")
    public AcademicDtos.TeacherToday teacher(CurrentUser user) {
        return portal.teacherToday(user);
    }

    @GetMapping("/student")
    public AcademicDtos.StudentDashboard student(CurrentUser user) {
        return portal.studentDashboard(user);
    }
}
