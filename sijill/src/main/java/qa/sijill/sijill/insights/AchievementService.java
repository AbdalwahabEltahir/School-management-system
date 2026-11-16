package qa.sijill.sijill.insights;

import java.net.URI;
import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.insights.InsightsDtos.AchievementRequest;
import qa.sijill.sijill.insights.InsightsDtos.AchievementView;
import qa.sijill.sijill.insights.InsightsDtos.CategoryCount;
import qa.sijill.sijill.insights.InsightsDtos.CategoryView;
import qa.sijill.sijill.shared.events.PersonNotice;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * School achievements. Each school fills in the same template, one entry per achievement against EAA's success
 * measures; each kind of achievement is worth a set number of recognition points for the school. The principal and
 * coordinators record them; Leadership sees every school's and a summary across the network.
 */
@Service
@RequiredArgsConstructor
public class AchievementService {

    /** The template: the kinds of achievement EAA recognises, and what each is worth. */
    public static final List<CategoryView> CATEGORIES = List.of(
            new CategoryView("FACILITIES", "Facilities and innovation", 20, "A new learning space or technology, for example a VR room or a science lab."),
            new CategoryView("ACADEMIC", "Academic results", 15, "Results that improved, a subject that outperformed the target."),
            new CategoryView("COMPETITION", "Student competitions and awards", 15, "Prizes and places students won at school, national or international level."),
            new CategoryView("COMMUNITY", "Community and partnerships", 10, "Work with parents, the community, universities or companies."),
            new CategoryView("STAFF", "Staff development", 10, "Training, certification or recognition of teachers and staff."),
            new CategoryView("WELLBEING", "Wellbeing and inclusion", 10, "Programmes for student wellbeing, attendance or inclusion."),
            new CategoryView("OTHER", "Other", 5, "Anything else worth recognising."));

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    static CategoryView category(String key) {
        return CATEGORIES.stream().filter(c -> c.key().equals(key)).findFirst().orElse(CATEGORIES.get(CATEGORIES.size() - 1));
    }

    @Transactional
    public AchievementView add(CurrentUser user, AchievementRequest r) {
        if (!user.isAny(Role.PRINCIPAL, Role.PRINCIPAL_ASSISTANT)) {
            throw new ForbiddenException("The principal or the principal assistant records the school's achievements.");
        }
        CategoryView c = CATEGORIES.stream().filter(x -> x.key().equals(r.category())).findFirst()
                .orElseThrow(() -> new BadRequestException("Choose what kind of achievement it is."));
        String title = r.title().trim();
        if (title.length() > 200) {
            throw new BadRequestException("Please keep the title under 200 characters.");
        }
        String link = r.evidenceLink() == null || r.evidenceLink().isBlank() ? null : r.evidenceLink().trim();
        if (link != null && !isHttps(link)) {
            throw new BadRequestException("The evidence link must start with https://");
        }
        if (r.achievedOn().isAfter(java.time.LocalDate.now().plusDays(1))) {
            throw new BadRequestException("The date cannot be in the future.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO school_achievements (id, org_id, site_id, category, title, description, impact, achieved_on, evidence_link, points, created_by) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)", id, user.orgId(), user.siteId(), c.key(), title, cut(r.description(), 4000),
                cut(r.impact(), 1000), Date.valueOf(r.achievedOn()), link, c.points(), user.personId());
        String school = jdbc.queryForList("SELECT name FROM sites WHERE id = ?", String.class, user.siteId()).stream().findFirst().orElse("");
        for (UUID leader : jdbc.queryForList("SELECT id FROM people WHERE org_id = ? AND role = 'LEADERSHIP' AND status <> 'LEFT'", UUID.class, user.orgId())) {
            events.publishEvent(new PersonNotice(leader, NotificationKind.SYSTEM, "إنجاز جديد: " + school,
                    "New achievement: " + school, title + " (" + c.label() + ")"));
        }
        return list("WHERE a.id = ?", id).get(0);
    }

    /** One school's achievements: the principal and staff of the school see their own; Leadership any school's. */
    @Transactional(readOnly = true)
    public List<AchievementView> forSchool(CurrentUser user, UUID siteId) {
        UUID site = siteId == null ? user.siteId() : siteId;
        requireView(user, site);
        return list("WHERE a.site_id = ?", site);
    }

    /** Every school's, for Leadership. */
    @Transactional(readOnly = true)
    public List<AchievementView> all(CurrentUser user) {
        if (!user.is(Role.LEADERSHIP)) {
            throw new ForbiddenException("The summary across schools is for Leadership.");
        }
        return list("WHERE a.org_id = ?", user.orgId());
    }

    @Transactional
    public void remove(CurrentUser user, UUID id) {
        if (!user.is(Role.PRINCIPAL)) {
            throw new ForbiddenException("Only the principal can remove an achievement.");
        }
        int n = jdbc.update("DELETE FROM school_achievements WHERE id = ? AND site_id = ?", id, user.siteId());
        if (n == 0) {
            throw new NotFoundException("The achievement");
        }
    }

    void requireView(CurrentUser user, UUID siteId) {
        boolean ok = user.is(Role.LEADERSHIP)
                || (user.isAny(Role.PRINCIPAL, Role.PRINCIPAL_ASSISTANT, Role.COORDINATOR, Role.TEACHER, Role.SCHOOL_IT) && siteId.equals(user.siteId()));
        if (!ok) {
            throw new ForbiddenException("You can see your own school's achievements.");
        }
    }

    static List<CategoryCount> countByCategory(List<AchievementView> list) {
        Map<String, int[]> m = new LinkedHashMap<>();
        CATEGORIES.forEach(c -> m.put(c.key(), new int[2]));
        list.forEach(a -> { int[] v = m.get(a.category()); if (v != null) { v[0]++; v[1] += a.points(); } });
        List<CategoryCount> out = new ArrayList<>();
        CATEGORIES.forEach(c -> out.add(new CategoryCount(c.key(), c.label(), m.get(c.key())[0], m.get(c.key())[1])));
        return out;
    }

    List<AchievementView> list(String where, Object... args) {
        return jdbc.query("SELECT a.id, a.site_id, s.name, a.category, a.title, a.description, a.impact, a.achieved_on, a.evidence_link, a.points, "
                        + "p.display_name, a.created_at FROM school_achievements a JOIN sites s ON s.id = a.site_id LEFT JOIN people p ON p.id = a.created_by "
                        + where + " ORDER BY a.achieved_on DESC, a.created_at DESC",
                (rs, i) -> new AchievementView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                        category(rs.getString(4)).label(), rs.getString(5), rs.getString(6), rs.getString(7), rs.getDate(8).toLocalDate(),
                        rs.getString(9), rs.getInt(10), rs.getString(11), rs.getTimestamp(12).toInstant()), args);
    }

    private static String cut(String s, int max) {
        String v = s == null ? "" : s.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static boolean isHttps(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
