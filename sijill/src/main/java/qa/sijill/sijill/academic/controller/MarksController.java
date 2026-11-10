package qa.sijill.sijill.academic.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.academic.dto.AcademicDtos;
import qa.sijill.sijill.academic.service.MarksService;
import qa.sijill.sijill.shared.security.CurrentUser;

/** Assessments, mark entry, locking, publishing, and a student's own published results. */
@RestController
@RequestMapping("/api/marks")
@RequiredArgsConstructor
public class MarksController {

    private final MarksService marks;

    @GetMapping("/assessments")
    public List<AcademicDtos.AssessmentView> assessments(CurrentUser user, @RequestParam(required = false) UUID sectionId) {
        return marks.list(user, sectionId);
    }

    @PostMapping("/assessments")
    public AcademicDtos.AssessmentView create(CurrentUser user, @Valid @RequestBody AcademicDtos.AssessmentRequest request) {
        return marks.create(user, request.sectionId(), request.subjectId(), request.term(), request.name(), request.maxMark());
    }

    @GetMapping("/assessments/{assessmentId}/sheet")
    public List<AcademicDtos.MarkRow> sheet(CurrentUser user, @PathVariable UUID assessmentId) {
        return marks.sheet(user, assessmentId);
    }

    @PutMapping("/assessments/{assessmentId}/marks")
    public Map<String, Integer> enter(CurrentUser user, @PathVariable UUID assessmentId,
                                      @Valid @RequestBody AcademicDtos.MarksRequest request) {
        return Map.of("saved", marks.enter(user, assessmentId, request.marks()));
    }

    @PostMapping("/assessments/{assessmentId}/submit")
    public void submit(CurrentUser user, @PathVariable UUID assessmentId) {
        marks.submit(user, assessmentId);
    }

    @PostMapping("/assessments/{assessmentId}/unlock")
    public void unlock(CurrentUser user, @PathVariable UUID assessmentId, @RequestBody AcademicDtos.ReasonRequest request) {
        marks.unlock(user, assessmentId, request.reason());
    }

    @PostMapping("/sections/{sectionId}/publish")
    public void publish(CurrentUser user, @PathVariable UUID sectionId, @RequestBody AcademicDtos.PublishRequest request) {
        marks.publish(user, sectionId, request.term());
    }

    @PostMapping("/sections/{sectionId}/unpublish")
    public void unpublish(CurrentUser user, @PathVariable UUID sectionId, @RequestBody AcademicDtos.PublishRequest request) {
        marks.unpublish(user, sectionId, request.term(), request.reason());
    }

    @GetMapping("/mine")
    public List<AcademicDtos.ResultRow> mine(CurrentUser user) {
        return marks.myResults(user);
    }
}
