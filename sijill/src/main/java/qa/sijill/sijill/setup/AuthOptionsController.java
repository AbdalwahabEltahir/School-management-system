package qa.sijill.sijill.setup;

import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public: tells the welcome page whether the one-click test sign-in (dev profile only) exists. */
@RestController
public class AuthOptionsController {

    private final boolean dev;

    AuthOptionsController(Environment env) {
        this.dev = env.acceptsProfiles(Profiles.of("dev"));
    }

    @GetMapping("/api/auth/options")
    public Map<String, Object> options() {
        return Map.of("dev", dev);
    }
}
