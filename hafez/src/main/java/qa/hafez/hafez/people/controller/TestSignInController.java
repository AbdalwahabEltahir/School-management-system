package qa.hafez.hafez.people.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.people.repository.PersonRepository;
import qa.hafez.hafez.shared.exception.ConflictException;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.exception.UnauthenticatedException;

/**
 * <b>Testing only.</b> Lets someone try the system by typing a person's name (for example "Huda") or picking them
 * from a list, and signs them in with that person's role. There is no password and no Google check, so anybody who
 * can open the page can become any person. It exists only when {@code hafez.signin.test-mode} is true (off by
 * default); otherwise every call answers 404.
 */
@RestController
@RequestMapping("/api/auth/test")
public class TestSignInController {

    public record NameOnly(@NotBlank String name) {}

    public record PersonRef(UUID id, String name, String role) {}

    public record PickRequest(UUID id) {}

    /** The only accounts offered by test sign-in: a name (spaces ignored, matched from the start of the full name) and its role. */
    private static final Map<String, Role> ALLOWED = Map.of(
            "abdalwahab", Role.SCHOOL_IT, "huda", Role.PRINCIPAL, "noora", Role.LEADERSHIP, "nora", Role.LEADERSHIP, "rajaa", Role.COORDINATOR,
            "teacher1", Role.TEACHER, "teacher2", Role.TEACHER, "student", Role.STUDENT, "parent", Role.PARENT);

    private static boolean offered(Person p) {
        if (!p.canSignIn()) {
            return false;
        }
        String name = p.getDisplayName().toLowerCase(Locale.ROOT).replace(" ", "");
        return ALLOWED.entrySet().stream().anyMatch(e -> name.startsWith(e.getKey()) && p.getRole() == e.getValue());
    }

    private final PersonRepository people;
    private final boolean testMode;

    TestSignInController(PersonRepository people, @Value("${hafez.signin.test-mode:false}") boolean testMode) {
        this.people = people;
        this.testMode = testMode;
    }

    /** Everyone who can sign in, for the "pick a person" list. */
    @GetMapping("/people")
    public List<PersonRef> list() {
        requireTestMode();
        return people.findAll().stream().filter(TestSignInController::offered)
                .sorted(Comparator.comparing(Person::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> new PersonRef(p.getId(), p.getDisplayName(), p.getRole().name())).toList();
    }

    /** Signs in as the one person whose full name or first name matches what was typed. */
    @PostMapping("/signin")
    public Map<String, Object> byName(@Valid @RequestBody NameOnly body, HttpServletRequest request, HttpServletResponse response) {
        requireTestMode();
        String typed = body.name().trim().toLowerCase(Locale.ROOT);
        List<Person> matches = people.findAll().stream().filter(TestSignInController::offered)
                .filter(p -> {
                    String full = p.getDisplayName().trim().toLowerCase(Locale.ROOT);
                    return full.equals(typed) || full.split("\\s+")[0].equals(typed);
                }).toList();
        if (matches.isEmpty()) {
            throw new UnauthenticatedException("No one is called \"" + body.name().trim() + "\". Check the spelling or pick a person from the list.");
        }
        if (matches.size() > 1) {
            throw new ConflictException("More than one person is called \"" + body.name().trim() + "\". Pick the one you want from the list below.");
        }
        return signIn(matches.get(0), request, response);
    }

    /** Signs in as the person picked from the list (also handles two people with the same name). */
    @PostMapping("/pick")
    public Map<String, Object> pick(@RequestBody PickRequest body, HttpServletRequest request, HttpServletResponse response) {
        requireTestMode();
        Person p = people.findById(body.id()).filter(TestSignInController::offered)
                .orElseThrow(() -> new UnauthenticatedException("That person cannot sign in."));
        return signIn(p, request, response);
    }

    private void requireTestMode() {
        if (!testMode) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private static Map<String, Object> signIn(Person p, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId(); // a fresh session id at sign-in
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                p.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + p.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        return Map.of("role", p.getRole().name());
    }
}
