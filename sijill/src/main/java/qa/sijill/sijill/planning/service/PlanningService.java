package qa.sijill.sijill.planning.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.model.Subject;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.organisation.repository.SubjectRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.planning.model.PlanningReview;
import qa.sijill.sijill.planning.model.PlanningStatus;
import qa.sijill.sijill.planning.model.PlanningSubmission;
import qa.sijill.sijill.planning.model.ReviewDecision;
import qa.sijill.sijill.planning.repository.PlanningReviewRepository;
import qa.sijill.sijill.planning.repository.PlanningSubmissionRepository;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.events.PersonNotice;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.AccessPolicy;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Planning-file review (proposal 5.9, FR-9.1 – FR-9.4).
 *
 * <ol>
 *   <li>A teacher <b>submits</b> subject, grade, week, type and a drive link (the file is never copied).</li>
 *   <li>The subject's coordinator <b>approves</b> it or <b>returns</b> it with a reason.</li>
 *   <li>A returned plan is <b>resubmitted</b>; every decision stays in the history.</li>
 * </ol>
 * Teachers see their own submissions, coordinators their subject's, principals the whole school's
 * (view only). Families never see planning files.
 */
@Service
@RequiredArgsConstructor
public class PlanningService {

    /** What a teacher sends when submitting a plan. */
    public record SubmitCommand(UUID subjectId, String grade, LocalDate weekStart, String planType, String driveLink) {}

    /** Counts per status, the principal's totals view (FR-9.4). */
    public record Totals(long submitted, long returned, long approved) {}

    private final PlanningSubmissionRepository submissions;
    private final PlanningReviewRepository reviews;
    private final SubjectRepository subjects;
    private final SiteRepository sites;
    private final PersonRepository people;
    private final AuditPublisher audit;
    private final ApplicationEventPublisher publisher;
    private final AccessPolicy policy;
    private final Clock clock;

    // ================================================================== teacher

    /** Submits a plan, or resubmits a returned one for the same teacher, subject, grade and week. */
    @Transactional
    public PlanningSubmission submit(CurrentUser user, SubmitCommand cmd) {
        policy.requireRole(user, Role.TEACHER);
        if (isBlank(cmd.grade()) || isBlank(cmd.planType()) || isBlank(cmd.driveLink()) || cmd.weekStart() == null) {
            throw new BadRequestException("Please fill in the grade, week, type and the drive link.");
        }
        Subject subject = subjects.findById(cmd.subjectId())
                .filter(s -> s.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new BadRequestException("Please choose one of your school's subjects."));

        PlanningSubmission plan = submissions.findByTeacherIdAndSubjectIdAndGradeAndWeekStart(
                user.personId(), subject.getId(), cmd.grade().trim(), cmd.weekStart()).orElse(null);
        if (plan == null) {
            plan = new PlanningSubmission();
            plan.setOrgId(user.orgId());
            plan.setSiteId(user.siteId());
            plan.setTeacherId(user.personId());
            plan.setSubjectId(subject.getId());
            plan.setGrade(cmd.grade().trim());
            plan.setWeekStart(cmd.weekStart());
        } else if (plan.getStatus() == PlanningStatus.RETURNED) {
            plan.setResubmissionCount(plan.getResubmissionCount() + 1);
        } else {
            throw new ConflictException("This plan was already submitted.");
        }
        plan.setPlanType(cmd.planType().trim());
        plan.setDriveLink(cmd.driveLink().trim());
        plan.setStatus(PlanningStatus.SUBMITTED);
        plan.setSubmittedAt(Instant.now(clock));
        submissions.save(plan);

        audit.log(plan.getOrgId(), plan.getSiteId(), ActivityActions.PLANNING_SUBMITTED)
                .by(user).on("PlanningSubmission", plan.getId()).subject(plan.getSubjectId())
                .change(null, "week " + plan.getWeekStart()).save();
        if (subject.getCoordinatorId() != null) {
            publisher.publishEvent(new PersonNotice(subject.getCoordinatorId(), NotificationKind.PLANNING,
                    "خطة جديدة بانتظار مراجعتك", "A new plan is waiting for your review", subject.getName()));
        }
        return plan;
    }

    // ================================================================== coordinator

    /** Approves, or returns with a reason (a reason is mandatory when returning). */
    @Transactional
    public PlanningSubmission review(CurrentUser user, UUID submissionId, ReviewDecision decision, String reason) {
        PlanningSubmission plan = find(user, submissionId);
        if (!policy.coordinatesSubject(user, plan.getSiteId(), plan.getSubjectId())) {
            throw new ForbiddenException("Only the subject coordinator can review this plan.");
        }
        if (plan.getStatus() != PlanningStatus.SUBMITTED) {
            throw new ConflictException("This plan is not waiting for review.");
        }
        if (decision == ReviewDecision.RETURNED && isBlank(reason)) {
            throw new BadRequestException("Please tell the teacher why the plan is returned.");
        }

        PlanningReview review = new PlanningReview();
        review.setOrgId(plan.getOrgId());
        review.setSiteId(plan.getSiteId());
        review.setSubmissionId(plan.getId());
        review.setReviewerId(user.personId());
        review.setDecision(decision);
        review.setReason(isBlank(reason) ? null : reason.trim());
        reviews.save(review);

        plan.setStatus(decision == ReviewDecision.APPROVED ? PlanningStatus.APPROVED : PlanningStatus.RETURNED);
        audit.log(plan.getOrgId(), plan.getSiteId(), ActivityActions.PLANNING_REVIEWED)
                .by(user).on("PlanningSubmission", plan.getId()).subject(plan.getSubjectId())
                .change("SUBMITTED", plan.getStatus().name()).save();
        publisher.publishEvent(new PersonNotice(plan.getTeacherId(), NotificationKind.PLANNING,
                decision == ReviewDecision.APPROVED ? "تمت الموافقة على خطتك" : "أُعيدت خطتك للتعديل",
                decision == ReviewDecision.APPROVED ? "Your plan was approved" : "Your plan was returned",
                review.getReason()));
        return plan;
    }

    // ================================================================== reading

    /** Submissions the user may see: own (teacher), her subject's (coordinator), the school's (principal). */
    @Transactional(readOnly = true)
    public List<PlanningSubmission> list(CurrentUser user) {
        return switch (user.role()) {
            case TEACHER -> submissions.findByTeacherIdOrderByWeekStartDesc(user.personId());
            case COORDINATOR -> user.subjectId() == null
                    ? submissions.findBySiteIdOrderByWeekStartDesc(user.siteId())
                    : submissions.findBySiteIdAndSubjectIdOrderByWeekStartDesc(user.siteId(), user.subjectId());
            case PRINCIPAL -> submissions.findBySiteIdOrderByWeekStartDesc(user.siteId());
            case SCHOOL_IT, LEADERSHIP, STUDENT -> throw new ForbiddenException("Planning files are not available for your role.");
        };
    }

    /** The review history of one submission (resubmissions keep their history). */
    @Transactional(readOnly = true)
    public List<PlanningReview> history(CurrentUser user, UUID submissionId) {
        find(user, submissionId);
        return reviews.findBySubmissionIdOrderByCreatedAtAsc(submissionId);
    }

    /** Totals for a week: the principal's view (FR-9.4). */
    @Transactional(readOnly = true)
    public Totals totals(CurrentUser user, LocalDate weekStart) {
        policy.requireRole(user, Role.PRINCIPAL, Role.COORDINATOR);
        List<PlanningSubmission> week = submissions.findBySiteIdAndWeekStart(user.siteId(), weekStart);
        return new Totals(count(week, PlanningStatus.SUBMITTED), count(week, PlanningStatus.RETURNED),
                count(week, PlanningStatus.APPROVED));
    }

    // ================================================================== due-day reminder (scheduled)

    /**
     * One reminder to every teacher who has not submitted a plan for next week (FR-9.3). Called weekly by
     * {@code ScheduledJobs}. Returns how many teachers were reminded.
     */
    @Transactional
    public int remindMissing() {
        LocalDate nextWeek = LocalDate.now(clock).with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        int reminded = 0;
        for (Site site : sites.findAll()) {
            if (!site.isActive() || site.isHeadOffice()) {
                continue;
            }
            Set<UUID> submitted = submissions.findBySiteIdAndWeekStart(site.getId(), nextWeek).stream()
                    .map(PlanningSubmission::getTeacherId).collect(Collectors.toSet());
            for (Person teacher : people.findBySiteIdAndRole(site.getId(), Role.TEACHER)) {
                if (teacher.getStatus() != PersonStatus.LEFT && !submitted.contains(teacher.getId())) {
                    publisher.publishEvent(new PersonNotice(teacher.getId(), NotificationKind.PLANNING,
                            "تذكير: لم تُرسل خطة الأسبوع القادم", "Reminder: next week's plan is not submitted yet",
                            "Week of " + nextWeek));
                    reminded++;
                }
            }
        }
        return reminded;
    }

    // ================================================================== helpers

    /** A submission the user may see, else "not found". */
    private PlanningSubmission find(CurrentUser user, UUID id) {
        PlanningSubmission plan = submissions.findById(id).orElseThrow(() -> new NotFoundException("The plan"));
        boolean visible = switch (user.role()) {
            case TEACHER -> plan.getTeacherId().equals(user.personId());
            case COORDINATOR -> policy.coordinatesSubject(user, plan.getSiteId(), plan.getSubjectId());
            case PRINCIPAL -> policy.sameSite(user, plan.getSiteId());
            case SCHOOL_IT, LEADERSHIP, STUDENT -> false;
        };
        if (!visible) {
            throw new NotFoundException("The plan");
        }
        return plan;
    }

    private long count(List<PlanningSubmission> list, PlanningStatus status) {
        return list.stream().filter(p -> p.getStatus() == status).count();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
