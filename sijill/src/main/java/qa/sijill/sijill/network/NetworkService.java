package qa.sijill.sijill.network;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.network.NetworkDtos.CreateItRequest;
import qa.sijill.sijill.network.NetworkDtos.ItAccount;
import qa.sijill.sijill.network.NetworkDtos.LogPage;
import qa.sijill.sijill.network.NetworkDtos.LogRow;
import qa.sijill.sijill.network.NetworkDtos.RoleCount;
import qa.sijill.sijill.network.NetworkDtos.School;
import qa.sijill.sijill.network.NetworkDtos.SchoolDetail;
import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * What EAA IT does across the network: see each school's head counts and principal, switch a school on or off, look after the
 * School IT account of each school, and search the activity log by who, when, school and kind of action. Only EAA IT may call it.
 * It stands apart from the other modules (it reads them, and nothing reads it).
 */
@Service
@RequiredArgsConstructor
public class NetworkService {

    private static final ZoneId QATAR = ZoneId.of("Asia/Qatar");
    private static final String STAFF = "('TEACHER','COORDINATOR','PRINCIPAL','PRINCIPAL_ASSISTANT','SCHOOL_IT','ATTENDANCE_OFFICER')";

    private final JdbcTemplate jdbc;
    private final SiteRepository sites;
    private final PersonRepository people;
    private final AuditPublisher audit;

    private static void requireEaa(CurrentUser user) {
        if (!user.isAny(Role.EAA_ADMIN)) {
            throw new ForbiddenException("Only EAA IT can do this.");
        }
    }

    // ------------------------------------------------------------------ schools

    private static final String SCHOOL_SQL = """
            SELECT s.id, s.name, s.domain, s.active, s.scored, s.head_office, s.first_check_at,
                   COUNT(p.id) FILTER (WHERE p.role = 'STUDENT' AND p.status <> 'LEFT') AS students,
                   COUNT(p.id) FILTER (WHERE p.role IN %s AND p.status <> 'LEFT') AS staff,
                   COUNT(p.id) FILTER (WHERE p.role = 'PARENT' AND p.status <> 'LEFT') AS parents,
                   COUNT(p.id) FILTER (WHERE p.role = 'SCHOOL_IT' AND p.status <> 'LEFT') AS it_accounts,
                   (SELECT x.display_name FROM people x WHERE x.site_id = s.id AND x.role = 'PRINCIPAL' AND x.status <> 'LEFT' ORDER BY x.created_at LIMIT 1) AS principal_name,
                   (SELECT x.email FROM people x WHERE x.site_id = s.id AND x.role = 'PRINCIPAL' AND x.status <> 'LEFT' ORDER BY x.created_at LIMIT 1) AS principal_email
            FROM sites s LEFT JOIN people p ON p.site_id = s.id
            WHERE s.org_id = ? %s
            GROUP BY s.id ORDER BY s.name""";

    private List<School> querySchools(UUID orgId, UUID siteId) {
        String sql = SCHOOL_SQL.formatted(STAFF, siteId == null ? "" : "AND s.id = ?");
        Object[] args = siteId == null ? new Object[] {orgId} : new Object[] {orgId, siteId};
        return jdbc.query(sql, (rs, i) -> new School(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("domain"), rs.getBoolean("active"),
                rs.getBoolean("scored"), rs.getBoolean("head_office"), rs.getInt("students"), rs.getInt("staff"), rs.getInt("parents"), rs.getInt("it_accounts"),
                rs.getString("principal_name"), rs.getString("principal_email"),
                rs.getTimestamp("first_check_at") == null ? null : rs.getTimestamp("first_check_at").toInstant()), args);
    }

    @Transactional(readOnly = true)
    public List<School> schools(CurrentUser user) {
        requireEaa(user);
        return querySchools(user.orgId(), null);
    }

    @Transactional(readOnly = true)
    public SchoolDetail school(CurrentUser user, UUID siteId) {
        requireEaa(user);
        School s = querySchools(user.orgId(), siteId).stream().findFirst().orElseThrow(() -> new NotFoundException("The school"));
        List<RoleCount> roles = jdbc.query("SELECT role, COUNT(*) AS n FROM people WHERE site_id = ? AND status <> 'LEFT' GROUP BY role ORDER BY role",
                (rs, i) -> new RoleCount(rs.getString("role"), rs.getInt("n")), siteId);
        return new SchoolDetail(s, roles);
    }

    @Transactional
    public SchoolDetail setSchoolActive(CurrentUser user, UUID siteId, boolean active) {
        requireEaa(user);
        Site site = sites.findById(siteId).filter(x -> x.getOrgId().equals(user.orgId())).orElseThrow(() -> new NotFoundException("The school"));
        if (site.isHeadOffice() && !active) {
            throw new ConflictException("The head office cannot be switched off.");
        }
        if (site.isActive() != active) {
            site.setActive(active);
            sites.saveAndFlush(site);
            audit.log(site.getOrgId(), site.getId(), ActivityActions.SITE_UPDATED).by(user).on("Site", site.getId())
                    .change(site.getName() + ": " + (active ? "inactive" : "active"), site.getName() + ": " + (active ? "active" : "inactive")).save();
        }
        return school(user, siteId);
    }

    // ------------------------------------------------------------------ School IT accounts

    @Transactional(readOnly = true)
    public List<ItAccount> itAccounts(CurrentUser user) {
        requireEaa(user);
        return jdbc.query("""
                SELECT p.id, p.display_name, p.email, p.status, p.site_id, s.name AS site_name, p.created_at
                FROM people p JOIN sites s ON s.id = p.site_id WHERE p.org_id = ? AND p.role = 'SCHOOL_IT' ORDER BY s.name, p.display_name""",
                (rs, i) -> new ItAccount(rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("email"), rs.getString("status"),
                        rs.getObject("site_id", UUID.class), rs.getString("site_name"), rs.getTimestamp("created_at").toInstant()), user.orgId());
    }

    @Transactional
    public ItAccount createIt(CurrentUser user, CreateItRequest req) {
        requireEaa(user);
        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new BadRequestException("Please give a valid e-mail address.");
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new BadRequestException("Please give the person's name.");
        }
        Site site = req.siteId() == null ? null : sites.findById(req.siteId()).filter(s -> s.getOrgId().equals(user.orgId())).orElse(null);
        if (site == null) {
            throw new BadRequestException("Choose the school this School IT account belongs to.");
        }
        String domain = email.substring(email.indexOf('@') + 1);
        if (sites.findByOrgIdOrderByName(user.orgId()).stream().noneMatch(s -> s.isActive() && s.getDomain().equalsIgnoreCase(domain))) {
            throw new BadRequestException("@" + domain + " does not belong to any school of the network, so this person could not sign in.");
        }
        if (people.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException(email + " already has an account.");
        }
        Person p = new Person();
        p.setOrgId(user.orgId());
        p.setSiteId(site.getId());
        p.setEmail(email);
        p.setDisplayName(req.name().trim());
        p.setRole(Role.SCHOOL_IT);
        p.setStatus(PersonStatus.ACTIVE);
        people.saveAndFlush(p);
        audit.log(p.getOrgId(), p.getSiteId(), ActivityActions.PERSON_ADDED).by(user).on("Person", p.getId()).change(null, email + " as SCHOOL_IT").save();
        return toIt(p, site.getName());
    }

    private Person itAccount(CurrentUser user, UUID id) {
        return people.findById(id).filter(p -> p.getOrgId().equals(user.orgId()) && p.getRole() == Role.SCHOOL_IT)
                .orElseThrow(() -> new NotFoundException("The School IT account"));
    }

    @Transactional
    public ItAccount setItStatus(CurrentUser user, UUID id, String status) {
        requireEaa(user);
        PersonStatus next;
        try {
            next = PersonStatus.valueOf(status == null ? "" : status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Choose active or suspended.");
        }
        if (next != PersonStatus.ACTIVE && next != PersonStatus.LEFT) {
            throw new BadRequestException("Choose active or suspended.");
        }
        Person p = itAccount(user, id);
        if (p.getStatus() != next) {
            String before = p.getRole() + "/" + p.getStatus();
            p.setStatus(next);
            p.setOverridden(true);
            people.save(p);
            audit.log(p.getOrgId(), p.getSiteId(), ActivityActions.PERSON_UPDATED).by(user).on("Person", p.getId()).change(before, p.getRole() + "/" + next).save();
        }
        return toIt(p, sites.findById(p.getSiteId()).map(Site::getName).orElse(""));
    }

    @Transactional
    public void deleteIt(CurrentUser user, UUID id) {
        requireEaa(user);
        Person p = itAccount(user, id);
        String summary = p.getEmail() + " (SCHOOL_IT)";
        try {
            people.delete(p);
            people.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(p.getDisplayName() + " already has records in Hafez, so the account cannot be deleted. Suspend it instead.");
        }
        audit.log(p.getOrgId(), p.getSiteId(), ActivityActions.PERSON_DELETED).by(user).on("Person", id).change(summary, null).save();
    }

    private static ItAccount toIt(Person p, String siteName) {
        return new ItAccount(p.getId(), p.getDisplayName(), p.getEmail(), p.getStatus().name(), p.getSiteId(), siteName, p.getCreatedAt());
    }

    // ------------------------------------------------------------------ the activity log

    /** The log with who did each thing (EAA IT: the whole network; School IT: their own school), filtered by dates, school, action, area and a search word. Dates are Qatar days, both included. */
    @Transactional(readOnly = true)
    public LogPage log(CurrentUser user, LocalDate from, LocalDate to, UUID siteId, String action, String area, String q, int page, int size) {
        if (user.isAny(Role.SCHOOL_IT)) {
            siteId = user.siteId();                       // School IT reads the same log, for their own school only
        } else {
            requireEaa(user);
        }
        String schoolExpr = "COALESCE(CASE WHEN l.entity_type = 'Site' THEN l.entity_id END, l.site_id)";
        StringBuilder where = new StringBuilder(" WHERE l.org_id = ?");
        List<Object> args = new ArrayList<>(List.of(user.orgId()));
        if (from != null) {
            where.append(" AND l.created_at >= ?");
            args.add(Timestamp.from(from.atStartOfDay(QATAR).toInstant()));
        }
        if (to != null) {
            where.append(" AND l.created_at < ?");
            args.add(Timestamp.from(to.plusDays(1).atStartOfDay(QATAR).toInstant()));
        }
        if (siteId != null) {
            where.append(" AND ").append(schoolExpr).append(" = ?");
            args.add(siteId);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND l.action = ?");
            args.add(action);
        }
        if (area != null && !area.isBlank()) {
            where.append(" AND l.entity_type = ?");
            args.add(area);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (a.display_name ILIKE ? OR l.action ILIKE ? OR COALESCE(l.before_state,'') ILIKE ? OR COALESCE(l.after_state,'') ILIKE ? OR COALESCE(t.display_name,ts.name,'') ILIKE ?)");
            String like = "%" + q.trim().replace("%", "").replace("_", "") + "%";
            for (int i = 0; i < 5; i++) {
                args.add(like);
            }
        }
        String joins = " FROM activity_log l LEFT JOIN people a ON a.id = l.actor_id LEFT JOIN people t ON l.entity_type = 'Person' AND t.id = l.entity_id"
                + " LEFT JOIN sites ts ON l.entity_type = 'Site' AND ts.id = l.entity_id LEFT JOIN sites sc ON sc.id = " + schoolExpr;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + joins + where, Long.class, args.toArray());
        int limit = Math.min(Math.max(size, 1), 200), offset = Math.max(page, 0) * limit;
        List<Object> rowArgs = new ArrayList<>(args);
        rowArgs.add(limit);
        rowArgs.add(offset);
        List<LogRow> rows = jdbc.query("SELECT l.seq, l.created_at, l.actor_id, a.display_name AS actor_name, a.role AS actor_role, l.action, l.entity_type, l.entity_id,"
                + " COALESCE(t.display_name, ts.name) AS target_name, sc.id AS school_id, sc.name AS school_name, l.before_state, l.after_state"
                + joins + where + " ORDER BY l.seq DESC LIMIT ? OFFSET ?",
                (rs, i) -> new LogRow(rs.getLong("seq"), rs.getTimestamp("created_at").toInstant(), rs.getObject("actor_id", UUID.class), rs.getString("actor_name"),
                        rs.getString("actor_role"), rs.getString("action"), rs.getString("entity_type"), rs.getObject("entity_id", UUID.class),
                        rs.getString("target_name"), rs.getObject("school_id", UUID.class), rs.getString("school_name"), rs.getString("before_state"), rs.getString("after_state")),
                rowArgs.toArray());
        List<String> actions = jdbc.queryForList("SELECT DISTINCT action FROM activity_log WHERE org_id = ? ORDER BY action", String.class, user.orgId());
        List<String> areas = jdbc.queryForList("SELECT DISTINCT entity_type FROM activity_log WHERE org_id = ? ORDER BY entity_type", String.class, user.orgId());
        return new LogPage(total == null ? 0 : total, rows, actions, areas);
    }
}
