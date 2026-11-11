package qa.sijill.sijill.issues.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.issues.event.FileRechecker;
import qa.sijill.sijill.issues.event.IssueEvents;
import qa.sijill.sijill.issues.model.ExceptionGrant;
import qa.sijill.sijill.issues.model.Issue;
import qa.sijill.sijill.issues.model.IssueStatus;
import qa.sijill.sijill.issues.repository.ExceptionGrantRepository;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.AccessPolicy;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Allowed exceptions (FR-2.5): a coordinator decides a file may break a rule until a given date.
 *
 * <ul>
 *   <li><b>Allow:</b> only the subject's coordinator; a reason and an end date are mandatory. The issue
 *       closes as "Allowed exception" and the owner is told.</li>
 *   <li><b>End:</b> on the end date (a daily job) or manually by the coordinator or School IT. The rule
 *       applies again at once: the file is re-checked and, if still broken, a new issue tells the owner.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExceptionService {

    private final ExceptionGrantRepository exceptions;
    private final IssueService issueService;
    private final FileRechecker rechecker;
    private final AuditPublisher audit;
    private final ApplicationEventPublisher publisher;
    private final AccessPolicy policy;
    private final Clock clock;

    // ================================================================== allow

    @Transactional
    public ExceptionGrant grant(CurrentUser user, UUID issueId, String reason, LocalDate endsOn) {
        Issue issue = issueService.getVisible(user, issueId);
        policy.requireCoordinator(user, issue.scope());

        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Please give a reason for the exception.");
        }
        if (endsOn == null || endsOn.isBefore(LocalDate.now(clock))) {
            throw new BadRequestException("Please choose an end date that is today or later.");
        }
        if (!issue.isOpen()) {
            throw new ConflictException("This issue is already closed.");
        }

        ExceptionGrant grant = new ExceptionGrant();
        grant.setOrgId(issue.getOrgId());
        grant.setSiteId(issue.getSiteId());
        grant.setFileId(issue.getFileId());
        grant.setRuleId(issue.getRuleId());
        grant.setIssueId(issue.getId());
        grant.setSubjectId(issue.getSubjectId());
        grant.setReason(reason.trim());
        grant.setGrantedBy(user.personId());
        grant.setEndsOn(endsOn);
        exceptions.save(grant);

        issueService.closeAs(issue, IssueStatus.ALLOWED_EXCEPTION, user.personId(),
                "Exception until " + endsOn + ": " + reason.trim());
        audit.log(issue.getOrgId(), issue.getSiteId(), ActivityActions.EXCEPTION_GRANTED)
                .by(user).on("Exception", grant.getId()).subject(issue.getSubjectId())
                .change(null, "until " + endsOn).save();
        publisher.publishEvent(new IssueEvents.ExceptionGranted(issue.getId(), endsOn));
        return grant;
    }

    // ================================================================== end

    /** Ends an exception by hand: the subject's coordinator, or School IT of the same school. */
    @Transactional
    public void end(CurrentUser user, UUID exceptionId) {
        ExceptionGrant grant = exceptions.findById(exceptionId)
                .filter(g -> g.getOrgId().equals(user.orgId()))
                .orElseThrow(() -> new NotFoundException("The exception"));

        boolean allowed = policy.coordinatesSubject(user, grant.getSiteId(), grant.getSubjectId())
                || (user.is(Role.SCHOOL_IT) && policy.sameSite(user, grant.getSiteId()));
        if (!allowed) {
            throw new ForbiddenException("Only the subject coordinator or School IT can end this exception.");
        }
        if (!grant.isActive()) {
            throw new ConflictException("This exception has already ended.");
        }
        close(grant, user.personId());
    }

    /** Daily job: ends every exception whose date has passed. Returns how many ended. */
    @Transactional
    public int expireDue() {
        List<ExceptionGrant> due = exceptions.findByEndedAtIsNullAndEndsOnBefore(LocalDate.now(clock));
        due.forEach(g -> close(g, null));
        return due.size();
    }

    private void close(ExceptionGrant grant, UUID endedBy) {
        grant.setEndedAt(Instant.now(clock));
        grant.setEndedBy(endedBy);
        audit.log(grant.getOrgId(), grant.getSiteId(), ActivityActions.EXCEPTION_ENDED)
                .by(endedBy).on("Exception", grant.getId()).save();
        // The rule applies again: re-check now; a still-broken file gets a new issue and the owner is told.
        rechecker.recheck(grant.getFileId());
    }

    // ================================================================== reading

    /** The Exceptions table (FR-11.4): coordinators see their subject's, principals and IT their school's, leadership all. */
    @Transactional(readOnly = true)
    public List<ExceptionGrant> listActive(CurrentUser user) {
        return switch (user.role()) {
            case LEADERSHIP -> exceptions.findByOrgIdAndEndedAtIsNullOrderByEndsOnAsc(user.orgId());
            case PRINCIPAL, SCHOOL_IT -> exceptions.findBySiteIdAndEndedAtIsNullOrderByEndsOnAsc(user.siteId());
            case COORDINATOR -> exceptions.findBySiteIdAndEndedAtIsNullOrderByEndsOnAsc(user.siteId()).stream()
                    .filter(g -> policy.coordinatesSubject(user, g.getSiteId(), g.getSubjectId())).toList();
            case TEACHER, STUDENT, PARENT -> throw new ForbiddenException("Exceptions are not available for your role.");
        };
    }
}
