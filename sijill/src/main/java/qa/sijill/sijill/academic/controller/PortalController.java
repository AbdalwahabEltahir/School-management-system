package qa.sijill.sijill.academic.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.academic.dto.AcademicDtos;
import qa.sijill.sijill.academic.service.PortalService;
import qa.sijill.sijill.shared.security.CurrentUser;

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
