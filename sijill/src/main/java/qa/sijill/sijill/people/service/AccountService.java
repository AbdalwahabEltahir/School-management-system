package qa.sijill.sijill.people.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.AccessPolicy;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Students and parents have their own e-mail and password (staff keep signing in with Google). A student registers at
 * their school and is given a student ID. A parent registers with their own e-mail and password plus the student ID,
 * and follows that student: several parents can use one ID, each with their own e-mail and password.
 *
 * <p>Passwords are stored only as BCrypt hashes. Five wrong passwords lock the account for 15 minutes, and tries per
 * address are limited, so neither a password nor a student ID can be guessed quickly.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final int MAX_WRONG = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final String WRONG = "Wrong e-mail or password.";
    private static final String NO_STUDENT = "That student ID was not found. Please check it with your child.";

    private final PersonRepository people;
    private final SiteRepository sites;
    private final ParentLinks links;
    private final JdbcTemplate jdbc;
    private final AuditPublisher audit;
    private final AttemptLimiter limiter;
    private final AccessPolicy policy;
    private final Clock clock;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    /** Creates a parent or student account. Used only by {@link InvitationService}: there is no public registration. */
    @Transactional
    public Person createAccount(Site site, Role role, String name, String email, String password) {
        return create(site, role, name, email, password);
    }

    /** Sets (or resets) the password of an account the school provisioned. */
    @Transactional
    public void setPassword(Person person, String password) {
        checkPassword(password);
        jdbc.update("DELETE FROM credentials WHERE person_id = ?", person.getId());
        jdbc.update("INSERT INTO credentials (person_id, password_hash) VALUES (?, ?)", person.getId(), encoder.encode(password));
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < 8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("The password must be at least 8 characters.");
        }
    }

    /** Checks the e-mail and password of a student or a parent. Every failure gives the same message. */
    @Transactional(noRollbackFor = {BadRequestException.class, ConflictException.class})
    public Person login(String email, String password, String ip) {
        limiter.hit("login:" + ip, 30, WINDOW);
        String clean = email == null ? "" : email.trim().toLowerCase();
        var rows = jdbc.queryForList("""
                SELECT c.person_id, c.password_hash, c.failed_attempts, c.locked_until
                FROM credentials c JOIN people p ON p.id = c.person_id WHERE lower(p.email) = ?""", clean);
        if (rows.isEmpty() || password == null) {
            encoder.matches("x", "$2a$10$7EqJtq98hPqEX7fNZaFWoOa3sWk0qO9d7k8u0g3S1cP9Zr2b9Qy5e"); // same time as a real check
            throw new BadRequestException(WRONG);
        }
        var row = rows.get(0);
        UUID personId = (UUID) row.get("person_id");
        Timestamp lockedUntil = (Timestamp) row.get("locked_until");
        Instant now = Instant.now(clock);
        if (lockedUntil != null && lockedUntil.toInstant().isAfter(now)) {
            throw new ConflictException("Too many wrong passwords. Please try again in 15 minutes.");
        }
        if (!encoder.matches(password, (String) row.get("password_hash"))) {
            int failed = ((Number) row.get("failed_attempts")).intValue() + 1;
            if (failed >= MAX_WRONG) {
                jdbc.update("UPDATE credentials SET failed_attempts = 0, locked_until = ? WHERE person_id = ?", Timestamp.from(now.plus(LOCK)), personId);
            } else {
                jdbc.update("UPDATE credentials SET failed_attempts = ? WHERE person_id = ?", failed, personId);
            }
            throw new BadRequestException(WRONG);
        }
        Person person = people.findById(personId).filter(Person::canSignIn)
                .filter(p -> p.getRole() == Role.STUDENT || p.getRole() == Role.PARENT)
                .orElseThrow(() -> new BadRequestException(WRONG));
        jdbc.update("UPDATE credentials SET failed_attempts = 0, locked_until = NULL WHERE person_id = ?", personId);
        return person;
    }

    private Person findStudent(String studentCode, String ip) {
        limiter.hit("code:" + ip, 10, WINDOW);
        String code = studentCode == null ? "" : studentCode.trim().toUpperCase();
        return people.findByStudentCode(code).filter(s -> s.getRole() == Role.STUDENT && s.canSignIn())
                .orElseThrow(() -> new BadRequestException(NO_STUDENT));
    }

    private Person create(Site site, Role role, String name, String email, String password) {
        String clean = email == null ? "" : email.trim().toLowerCase();
        if (!clean.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new BadRequestException("Please give a valid e-mail address.");
        }
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new BadRequestException("Please give your full name.");
        }
        checkPassword(password);
        if (people.findByEmailIgnoreCase(clean).isPresent()) {
            throw new ConflictException("That e-mail already has an account. Sign in, or ask the school office.");
        }
        try {
            Person p = new Person();
            p.setOrgId(site.getOrgId());
            p.setSiteId(site.getId());
            p.setEmail(clean);
            p.setDisplayName(name.trim());
            p.setRole(role);
            p.setStatus(PersonStatus.ACTIVE);
            people.saveAndFlush(p);
            jdbc.update("INSERT INTO credentials (person_id, password_hash) VALUES (?, ?)", p.getId(), encoder.encode(password));
            return p;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("That e-mail already has an account. Sign in, or ask the school office.");
        }
    }
}
