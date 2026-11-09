package qa.sijill.sijill.planning.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.planning.dto.PlanningDtos;
import qa.sijill.sijill.planning.service.PlanningService;
import qa.sijill.sijill.shared.security.CurrentUser;

/** Planning-file review (proposal 5.9): "My planning" for teachers, "Planning review" for coordinators. */
@RestController
@RequestMapping("/api/planning")
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningService planning;

    /** Submissions the user may see: own, her subject's, or the school's (principal, read-only). */
    @GetMapping
    public List<PlanningDtos.SubmissionView> list(CurrentUser user) {
        return planning.list(user).stream().map(PlanningDtos.SubmissionView::from).toList();
    }

    /** A teacher submits a plan (or resubmits a returned one). */
    @PostMapping
    public PlanningDtos.SubmissionView submit(CurrentUser user, @Valid @RequestBody PlanningDtos.SubmitRequest request) {
        return PlanningDtos.SubmissionView.from(planning.submit(user, request.toCommand()));
    }

    /** The coordinator approves, or returns with a reason. */
    @PostMapping("/{submissionId}/review")
    public PlanningDtos.SubmissionView review(CurrentUser user, @PathVariable UUID submissionId,
                                              @Valid @RequestBody PlanningDtos.ReviewRequest request) {
        return PlanningDtos.SubmissionView.from(planning.review(user, submissionId, request.decision(), request.reason()));
    }

    @GetMapping("/{submissionId}/history")
    public List<PlanningDtos.ReviewView> history(CurrentUser user, @PathVariable UUID submissionId) {
        return planning.history(user, submissionId).stream().map(PlanningDtos.ReviewView::from).toList();
    }

    /** Totals for a week (principal's view). */
    @GetMapping("/totals")
    public PlanningDtos.TotalsView totals(CurrentUser user,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return PlanningDtos.TotalsView.from(planning.totals(user, week));
    }
}
