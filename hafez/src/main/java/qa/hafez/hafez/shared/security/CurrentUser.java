package qa.hafez.hafez.shared.security;

import java.util.UUID;

import qa.hafez.hafez.shared.model.Role;

/**
 * The signed-in person as the rest of the application sees them: an immutable snapshot taken at the
 * start of the request. Controllers receive it as a method parameter (see
 * {@link CurrentUserArgumentResolver}); services pass it to {@link AccessPolicy} to enforce who may do what.
 *
 * @param personId  the person's id in the {@code people} table
 * @param orgId     organisation (school network)
 * @param siteId    the person's school (or head office)
 * @param role      the single role they hold
 * @param subjectId the subject they teach/coordinate; null for principals, IT and leadership
 * @param email     the school account they signed in with
 * @param name      display name, for logs and messages
 */
public record CurrentUser(UUID personId, UUID orgId, UUID siteId, Role role, UUID subjectId, String email, String name) {

    public boolean is(Role r) {
        return role == r;
    }

    /** Leadership (read-only) or EAA admin: sees every school of the network. */
    public boolean isNetwork() {
        return role.isNetwork();
    }

    public boolean isAny(Role... roles) {
        for (Role r : roles) {
            if (role == r) {
                return true;
            }
        }
        return false;
    }
}
