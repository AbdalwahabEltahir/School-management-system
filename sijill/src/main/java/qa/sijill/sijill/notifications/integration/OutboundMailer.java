package qa.sijill.sijill.notifications.integration;

/**
 * Sends a message by e-mail from the dedicated sender account (proposal 5.4). Kept behind an interface
 * so the mail provider can be chosen later. Messages are always stored in the {@code messages} table
 * first, so every message is logged whether or not a mailer is configured.
 */
public interface OutboundMailer {

    /** @param copyTo may be null; the coordinator copied on urgent issues */
    void send(String to, String copyTo, String subject, String body);
}
