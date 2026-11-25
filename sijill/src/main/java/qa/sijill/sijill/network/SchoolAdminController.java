package qa.sijill.sijill.network;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.network.SchoolAdminDtos.AssignmentRow;
import qa.sijill.sijill.network.SchoolAdminDtos.AttendanceView;
import qa.sijill.sijill.network.SchoolAdminDtos.EnrolmentRow;
import qa.sijill.sijill.network.SchoolAdminDtos.MarksView;
import qa.sijill.sijill.network.SchoolAdminDtos.ResultsView;
import qa.sijill.sijill.shared.security.CurrentUser;

/** The School IT tracking screens. */
@RestController
@RequestMapping("/api/schooladmin")
@RequiredArgsConstructor
public class SchoolAdminController {

    private final SchoolAdminService service;

    @GetMapping("/enrolments")
    public List<EnrolmentRow> enrolments(CurrentUser user) {
        return service.enrolments(user);
    }

    @GetMapping("/assignments")
    public List<AssignmentRow> assignments(CurrentUser user, @RequestParam UUID section) {
        return service.assignments(user, section);
    }

    @GetMapping("/attendance")
    public AttendanceView attendance(CurrentUser user, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) Integer grade) {
        return service.attendance(user, from, to, grade);
    }

    @GetMapping("/marks")
    public MarksView marks(CurrentUser user, @RequestParam(required = false) Integer term, @RequestParam(required = false) Integer grade) {
        return service.marks(user, term, grade);
    }

    @GetMapping("/results")
    public ResultsView results(CurrentUser user, @RequestParam(required = false) Integer term, @RequestParam(required = false) Integer grade) {
        return service.results(user, term, grade);
    }
}
