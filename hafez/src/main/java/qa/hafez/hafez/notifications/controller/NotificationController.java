package qa.hafez.hafez.notifications.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.hafez.hafez.notifications.dto.NotificationDtos;
import qa.hafez.hafez.notifications.service.NotificationService;
import qa.hafez.hafez.shared.security.CurrentUser;

/** The header bell (FR-11.2): the person's own notifications, the unread count, mark as read. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;

    @GetMapping
    public List<NotificationDtos.NotificationView> mine(CurrentUser user) {
        return notifications.mine(user).stream().map(NotificationDtos.NotificationView::from).toList();
    }

    @GetMapping("/unread-count")
    public NotificationDtos.UnreadView unread(CurrentUser user) {
        return new NotificationDtos.UnreadView(notifications.unreadCount(user));
    }

    @PostMapping("/{notificationId}/read")
    public void markRead(CurrentUser user, @PathVariable UUID notificationId) {
        notifications.markRead(user, notificationId);
    }

    /** "Mark all as read". */
    @PostMapping("/read-all")
    public NotificationDtos.MarkedView markAllRead(CurrentUser user) {
        return new NotificationDtos.MarkedView(notifications.markAllRead(user));
    }
}
