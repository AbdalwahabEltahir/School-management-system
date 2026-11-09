package qa.sijill.sijill.issues.event;

import java.util.UUID;

/**
 * A port: "re-check this file now". The {@code issues} module needs a real re-check when somebody presses
 * "I've fixed it" or when an exception ends, but it must not depend on the {@code rules} module that
 * knows how to check. {@code rules} implements this interface ({@code RuleEngineService}); {@code issues}
 * only sees the interface. (Dependency inversion keeps the module graph free of cycles.)
 */
public interface FileRechecker {

    /**
     * Re-reads the file from the workspace (read-only) and re-evaluates every rule against it, opening,
     * updating or closing issues. Only this re-check ever closes an issue as fixed.
     */
    void recheck(UUID fileId);
}
