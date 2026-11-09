package qa.sijill.sijill.shared.security;

import java.util.UUID;

/**
 * The few facts about a record that decide who may see it: its school, its subject and its owner.
 * Files and issues describe themselves with {@code scope()}, so {@link AccessPolicy} (shared) never
 * has to import a business module's entity.
 *
 * @param ownerId    owning person's id (issues); may be null
 * @param ownerEmail owning person's school account (files); may be null
 */
public record AccessScope(UUID siteId, UUID subjectId, UUID ownerId, String ownerEmail) {
}
