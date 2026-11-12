package qa.hafez.hafez.notifications.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.notifications.integration.OutboundMailer;
import qa.hafez.hafez.notifications.model.Message;
import qa.hafez.hafez.notifications.repository.MessageRepository;
import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.people.repository.PersonRepository;

/**
 * Sends logged messages whose delivery time has come (quiet hours already applied when they were created).
 *
 * <p>Messages live in the database first, so queued work "resumes after any outage" (proposal 6): if no
 * {@link OutboundMailer} is configured, or sending fails, the message simply stays unsent and is retried
 * on the next run.
 */
@Service
@RequiredArgsConstructor
public class MessageDispatchService {

    private static final Logger log = LoggerFactory.getLogger(MessageDispatchService.class);

    private final MessageRepository messages;
    private final PersonRepository people;
    private final ObjectProvider<OutboundMailer> mailer;
    private final Clock clock;

    /** Called every minute by {@code ScheduledJobs}. Returns how many messages were sent. */
    @Transactional
    public int dispatchDue() {
        OutboundMailer out = mailer.getIfAvailable();
        if (out == null) {
            log.debug("No OutboundMailer configured; messages stay queued.");
            return 0;
        }
        Instant now = Instant.now(clock);
        List<Message> due = messages.findBySentAtIsNullAndDeliverAtLessThanEqual(now);
        int sent = 0;
        for (Message m : due) {
            try {
                String to = people.findById(m.getRecipientId()).map(Person::getEmail).orElse(null);
                String copy = m.getCopyToId() == null ? null
                        : people.findById(m.getCopyToId()).map(Person::getEmail).orElse(null);
                if (to == null) {
                    continue; // recipient was removed; leave it for a person to look at
                }
                out.send(to, copy, m.getSubject(), m.getBody());
                m.setSentAt(now);
                sent++;
            } catch (RuntimeException e) {
                log.warn("Could not send message {}; will retry: {}", m.getId(), e.getMessage());
            }
        }
        return sent;
    }
}
