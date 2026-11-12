package qa.sijill.sijill.people.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.people.model.Person;
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
 * Invite-only onboarding. There is no public registration: the school adds the student, then issues a one-time code
 * that lets a parent create an account linked to one or several children (or lets a student set a password). A code
 * works once, expires after 14 days and is stored only as a hash, so it is shown to the school a single time.
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

    private static final Duration VALID_FOR = Duration.ofDays(14);
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String BAD_CODE = "That invitation code is not valid. Ask the school for a new one.";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Issued(UUID id, String code, Instant expiresAt) {}

    public record InvitationView(UUID id, String kind, String email, List<String> students, String status,
                                 Instant createdAt, Instant expiresAt) {}

    private final JdbcTemplate jdbc;
    private final PersonRepository people;
    private final SiteRepository sites;
    private final ParentLinks links;
    private final AccountService accounts;
    private final AttemptLimiter limiter;
    private final AuditPublisher audit;
    private final AccessPolicy policy;
    private final Clock clock;

    /** The school issues a code: PARENT for one or more of its students, or STUDENT to let a student set a password. */
    @Transactional
    public Issued issue(CurrentUser user, String kind, List<UUID> studentIds, String email) {
        policy.requireRole(user, Role.SCHOOL_IT, Role.COORDINATOR);
        if (!"PARENT".equals(kind) && !"STUDENT".equals(kind)) {
            throw new BadRequestException("The invitation is for a parent or a student.");
        }
        if (studentIds == null || studentIds.isEmpty() || ("STUDENT".equals(kind) && studentIds.size() != 1)) {
            throw new BadRequestException("STUDENT".equals(kind) ? "Choose the student." : "Choose at least one student.");
        }
        List<Person> students = people.findAllById(studentIds);
        if (students.size() != studentIds.stream().distinct().count() || students.stream()
                .anyMatch(s -> s.getRole() != Role.STUDENT || !s.getSiteId().equals(user.siteId()))) {
            throw new BadRequestException("Every student must belong to your school.");
        }
        String cleanEmail = email == null || email.isBlank() ? null : email.trim().toLowerCase();
        if (cleanEmail != null && !cleanEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new BadRequestException("Please give a valid e-mail address.");
        }
        String code = newCode();
        UUID id = UUID.randomUUID();
        Instant expires = Instant.now(clock).plus(VALID_FOR);
        jdbc.update("INSERT INTO invitations (id, org_id, site_id, kind, code_hash, email, created_by, expires_at) VALUES (?,?,?,?,?,?,?,?)",
                id, user.orgId(), user.siteId(), kind, hash(code), cleanEmail, user.personId(), Timestamp.from(expires));
        for (UUID s : studentIds.stream().distinct().toList()) {
            jdbc.update("INSERT INTO invitation_students (invitation_id, student_id) VALUES (?,?)", id, s);
        }
        audit.log(user.orgId(), user.siteId(), "INVITATION_ISSUED").by(user).on("Invitation", id)
                .change(null, kind + " for " + students.size() + " student(s)").save();
        return new Issued(id, code, expires);
    }

    @Transactional(readOnly = true)
    public List<InvitationView> list(CurrentUser user) {
        policy.requireRole(user, Role.SCHOOL_IT, Role.COORDINATOR);
        Instant now = Instant.now(clock);
        return jdbc.queryForList("SELECT id, kind, email, created_at, expires_at, used_at, revoked FROM invitations WHERE site_id = ? ORDER BY created_at DESC LIMIT 100",
                user.siteId()).stream().map(r -> {
            UUID id = (UUID) r.get("id");
            List<String> names = jdbc.queryForList("SELECT p.display_name FROM invitation_students i JOIN people p ON p.id = i.student_id WHERE i.invitation_id = ? ORDER BY p.display_name",
                    String.class, id);
            Instant expires = ((Timestamp) r.get("expires_at")).toInstant();
            String status = r.get("used_at") != null ? "USED" : (Boolean) r.get("revoked") ? "REVOKED" : expires.isBefore(now) ? "EXPIRED" : "PENDING";
            return new InvitationView(id, (String) r.get("kind"), (String) r.get("email"), names, status,
                    ((Timestamp) r.get("created_at")).toInstant(), expires);
        }).toList();
    }

    @Transactional
    public void revoke(CurrentUser user, UUID id) {
        policy.requireRole(user, Role.SCHOOL_IT, Role.COORDINATOR);
        int n = jdbc.update("UPDATE invitations SET revoked = TRUE WHERE id = ? AND site_id = ? AND used_at IS NULL", id, user.siteId());
        if (n == 0) {
            throw new NotFoundException("The invitation");
        }
        audit.log(user.orgId(), user.siteId(), "INVITATION_REVOKED").by(user).on("Invitation", id).save();
    }

    /** A parent without an account redeems a code: creates the account and links every child on the invitation. */
    @Transactional
    public Person redeemNewParent(String code, String name, String email, String password, String ip) {
        Map<String, Object> inv = open(code, "PARENT", ip);
        String clean = email == null ? "" : email.trim().toLowerCase();
        if (inv.get("email") != null && !inv.get("email").toString().equalsIgnoreCase(clean)) {
            throw new BadRequestException("This invitation was issued for a different e-mail address.");
        }
        Site site = sites.findById((UUID) inv.get("site_id")).orElseThrow(() -> new BadRequestException(BAD_CODE));
        Person parent = accounts.createAccount(site, Role.PARENT, name, email, password);
        linkAll(parent.getId(), (UUID) inv.get("id"));
        consume((UUID) inv.get("id"), parent.getId());
        audit.log(parent.getOrgId(), parent.getSiteId(), ActivityActions.PERSON_ADDED).by(parent.getId()).on("Person", parent.getId())
                .change(null, parent.getEmail() + " as PARENT (invitation)").save();
        return parent;
    }

    /** A signed-in parent redeems another code to follow more children. */
    @Transactional
    public void redeemForParent(CurrentUser user, String code, String ip) {
        policy.requireRole(user, Role.PARENT);
        Map<String, Object> inv = open(code, "PARENT", ip);
        if (!inv.get("site_id").equals(user.siteId())) {
            throw new BadRequestException(BAD_CODE);
        }
        linkAll(user.personId(), (UUID) inv.get("id"));
        consume((UUID) inv.get("id"), user.personId());
        audit.log(user.orgId(), user.siteId(), ActivityActions.PERSON_UPDATED).by(user).on("Person", user.personId())
                .change(null, "linked more children (invitation)").save();
    }

    /** A student whose account the school created sets a password with a STUDENT code. */
    @Transactional
    public Person redeemStudent(String code, String password, String ip) {
        Map<String, Object> inv = open(code, "STUDENT", ip);
        UUID studentId = jdbc.queryForObject("SELECT student_id FROM invitation_students WHERE invitation_id = ?", UUID.class, inv.get("id"));
        Person student = people.findById(studentId).filter(p -> p.getRole() == Role.STUDENT && p.canSignIn())
                .orElseThrow(() -> new BadRequestException(BAD_CODE));
        accounts.setPassword(student, password);
        consume((UUID) inv.get("id"), student.getId());
        audit.log(student.getOrgId(), student.getSiteId(), ActivityActions.PERSON_UPDATED).by(student.getId()).on("Person", student.getId())
                .change(null, "student account activated (invitation)").save();
        return student;
    }

    private void linkAll(UUID parentId, UUID invitationId) {
        for (UUID studentId : jdbc.queryForList("SELECT student_id FROM invitation_students WHERE invitation_id = ?", UUID.class, invitationId)) {
            links.link(parentId, studentId);
        }
    }

    private void consume(UUID id, UUID by) {
        jdbc.update("UPDATE invitations SET used_at = ?, used_by = ? WHERE id = ?", Timestamp.from(Instant.now(clock)), by, id);
    }

    /** Finds a code that is unused, not revoked and not expired. Every failure gives the same message. */
    private Map<String, Object> open(String code, String kind, String ip) {
        limiter.hit("invite:" + ip, 10, WINDOW);
        String clean = code == null ? "" : code.trim().toUpperCase().replace(" ", "");
        var rows = jdbc.queryForList("SELECT id, site_id, email, expires_at, used_at, revoked FROM invitations WHERE code_hash = ? AND kind = ?", hash(clean), kind);
        if (rows.isEmpty()) {
            throw new BadRequestException(BAD_CODE);
        }
        var r = rows.get(0);
        if (r.get("used_at") != null || (Boolean) r.get("revoked") || ((Timestamp) r.get("expires_at")).toInstant().isBefore(Instant.now(clock))) {
            throw new BadRequestException(BAD_CODE);
        }
        return r;
    }

    private static String newCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String hash(String code) {
        try {
            String normal = code.toUpperCase().replace(" ", "");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normal.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
