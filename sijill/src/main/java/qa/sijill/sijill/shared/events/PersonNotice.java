package qa.sijill.sijill.shared.events;

import java.util.UUID;

import qa.sijill.sijill.shared.model.NotificationKind;

/**
 * "Tell this person something" — a plain in-app notice. Published by modules that only want to notify
 * someone (planning review, welcome message) without depending on the notification module; the
 * {@code notifications} module turns it into a bell entry kept in both languages. {@code bodyAr} is optional:
 * when it is null the text is shown as written (often a name or a date).
 */
public record PersonNotice(UUID personId, NotificationKind kind, String titleAr, String titleEn, String body, String bodyAr) {

    public PersonNotice(UUID personId, NotificationKind kind, String titleAr, String titleEn, String body) {
        this(personId, kind, titleAr, titleEn, body, null);
    }
}
