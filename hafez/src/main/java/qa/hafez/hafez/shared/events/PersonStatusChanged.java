package qa.hafez.hafez.shared.events;

import java.util.UUID;

/**
 * A person's status changed to {@code newStatus} ("LEAVING" or "LEFT", NEW, ACTIVE). Published by the
 * {@code people} module; other modules react without {@code people} knowing about them:
 * <ul>
 *   <li>{@code rules}: re-checks the person's files (a leaver raises rule R8);</li>
 *   <li>{@code issues}: when the person has LEFT, moves their open issues to the coordinator.</li>
 * </ul>
 * The status is carried as text so this event does not depend on the people module's enum.
 */
public record PersonStatusChanged(UUID personId, String email, String newStatus) {

    public boolean isLeaving() {
        return "LEAVING".equals(newStatus);
    }

    public boolean hasLeft() {
        return "LEFT".equals(newStatus);
    }
}
