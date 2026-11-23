package qa.sijill.sijill.askhafez;

import java.util.List;
import java.util.UUID;

/** Request and response shapes of "Ask Hafez". */
public final class AskHafezDtos {

    private AskHafezDtos() {
    }

    /** A Hafez page an answer came from. {@code href} is a route inside the app, such as {@code #/homework}. */
    public record Link(String label, String href) {}

    /** One answer: the text, the pages it came from, and a few follow-up questions to offer. */
    public record Reply(UUID id, String answer, List<Link> links, List<String> suggestions, String language) {}

    /** A parent's switch for one child. */
    public record ChildSetting(UUID id, String name, boolean enabled) {}

    /** Whether the button shows for this person, and (for a parent) the switch for each child. */
    public record Status(boolean available, boolean offByParent, List<ChildSetting> children) {}

    public record AskRequest(String question) {}

    public record RatingRequest(Boolean up) {}

    public record SwitchRequest(Boolean enabled) {}
}
