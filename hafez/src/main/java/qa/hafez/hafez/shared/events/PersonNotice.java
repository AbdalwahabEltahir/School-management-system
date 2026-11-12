package qa.hafez.hafez.shared.events;

import java.util.UUID;

import qa.hafez.hafez.shared.model.NotificationKind;

/**
 * "Tell this person something" — a plain in-app notice. Published by modules that only want to notify
 * someone (planning review, welcome message) without depending on the notification module; the
 * {@code notifications} module turns it into a bell entry in the person's language.
 */
public record PersonNotice(UUID personId, NotificationKind kind, String titleAr, String titleEn, String body) {
}
