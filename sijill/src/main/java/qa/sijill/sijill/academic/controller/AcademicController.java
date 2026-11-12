package qa.sijill.sijill.academic.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.academic.dto.AcademicDtos;
import qa.sijill.sijill.academic.service.AcademicSetupService;
import qa.sijill.sijill.shared.security.CurrentUser;

/** Sections, enrolment, teaching assignments and the timetable. The coordinator sets them up; teachers and students read theirs. */
@RestController
@RequestMapping("/api/academic")
@RequiredArgsConstructor
public class AcademicController {

    private final AcademicSetupService setup;

    @GetMapping("/sections")
    public List<AcademicDtos.SectionView> sections(CurrentUser user) {
        return setup.sections(user);
    }

    @GetMapping("/terms")
    public List<AcademicDtos.TermView> terms(CurrentUser user) {
        return setup.terms(user);
    }

    @PutMapping("/terms")
    public AcademicDtos.TermView saveTerm(CurrentUser user, @Valid @RequestBody AcademicDtos.TermRequest request) {
        return setup.saveTerm(user, request.number(), request.name(), request.startsOn(), request.endsOn());
    }

    @PostMapping("/sections")
    public AcademicDtos.SectionView createSection(CurrentUser user, @Valid @RequestBody AcademicDtos.SectionRequest request) {
        return setup.createSection(user, request.grade(), request.name());
    }

    @GetMapping("/sections/{sectionId}/students")
    public List<AcademicDtos.StudentView> students(CurrentUser user, @PathVariable UUID sectionId) {
        return setup.roster(user, sectionId);
    }

    @PostMapping("/sections/{sectionId}/students")
    public AcademicDtos.StudentView enrol(CurrentUser user, @PathVariable UUID sectionId,
                                          @Valid @RequestBody AcademicDtos.EnrolRequest request) {
        return setup.enrol(user, sectionId, request.studentEmail());
    }

    @GetMapping("/sections/{sectionId}/assignments")
    public List<AcademicDtos.AssignmentView> assignments(CurrentUser user, @PathVariable UUID sectionId) {
        return setup.assignmentsOf(user, sectionId);
    }

    @PutMapping("/sections/{sectionId}/assignments")
    public AcademicDtos.AssignmentView assign(CurrentUser user, @PathVariable UUID sectionId,
                                              @Valid @RequestBody AcademicDtos.AssignRequest request) {
        return setup.assign(user, sectionId, request.subjectId(), request.teacherId());
    }

    @GetMapping("/timetable")
    public List<AcademicDtos.LessonView> timetable(CurrentUser user, @RequestParam(required = false) UUID sectionId) {
        return setup.timetable(user, sectionId);
    }

    @PostMapping("/timetable")
    public AcademicDtos.LessonView addLesson(CurrentUser user, @Valid @RequestBody AcademicDtos.LessonRequest request) {
        return setup.addLesson(user, request.sectionId(), request.subjectId(), request.day(), request.period(), request.room());
    }

    @DeleteMapping("/timetable/{lessonId}")
    public void removeLesson(CurrentUser user, @PathVariable UUID lessonId) {
        setup.removeLesson(user, lessonId);
    }
}
