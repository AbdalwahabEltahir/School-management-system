package qa.hafez.hafez.notifications.dto;

import java.time.Instant;
import java.util.UUID;

import qa.hafez.hafez.notifications.model.Notification;

/** Response shapes of the notifications API (the header bell, FR-11.2). */
public final class NotificationDtos {

    private NotificationDtos() {}

    public record NotificationView(UUID id, String kind, String title, String body, UUID issueId, Instant createdAt,
                                   boolean read) {
        public static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getKind().name(), n.getTitle(), n.getBody(), n.getIssueId(),
                    n.getCreatedAt(), n.getReadAt() != null);
        }
    }

    public record UnreadView(long unread) {}

    public record MarkedView(int marked) {}
}
