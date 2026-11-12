package qa.hafez.hafez.shared.events;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import qa.hafez.hafez.shared.security.CurrentUser;

/**
 * The one-line way to write to the activity log from any module:
 * <pre>{@code
 * audit.log(orgId, siteId, ActivityActions.RULE_UPDATED)
 *      .by(user).on("Rule", rule.getId()).change("v1", "v2").save();
 * }</pre>
 * {@code save()} publishes an {@link AuditEvent}; the {@code audit} module appends it to the hash-chained
 * log <i>in the same transaction</i>, so a change and its log entry commit or roll back together.
 */
@Component
@RequiredArgsConstructor
public class AuditPublisher {

    private final ApplicationEventPublisher events;

    /** Starts an entry. Nothing is published until {@link Entry#save()}. */
    public Entry log(UUID orgId, UUID siteId, String action) {
        return new Entry(orgId, siteId, action);
    }

    /** Fluent builder for one audit entry. */
    public final class Entry {
        private final UUID orgId;
        private final UUID siteId;
        private final String action;
        private UUID actorId;
        private String entityType = "System";
        private UUID entityId;
        private UUID subjectId;
        private String before;
        private String after;

        private Entry(UUID orgId, UUID siteId, String action) {
            this.orgId = orgId;
            this.siteId = siteId;
            this.action = action;
        }

        /** The person who acted. Leave unset when Hafez itself acted. */
        public Entry by(CurrentUser user) {
            this.actorId = user == null ? null : user.personId();
            return this;
        }

        public Entry by(UUID personId) {
            this.actorId = personId;
            return this;
        }

        /** What the action was about, e.g. {@code on("Issue", issueId)}. */
        public Entry on(String entityType, UUID entityId) {
            this.entityType = entityType;
            this.entityId = entityId;
            return this;
        }

        /** The subject of the entity, so coordinators can read their subject's part of the log. */
        public Entry subject(UUID subjectId) {
            this.subjectId = subjectId;
            return this;
        }

        /** Short before/after descriptions. */
        public Entry change(String before, String after) {
            this.before = before;
            this.after = after;
            return this;
        }

        public void save() {
            events.publishEvent(new AuditEvent(orgId, siteId, actorId, action, entityType, entityId, subjectId, before, after));
        }
    }
}
