package qa.sijill.sijill.notifications.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import qa.sijill.sijill.notifications.model.Notification;
import qa.sijill.sijill.notifications.repository.NotificationRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.shared.events.PersonNotice;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.shared.util.MessageTemplates;

/**
 * The in-app notifications behind the header bell (FR-11.2): create one, and let a person read them,
 * see the unread count and mark them read. <i>Deciding</i> who gets which notification for an issue is
 * {@link IssueNotificationListener}'s job; this class only stores and serves them.
 *
 * <p>It also turns a plain {@link PersonNotice} (published by planning review, the welcome message, …)
 * into a bell entry in the person's language.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final PersonRepository people;
    private final Clock clock;

    // =============================================================== creating

    /** Stores one bell entry for a person. */
    @Transactional
    public void send(Person recipient, NotificationKind kind, String title, String body, UUID issueId) {
        Notification n = new Notification();
        n.setOrgId(recipient.getOrgId());
        n.setSiteId(recipient.getSiteId());
        n.setRecipientId(recipient.getId());
        n.setKind(kind);
        n.setTitle(title);
        n.setBody(body);
        n.setIssueId(issueId);
        notifications.save(n);
    }

    /** A plain in-app notice published by another module. */
    @EventListener
    @Transactional
    public void on(PersonNotice notice) {
        people.findById(notice.personId()).ifPresent(p -> send(p, notice.kind(),
                MessageTemplates.title(p.getLanguage(), notice.titleAr(), notice.titleEn()), notice.body(), null));
    }

    // =============================================================== reading (the header bell)

    @Transactional(readOnly = true)
    public List<Notification> mine(CurrentUser user) {
        return notifications.findTop50ByRecipientIdOrderByCreatedAtDesc(user.personId());
    }

    @Transactional(readOnly = true)
    public long unreadCount(CurrentUser user) {
        return notifications.countByRecipientIdAndReadAtIsNull(user.personId());
    }

    @Transactional
    public void markRead(CurrentUser user, UUID notificationId) {
        Notification n = notifications.findById(notificationId)
                .filter(x -> x.getRecipientId().equals(user.personId()))
                .orElseThrow(() -> new NotFoundException("The notification"));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now(clock));
        }
    }

    /** "Mark all as read". Returns how many were marked. */
    @Transactional
    public int markAllRead(CurrentUser user) {
        return notifications.markAllRead(user.personId(), Instant.now(clock));
    }
}
