package qa.sijill.sijill.files.event;

import java.util.UUID;

/** Domain events published by the {@code files} module. */
public final class FileEvents {

    private FileEvents() {}

    /**
     * A coordinator classified a file (answering "Needs review"). The {@code rules} module listens and
     * re-checks the file at once, so issues that were waiting for the answer move on or close.
     */
    public record Classified(UUID fileId) {}
}
