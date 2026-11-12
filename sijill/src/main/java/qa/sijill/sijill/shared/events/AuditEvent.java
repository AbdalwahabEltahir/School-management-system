package qa.sijill.sijill.shared.events;

import java.util.UUID;

/**
 * "Something auditable happened." Any module publishes this (through {@link AuditPublisher}); the
 * {@code audit} module listens and appends it to the tamper-evident activity log.
 *
 * <p>This is how every change gets logged without the changing module knowing anything about the log:
 * no module except {@code audit} depends on it, and a future module (Phase 2) gets logging for free by
 * publishing the same event.
 *
 * @param actorId null when Hafez itself acted (a scheduled check)
 * @param before  short description of the old state; never student names, IDs or health details
 * @param after   short description of the new state
 */
public record AuditEvent(UUID orgId, UUID siteId, UUID actorId, String action, String entityType, UUID entityId,
                         UUID subjectId, String before, String after) {
}
