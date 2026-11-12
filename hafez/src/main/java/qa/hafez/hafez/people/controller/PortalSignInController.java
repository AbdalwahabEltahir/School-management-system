package qa.hafez.hafez.people.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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

import qa.hafez.hafez.people.dto.AuthDtos;
import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.people.service.AccountService;

/**
 * Public: students and parents register and sign in with an e-mail and a password. A successful call starts the
 * session, exactly as a Google sign-in would, so the rest of the API treats them the same way.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PortalSignInController {

    private final AccountService accounts;

    @GetMapping("/schools")
    public List<AuthDtos.School> schools() {
        return accounts.schools().stream().map(s -> new AuthDtos.School(s.getId(), s.getName())).toList();
    }

    @PostMapping("/register/student")
    public Map<String, Object> registerStudent(@Valid @RequestBody AuthDtos.StudentRegistration r,
                                               HttpServletRequest request, HttpServletResponse response) {
        Person p = accounts.registerStudent(r.siteId(), r.name(), r.email(), r.password());
        signIn(p, request, response);
        return Map.of("role", p.getRole().name(), "studentCode", p.getStudentCode());
    }

    @PostMapping("/register/parent")
    public Map<String, Object> registerParent(@Valid @RequestBody AuthDtos.ParentRegistration r,
                                              HttpServletRequest request, HttpServletResponse response) {
        Person p = accounts.registerParent(r.name(), r.email(), r.password(), r.studentCode(), request.getRemoteAddr());
        signIn(p, request, response);
        return Map.of("role", p.getRole().name());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody AuthDtos.LoginRequest r,
                                     HttpServletRequest request, HttpServletResponse response) {
        Person p = accounts.login(r.email(), r.password(), request.getRemoteAddr());
        signIn(p, request, response);
        return Map.of("role", p.getRole().name());
    }

    private static void signIn(Person p, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId(); // a fresh session id at sign-in
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                p.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + p.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    }
}
