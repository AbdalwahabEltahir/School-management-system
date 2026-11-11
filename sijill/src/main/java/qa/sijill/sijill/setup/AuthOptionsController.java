package qa.sijill.sijill.setup;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public: tells the welcome page which test sign-in options exist (both are off unless switched on). */
@RestController
public class AuthOptionsController {

    private final boolean dev;
    private final boolean testSignin;

    AuthOptionsController(Environment env, @Value("${sijill.signin.test-mode:false}") boolean testSignin) {
        this.dev = env.acceptsProfiles(Profiles.of("dev"));
        this.testSignin = testSignin;
    }

    @GetMapping("/api/auth/options")
    public Map<String, Object> options() {
        return Map.of("dev", dev, "testSignin", testSignin);
    }
}
