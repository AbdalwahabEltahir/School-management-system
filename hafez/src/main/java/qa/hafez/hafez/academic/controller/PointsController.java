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
import qa.hafez.hafez.academic.service.PointsService;
import qa.hafez.hafez.shared.security.CurrentUser;

/** Recognition points: a student's own, a child's, a class's, and the school-wide picture. */
@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService points;

    @GetMapping("/mine")
    public AcademicDtos.PointsView mine(CurrentUser user) {
        return points.mine(user);
    }

    @GetMapping("/child/{studentId}")
    public AcademicDtos.PointsView child(CurrentUser user, @PathVariable UUID studentId) {
        return points.ofChild(user, studentId);
    }

    @GetMapping("/sections/{sectionId}")
    public List<AcademicDtos.ClassPoints> section(CurrentUser user, @PathVariable UUID sectionId) {
        return points.classTotals(user, sectionId);
    }

    @GetMapping("/recognition")
    public List<AcademicDtos.SchoolPoints> recognition(CurrentUser user) {
        return points.recognition(user);
    }

    @PostMapping("/award")
    public AcademicDtos.PointRow award(CurrentUser user, @RequestBody AcademicDtos.AwardRequest request) {
        return points.award(user, request.studentId(), request.amount(), request.reason());
    }
}
