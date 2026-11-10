package qa.sijill.sijill.setup.dev;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.shared.model.Role;

/**
 * <b>Development only</b> ({@code dev} profile): sign in as a role with one click, no password.
 *
 * <pre>
 *   /dev/login               a page with one button per role
 *   /dev/login/it            sign in as the IT admin, then go to /api/me
 *   /dev/login/teacher?next=/api/issues
 * </pre>
 * The bean does not exist in any other profile, so in production these URLs simply return 404. Real
 * sign-in is OpenID Connect against the school workspace (see {@code SecurityConfig}).
 */
@RestController
@Profile("dev")
@RequestMapping("/dev")
@RequiredArgsConstructor
public class DevLoginController {

    /** URL name of each role → the role it signs in as. */
    private static final Map<String, Role> ROLES = Map.of(
            "leadership", Role.LEADERSHIP, "it", Role.SCHOOL_IT, "principal", Role.PRINCIPAL,
            "coordinator", Role.COORDINATOR, "teacher", Role.TEACHER, "student", Role.STUDENT);

    private final PersonRepository people;

    /** A tiny page with one link per role, showing who each one signs in as. */
    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String page() {
        StringBuilder html = new StringBuilder("""
                <!doctype html><meta charset="utf-8"><title>Dev sign-in</title>
                <body style="font:16px system-ui;max-width:520px;margin:48px auto;padding:0 16px">
                <h1>Sijill: development sign-in</h1>
                <p>Pick a role. No password. Only available in the <code>dev</code> profile.</p><ul style="line-height:2.2">
                """);
        for (String key : List.of("leadership", "it", "principal", "coordinator", "teacher", "student")) {
            String who = people.findAll().stream().filter(p -> p.getRole() == ROLES.get(key)).findFirst()
                    .map(p -> p.getDisplayName() + " (" + p.getEmail() + ")").orElse("nobody yet");
            html.append("<li><a href=\"/dev/login/").append(key).append("\"><b>").append(key).append("</b></a> — ")
                    .append(who.replace("<", "&lt;")).append("</li>");
        }
        return html.append("</ul></body>").toString();
    }

    /** Signs in as the first person with the given role and redirects (default: {@code /api/me}). */
    @GetMapping("/login/{role}")
    public void login(@PathVariable String role, @RequestParam(defaultValue = "/api/me") String next,
                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        Role wanted = ROLES.get(role.toLowerCase(Locale.ROOT));
        if (wanted == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown role. Use: " + ROLES.keySet());
        }
        Person person = people.findAll().stream().filter(p -> p.getRole() == wanted && p.canSignIn()).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No " + role + " exists yet"));

        // The sign-in is the person's school e-mail, exactly what OpenID Connect would give us.
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                person.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + wanted.name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);

        // only local paths, so this cannot be used to bounce people to another site
        response.sendRedirect(next.startsWith("/") && !next.startsWith("//") ? next : "/api/me");
    }
}
