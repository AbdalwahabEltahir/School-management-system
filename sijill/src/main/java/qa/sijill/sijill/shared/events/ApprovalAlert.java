package qa.sijill.sijill.shared.events;

import java.util.Set;
import java.util.UUID;

import qa.sijill.sijill.shared.model.Role;

/**
 * "A sensitive action is waiting for a second person." Published by the approvals module; the notifications module
 * turns it into a bell entry for everyone in the given roles (network roles across the organisation, other roles in the
 * school), except the person who asked.
 */
public record ApprovalAlert(UUID orgId, UUID siteId, Set<Role> approvers, UUID exceptPersonId, String summary) {
}
