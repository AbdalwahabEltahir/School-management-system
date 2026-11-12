package qa.sijill.sijill.approvals.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.approvals.ApprovalHandler;
import qa.sijill.sijill.approvals.model.ApprovalRequest;
import qa.sijill.sijill.approvals.model.ApprovalStatus;
import qa.sijill.sijill.approvals.repository.ApprovalRequestRepository;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * The four-eyes principle in one place. A module asks for an action with {@link #request}; the action runs only when a
 * different authorised person approves it, and every step is written to the activity log.
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository requests;
    private final AuditPublisher audit;
    private final Clock clock;
    private final org.springframework.beans.factory.ObjectProvider<ApprovalHandler> handlerBeans;
    private volatile Map<String, ApprovalHandler> handlerMap;

    ApprovalService(ApprovalRequestRepository requests, AuditPublisher audit, Clock clock,
                    org.springframework.beans.factory.ObjectProvider<ApprovalHandler> handlerBeans) {
        this.requests = requests;
        this.audit = audit;
        this.clock = clock;
        this.handlerBeans = handlerBeans;
    }

    /** Looked up on first use: the handlers belong to modules that themselves call this service. */
    private Map<String, ApprovalHandler> handlers() {
        Map<String, ApprovalHandler> m = handlerMap;
        if (m == null) {
            m = handlerBeans.stream().collect(Collectors.toMap(ApprovalHandler::action, Function.identity()));
            handlerMap = m;
        }
        return m;
    }

    /** Asks for a sensitive action. The caller has already checked that the user may ask. */
    @Transactional
    public ApprovalRequest request(CurrentUser user, UUID siteId, String action, UUID targetId, String payload, String summary) {
        if (!handlers().containsKey(action)) {
            throw new BadRequestException("Unknown action.");
        }
        if (targetId != null && requests.findFirstByActionAndTargetIdAndStatus(action, targetId, ApprovalStatus.PENDING).isPresent()) {
            throw new ConflictException("This is already waiting for approval.");
        }
        ApprovalRequest r = new ApprovalRequest();
        r.setOrgId(user.orgId());
        r.setSiteId(siteId);
        r.setAction(action);
        r.setTargetId(targetId);
        r.setPayload(payload);
        r.setSummary(summary.length() > 300 ? summary.substring(0, 300) : summary);
        r.setRequestedBy(user.personId());
        r.setRequestedByName(user.name());
        requests.save(r);
        audit.log(r.getOrgId(), siteId, "APPROVAL_REQUESTED").by(user).on("ApprovalRequest", r.getId())
                .change(null, action + ": " + r.getSummary()).save();
        return r;
    }

    /** Requests the user may see: everything in the network for network roles, otherwise their school's. */
    @Transactional(readOnly = true)
    public List<ApprovalRequest> list(CurrentUser user) {
        if (user.isNetwork()) {
            return requests.findTop100ByOrgIdOrderByCreatedAtDesc(user.orgId());
        }
        return requests.findTop100BySiteIdOrderByCreatedAtDesc(user.siteId());
    }

    /** Whether this user could decide the request (right role, different person, still pending). */
    public boolean canDecide(CurrentUser user, ApprovalRequest r) {
        ApprovalHandler h = handlers().get(r.getAction());
        return h != null && r.getStatus() == ApprovalStatus.PENDING && h.approvers().contains(user.role())
                && !r.getRequestedBy().equals(user.personId()) && h.mayDecide(user, r) && visible(user, r);
    }

    @Transactional
    public ApprovalRequest approve(CurrentUser user, UUID id) {
        ApprovalRequest r = pending(user, id);
        authorise(user, r);
        handlers().get(r.getAction()).execute(r, user);
        decide(user, r, ApprovalStatus.APPROVED, null);
        return r;
    }

    @Transactional
    public ApprovalRequest reject(CurrentUser user, UUID id, String note) {
        ApprovalRequest r = pending(user, id);
        authorise(user, r);
        decide(user, r, ApprovalStatus.REJECTED, note);
        return r;
    }

    /** The requester withdraws their own request. */
    @Transactional
    public ApprovalRequest cancel(CurrentUser user, UUID id) {
        ApprovalRequest r = pending(user, id);
        if (!r.getRequestedBy().equals(user.personId())) {
            throw new ForbiddenException("Only the person who asked can withdraw this.");
        }
        decide(user, r, ApprovalStatus.CANCELLED, null);
        return r;
    }

    private ApprovalRequest pending(CurrentUser user, UUID id) {
        ApprovalRequest r = requests.findById(id).filter(x -> visible(user, x)).orElseThrow(() -> new NotFoundException("The request"));
        if (r.getStatus() != ApprovalStatus.PENDING) {
            throw new ConflictException("This request was already decided.");
        }
        return r;
    }

    private boolean visible(CurrentUser user, ApprovalRequest r) {
        return r.getOrgId().equals(user.orgId()) && (user.isNetwork() || r.getSiteId().equals(user.siteId()));
    }

    private void authorise(CurrentUser user, ApprovalRequest r) {
        ApprovalHandler h = handlers().get(r.getAction());
        if (h == null || !h.approvers().contains(user.role()) || !h.mayDecide(user, r)) {
            throw new ForbiddenException("Your role cannot approve this.");
        }
        if (r.getRequestedBy().equals(user.personId())) {
            throw new ForbiddenException("A different person must approve this. You cannot approve your own request.");
        }
    }

    private void decide(CurrentUser user, ApprovalRequest r, ApprovalStatus status, String note) {
        r.setStatus(status);
        r.setDecidedBy(user.personId());
        r.setDecidedByName(user.name());
        r.setDecidedAt(Instant.now(clock));
        r.setDecisionNote(note == null || note.isBlank() ? null : (note.length() > 500 ? note.substring(0, 500) : note));
        requests.save(r);
        audit.log(r.getOrgId(), r.getSiteId(), "APPROVAL_" + status.name()).by(user).on("ApprovalRequest", r.getId())
                .change(r.getAction(), r.getSummary()).save();
    }
}
