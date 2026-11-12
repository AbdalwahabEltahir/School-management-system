package qa.sijill.sijill.shared.security;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;

/**
 * The single place that decides <b>who may see and do what</b> (proposal 4, permissions matrix).
 * Every service calls it; controllers never decide permissions and the UI never hides buttons as the
 * only protection ("every permission is enforced on the server"). It is deliberately a small class on
 * its own so a reviewer or penetration tester can audit all access rules in one place.
 *
 * <p>Visibility summary
 * <pre>
 *  Teacher      own files and own issues only
 *  Coordinator  her subject's files and issues (a coordinator without a subject covers the whole school)
 *  Principal    everything in her school, read-only
 *  School IT    counts only – no file or issue details
 *  Leadership   network totals only – no file or issue details
 * </pre>
 * For records a person may not see, the {@code require...View} methods throw {@link NotFoundException}
 * rather than {@link ForbiddenException}, so the existence of other people's files is never revealed.
 */
@Component
public class AccessPolicy {

    // ------------------------------------------------------------------ roles

    /** Requires one of the given roles, otherwise 403. */
    public void requireRole(CurrentUser user, Role... allowed) {
        if (!user.isAny(allowed)) {
            throw new ForbiddenException("Your role is not allowed to do this.");
        }
    }

    // ------------------------------------------------------------------ sites

    public boolean sameSite(CurrentUser user, UUID siteId) {
        return Objects.equals(user.siteId(), siteId);
    }

    /** The user belongs to the given school, otherwise 403. */
    public void requireSameSite(CurrentUser user, UUID siteId) {
        if (!sameSite(user, siteId)) {
            throw new ForbiddenException("This belongs to another school.");
        }
    }

    /** School IT may act on their own school; Leadership may act on any school of the organisation. */
    public void requireSiteAccess(CurrentUser user, UUID siteId, UUID siteOrgId) {
        boolean ok = user.isNetwork()
                ? Objects.equals(user.orgId(), siteOrgId)
                : sameSite(user, siteId);
        if (!ok) {
            throw new ForbiddenException("This belongs to another school.");
        }
    }

    // ------------------------------------------------------------------ coordinators

    /**
     * True when the user is a coordinator of the given subject at their own school. A coordinator with
     * no subject of her own (the school-level coordinator) covers every subject of her school, and a
     * record whose subject is not known yet can be handled by any coordinator of the school, so that
     * somebody is always able to classify it.
     */
    public boolean coordinatesSubject(CurrentUser user, UUID siteId, UUID subjectId) {
        if (!user.is(Role.COORDINATOR) || !sameSite(user, siteId)) {
            return false;
        }
        return user.subjectId() == null || subjectId == null || Objects.equals(user.subjectId(), subjectId);
    }

    // ------------------------------------------------------------------ files and issues

    /** May this user open the record (a file or an issue) described by the scope? */
    public boolean canView(CurrentUser user, AccessScope scope) {
        if (isOwner(user, scope)) {
            return true; // everyone sees their own files and issues
        }
        return switch (user.role()) {
            case COORDINATOR -> coordinatesSubject(user, scope.siteId(), scope.subjectId());
            case PRINCIPAL -> sameSite(user, scope.siteId());
            case TEACHER, SCHOOL_IT, ATTENDANCE_OFFICER, LEADERSHIP, EAA_ADMIN, STUDENT, PARENT -> false;
        };
    }

    /** Throws "not found" unless the user may see the record. */
    public void requireView(CurrentUser user, AccessScope scope, String what) {
        if (!canView(user, scope)) {
            throw new NotFoundException(what);
        }
    }

    /** Only the record's owner (e.g. the file owner pressing "I've fixed it"). */
    public void requireOwner(CurrentUser user, AccessScope scope) {
        if (!isOwner(user, scope)) {
            throw new ForbiddenException("Only the owner of the file can do this.");
        }
    }

    /** Only the coordinator of the record's subject (keep the rule, allow an exception, …). */
    public void requireCoordinator(CurrentUser user, AccessScope scope) {
        if (!coordinatesSubject(user, scope.siteId(), scope.subjectId())) {
            throw new ForbiddenException("Only the subject coordinator can do this.");
        }
    }

    private boolean isOwner(CurrentUser user, AccessScope scope) {
        boolean byId = scope.ownerId() != null && scope.ownerId().equals(user.personId());
        boolean byEmail = scope.ownerEmail() != null && scope.ownerEmail().equalsIgnoreCase(user.email());
        return byId || byEmail;
    }
}
