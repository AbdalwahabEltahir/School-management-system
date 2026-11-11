package qa.sijill.sijill.issues.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.issues.event.IssueEvents;
import qa.sijill.sijill.issues.model.Incident;
import qa.sijill.sijill.issues.model.IncidentType;
import qa.sijill.sijill.issues.model.Issue;
import qa.sijill.sijill.issues.repository.IncidentRepository;
import qa.sijill.sijill.issues.repository.IssueRepository;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Records <b>major incidents</b>, the only events leadership hears about (proposal 2 and 5.4):
 * a leaked exam, an important deletion, 20+ deletions in an hour, an urgent issue left open, a lost
 * connection. Raising one publishes {@link IssueEvents.IncidentRaised}; the notification module alerts
 * the owner, coordinator, principal and leadership.
 *
 * <p>Exam leaks and important deletions are recognised from the {@link IssueEvents.Opened} event's
 * incident hint, which the rule engine sets for rules R6 and R10. Bulk deletions and lost connections
 * are detected by the check pipeline, which calls {@link #raiseBulkDeletion} / {@link #connectionLost}.
 */
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidents;
    private final IssueRepository issues;
    private final AuditPublisher audit;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    /** An issue was opened that amounts to a major incident (exam leak, important deletion). */
    @EventListener
    @Transactional
    public void onIssueOpened(IssueEvents.Opened event) {
        if (event.incidentHint() == null) {
            return;
        }
        issues.findById(event.issueId()).ifPresent(issue -> {
            String text = event.incidentHint() == IncidentType.EXAM_LEAK
                    ? "An exam file was shared before its date."
                    : "An important file was deleted or moved to the trash.";
            raiseForIssue(event.incidentHint(), issue, text);
        });
    }

    /** An urgent issue is still open after one school day. Raised once per issue. */
    @Transactional
    public void urgentUnresolved(Issue issue) {
        raiseForIssue(IncidentType.URGENT_UNRESOLVED, issue, "An urgent issue is still open after one school day.");
    }

    /** 20+ deletions within an hour in one school (counted and thresholded by the check pipeline). */
    @Transactional
    public void raiseBulkDeletion(UUID orgId, UUID siteId, long deletions) {
        if (!incidents.existsBySiteIdAndTypeAndResolvedAtIsNull(siteId, IncidentType.BULK_DELETION)) {
            raise(IncidentType.BULK_DELETION, orgId, siteId, null, null, deletions + " files were deleted within an hour.");
        }
    }

    /** The workspace connection has been down for over an hour. Raised once until resolved. */
    @Transactional
    public void connectionLost(UUID orgId, UUID siteId) {
        if (!incidents.existsBySiteIdAndTypeAndResolvedAtIsNull(siteId, IncidentType.CONNECTION_LOST)) {
            raise(IncidentType.CONNECTION_LOST, orgId, siteId, null, null,
                    "The connection to the school workspace has been lost for over an hour.");
        }
    }

    private void raiseForIssue(IncidentType type, Issue issue, String description) {
        if (!incidents.existsByIssueIdAndTypeAndResolvedAtIsNull(issue.getId(), type)) {
            raise(type, issue.getOrgId(), issue.getSiteId(), issue.getFileId(), issue.getId(), description);
        }
    }

    private void raise(IncidentType type, UUID orgId, UUID siteId, UUID fileId, UUID issueId, String description) {
        Incident incident = new Incident();
        incident.setOrgId(orgId);
        incident.setSiteId(siteId);
        incident.setType(type);
        incident.setFileId(fileId);
        incident.setIssueId(issueId);
        incident.setDescription(description);
        incident.setDetectedAt(Instant.now(clock));
        incident.setLeadershipAlertedAt(Instant.now(clock));
        incidents.save(incident);

        audit.log(orgId, siteId, ActivityActions.INCIDENT_RAISED)
                .on("Incident", incident.getId()).change(null, type.name()).save();
        publisher.publishEvent(new IssueEvents.IncidentRaised(incident.getId()));
    }

    // ------------------------------------------------------------------ reading and resolving

    /** Leadership sees every school's incidents; coordinator, principal and IT see their school's. */
    @Transactional(readOnly = true)
    public List<Incident> list(CurrentUser user) {
        return switch (user.role()) {
            case LEADERSHIP -> incidents.findByOrgIdOrderByDetectedAtDesc(user.orgId());
            case PRINCIPAL, SCHOOL_IT, COORDINATOR -> incidents.findBySiteIdOrderByDetectedAtDesc(user.siteId());
            case TEACHER, STUDENT, PARENT -> throw new ForbiddenException("Incidents are not available for your role.");
        };
    }

    /** Marks an incident as dealt with. Coordinators and School IT for their school; Leadership for any. */
    @Transactional
    public Incident resolve(CurrentUser user, UUID incidentId) {
        if (!user.isAny(Role.COORDINATOR, Role.SCHOOL_IT, Role.LEADERSHIP)) {
            throw new ForbiddenException("Your role is not allowed to resolve incidents.");
        }
        Incident incident = incidents.findById(incidentId)
                .filter(i -> i.getOrgId().equals(user.orgId()))
                .filter(i -> user.is(Role.LEADERSHIP) || i.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new NotFoundException("The incident"));
        if (incident.getResolvedAt() != null) {
            throw new ConflictException("This incident is already resolved.");
        }
        incident.setResolvedAt(Instant.now(clock));
        audit.log(incident.getOrgId(), incident.getSiteId(), ActivityActions.INCIDENT_RESOLVED)
                .by(user).on("Incident", incident.getId()).save();
        return incident;
    }
}
