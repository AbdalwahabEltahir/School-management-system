package qa.sijill.sijill.backup.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.backup.model.RestoreRequest;
import qa.sijill.sijill.backup.model.RestoreScope;
import qa.sijill.sijill.backup.model.RestoreStatus;
import qa.sijill.sijill.backup.repository.RestoreRequestRepository;
import qa.sijill.sijill.files.model.SchoolFile;
import qa.sijill.sijill.files.repository.SchoolFileRepository;
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
 * Restore requests (FR-13.3, proposal 9.4). <b>Hafez only tracks them</b>: School IT performs the
 * restore from the trash or the backup vault with the workspace's own tools, so Hafez stays read-only.
 *
 * <pre>
 *  coordinator / principal ──request──► REQUESTED ──approve──► APPROVED ──complete (IT)──► RESTORED
 *                                           └──reject──► REJECTED
 * </pre>
 * A single file needs one approval; a whole subject drive or school needs <b>two different approvers</b>
 * (School IT, or Leadership as the second person). Every step is audited.
 */
@Service
@RequiredArgsConstructor
public class RestoreService {

    /** What a coordinator or principal sends when asking for a restore. */
    public record RequestCommand(UUID fileId, RestoreScope scope, String target, String reason) {}

    private final RestoreRequestRepository restores;
    private final SchoolFileRepository files;
    private final AuditPublisher audit;
    private final AccessPolicy policy;
    private final Clock clock;

    @Transactional
    public RestoreRequest request(CurrentUser user, RequestCommand cmd) {
        policy.requireRole(user, Role.COORDINATOR, Role.PRINCIPAL);
        if (cmd.scope() == null || cmd.reason() == null || cmd.reason().isBlank()) {
            throw new BadRequestException("Please say what to restore and why.");
        }
        String target = cmd.target();
        if (cmd.scope() == RestoreScope.FILE) {
            SchoolFile file = files.findById(cmd.fileId())
                    .filter(f -> policy.sameSite(user, f.getSiteId()))
                    .orElseThrow(() -> new NotFoundException("The file"));
            target = file.getName();
        } else if (target == null || target.isBlank()) {
            throw new BadRequestException("Please name the drive to restore.");
        }

        RestoreRequest r = new RestoreRequest();
        r.setOrgId(user.orgId());
        r.setSiteId(user.siteId());
        r.setFileId(cmd.scope() == RestoreScope.FILE ? cmd.fileId() : null);
        r.setScope(cmd.scope());
        r.setTarget(target);
        r.setReason(cmd.reason().trim());
        r.setRequestedBy(user.personId());
        restores.save(r);
        audit.log(r.getOrgId(), r.getSiteId(), ActivityActions.RESTORE_REQUESTED)
                .by(user).on("RestoreRequest", r.getId()).change(null, r.getScope().name()).save();
        return r;
    }

    /** Approves: one approver for a file, two different approvers for a drive or school. */
    @Transactional
    public RestoreRequest approve(CurrentUser user, UUID requestId) {
        RestoreRequest r = findForApprover(user, requestId);
        if (r.getStatus() != RestoreStatus.REQUESTED) {
            throw new ConflictException("This request is no longer waiting for approval.");
        }
        boolean twoPeople = r.getScope() != RestoreScope.FILE;
        if (r.getFirstApproverId() == null) {
            r.setFirstApproverId(user.personId());
            if (!twoPeople) {
                r.setStatus(RestoreStatus.APPROVED);
            }
        } else {
            if (r.getFirstApproverId().equals(user.personId())) {
                throw new ForbiddenException("A second, different person must approve this restore.");
            }
            r.setSecondApproverId(user.personId());
            r.setStatus(RestoreStatus.APPROVED);
        }
        audit.log(r.getOrgId(), r.getSiteId(), ActivityActions.RESTORE_APPROVED)
                .by(user).on("RestoreRequest", r.getId()).change(null, r.getStatus().name()).save();
        return r;
    }

    @Transactional
    public RestoreRequest reject(CurrentUser user, UUID requestId) {
        RestoreRequest r = findForApprover(user, requestId);
        if (r.getStatus() != RestoreStatus.REQUESTED) {
            throw new ConflictException("This request is no longer waiting for approval.");
        }
        r.setStatus(RestoreStatus.REJECTED);
        audit.log(r.getOrgId(), r.getSiteId(), ActivityActions.RESTORE_REJECTED).by(user).on("RestoreRequest", r.getId()).save();
        return r;
    }

    /** School IT records that the restore was done (with the workspace's own tools). */
    @Transactional
    public RestoreRequest complete(CurrentUser user, UUID requestId) {
        policy.requireRole(user, Role.SCHOOL_IT);
        RestoreRequest r = findForApprover(user, requestId);
        if (r.getStatus() != RestoreStatus.APPROVED) {
            throw new ConflictException("The restore must be approved first.");
        }
        r.setStatus(RestoreStatus.RESTORED);
        r.setRestoredAt(Instant.now(clock));
        audit.log(r.getOrgId(), r.getSiteId(), ActivityActions.RESTORE_COMPLETED).by(user).on("RestoreRequest", r.getId()).save();
        return r;
    }

    /** Requests of the user's school (coordinators, principals and IT); Leadership passes a school id. */
    @Transactional(readOnly = true)
    public List<RestoreRequest> list(CurrentUser user, UUID siteId) {
        if (user.is(Role.TEACHER)) {
            throw new ForbiddenException("Restore requests are not available for your role.");
        }
        UUID target = user.isNetwork() && siteId != null ? siteId : user.siteId();
        return restores.findBySiteIdOrderByCreatedAtDesc(target).stream()
                .filter(r -> r.getOrgId().equals(user.orgId())).toList();
    }

    /** A request that School IT of that school, or Leadership, may act on; otherwise "not found". */
    private RestoreRequest findForApprover(CurrentUser user, UUID requestId) {
        policy.requireRole(user, Role.SCHOOL_IT, Role.EAA_ADMIN);
        return restores.findById(requestId)
                .filter(r -> r.getOrgId().equals(user.orgId()))
                .filter(r -> user.isNetwork() || policy.sameSite(user, r.getSiteId()))
                .orElseThrow(() -> new NotFoundException("The restore request"));
    }
}
