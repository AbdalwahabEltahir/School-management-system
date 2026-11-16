package qa.sijill.sijill.setup.dev;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import qa.sijill.sijill.academic.service.SchoolworkPoints;
import qa.sijill.sijill.organisation.model.Org;
import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.repository.OrgRepository;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.shared.model.Role;

/**
 * <b>Testing only</b> (runs while test sign-in is on). Makes every screen look populated for a demonstration:
 * <ul>
 *   <li>a school that has no teachers and no students is filled like the demo schools (staff, classes, months of
 *       history), so Leadership compares complete schools;</li>
 *   <li>the test accounts' school gets three classes of one grade with classmates, Teacher1 and Teacher2 teaching all
 *       three (so it shows how several classes work together), a clash-free timetable, published and open assessments,
 *       four weeks of attendance;</li>
 *   <li>then every school is topped up with homework, quizzes, study links, weekly plans and achievements.</li>
 * </ul>
 * Every step adds only what is missing, so restarting changes nothing, and nothing real is ever changed or removed.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "sijill.signin.test-mode", havingValue = "true")
@RequiredArgsConstructor
public class TestShowcase implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TestShowcase.class);
    private static final String DEMO_MAIL = "demo.hafez.test";

    private final JdbcTemplate jdbc;
    private final OrgRepository orgs;
    private final SiteRepository sites;
    private final PersonRepository people;
    private final DemoSchoolBuilder builder;
    private final DemoTopUp topUp;
    private final SchoolworkPoints schoolwork;

    @Override
    public void run(ApplicationArguments args) {
        Org org = orgs.findAll().stream().findFirst().orElse(null);
        if (org == null) {
            return;
        }
        try {
            fillEmptySchools(org);
        } catch (RuntimeException e) {
            log.warn("Test showcase: filling empty schools stopped: {}", e.getMessage());
        }
        try {
            showcase(org.getId());
        } catch (RuntimeException e) {
            log.warn("Test showcase: the test accounts' classes stopped: {}", e.getMessage());
        }
        try {
            topUp.topUp(org.getId());
        } catch (RuntimeException e) {
            log.warn("Test showcase: top-up stopped: {}", e.getMessage());
        }
    }

    // =================================================================== schools with nobody in them

    private void fillEmptySchools(Org org) {
        List<Person> admins = people.findByOrgIdAndRole(org.getId(), Role.EAA_ADMIN).stream().filter(p -> p.getStatus() != PersonStatus.LEFT).toList();
        List<Site> list = sites.findByOrgIdOrderByName(org.getId()).stream().filter(s -> s.isActive() && !s.isHeadOffice()).toList();
        int next = 1;
        for (Site site : list) {
            int staffAndStudents = count("SELECT count(*) FROM people WHERE site_id = ? AND role IN ('TEACHER', 'STUDENT')", site.getId());
            if (staffAndStudents > 0) {
                continue;
            }
            DemoSchools.Profile profile = DemoSchools.ALL.stream().filter(p -> p.name().equalsIgnoreCase(site.getName())).findFirst()
                    .orElse(DemoSchools.ALL.get(next++ % DemoSchools.ALL.size()));
            log.info("Test showcase: {} has nobody in it yet; filling it like the demo schools.", site.getName());
            builder.populate(org, site, profile, admins);
        }
    }

    // =================================================================== the test accounts' classes

    private record Lesson(UUID teacher, int day, int period) {}

    private void showcase(UUID org) {
        UUID t1 = testPerson(org, "teacher1", "TEACHER"), t2 = testPerson(org, "teacher2", "TEACHER");
        UUID student = testPerson(org, "student", "STUDENT"), coordinator = testPerson(org, "rajaa", "COORDINATOR");
        if (t1 == null && t2 == null) {
            return;
        }
        UUID site = jdbc.queryForObject("SELECT site_id FROM people WHERE id = ?", UUID.class, t1 != null ? t1 : t2);
        UUID recorder = firstOf(site, "ATTENDANCE_OFFICER", "SCHOOL_IT", "PRINCIPAL", "COORDINATOR");
        Random rnd = new Random(site.hashCode());

        // the classes: the test student's class and two more of the same grade
        Map<String, Object> own = student == null ? null : jdbc.queryForList("SELECT s.id, s.grade, s.name FROM enrolments e JOIN sections s ON s.id = e.section_id WHERE e.student_id = ?", student)
                .stream().findFirst().orElse(null);
        int grade = own == null ? 7 : ((Number) own.get("grade")).intValue();
        Map<String, UUID> sections = new LinkedHashMap<>();
        for (String name : new String[] {"A", "B", "C"}) {
            sections.put(grade + name, section(org, site, grade, name));
        }
        if (own != null) {
            sections.putIfAbsent(grade + (String) own.get("name"), (UUID) own.get("id"));
        } else if (student != null) {
            enrol(org, site, sections.values().iterator().next(), student);
        }

        // the subjects, and who teaches them in every class
        UUID math = subject(org, site, "Math", "Mathematics"), english = subject(org, site, "English"), science = subject(org, site, "Science"),
                arabic = subject(org, site, "Arabic");
        Map<UUID, UUID> teacherOf = new LinkedHashMap<>();
        teacherOf.put(math, t2 != null ? t2 : t1);
        teacherOf.put(english, t1 != null ? t1 : t2);
        teacherOf.put(science, t1 != null ? t1 : t2);
        teacherOf.put(arabic, coordinator != null ? coordinator : (t2 != null ? t2 : t1));

        int i = 0;
        for (Map.Entry<String, UUID> sec : sections.entrySet()) {
            classmates(org, site, sec.getKey(), sec.getValue(), i++ * 8);
            for (Map.Entry<UUID, UUID> st : teacherOf.entrySet()) {
                assign(org, site, sec.getValue(), st.getKey(), st.getValue());
            }
        }
        timetable(org, site, sections);
        UUID term = currentTerm(org, site);
        for (UUID sec : sections.values()) {
            for (Map<String, Object> ta : jdbc.queryForList("SELECT subject_id, teacher_id FROM teaching_assignments WHERE section_id = ?", sec)) {
                assessments(org, site, sec, (UUID) ta.get("subject_id"), term, student, rnd);
            }
            schoolwork.syncExams(sec);
            if (recorder != null) {
                attendance(org, site, sec, recorder, rnd);
            }
        }
        log.info("Test showcase: classes {} ready for the test accounts.", sections.keySet());
    }

    private UUID testPerson(UUID org, String name, String role) {
        return jdbc.queryForList("SELECT id FROM people WHERE org_id = ? AND role = ? AND lower(replace(display_name, ' ', '')) = ? AND status <> 'LEFT' ORDER BY created_at LIMIT 1",
                UUID.class, org, role, name).stream().findFirst().orElse(null);
    }

    private UUID firstOf(UUID site, String... roles) {
        for (String r : roles) {
            UUID id = jdbc.queryForList("SELECT id FROM people WHERE site_id = ? AND role = ? AND status <> 'LEFT' LIMIT 1", UUID.class, site, r).stream().findFirst().orElse(null);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private UUID section(UUID org, UUID site, int grade, String name) {
        UUID id = jdbc.queryForList("SELECT id FROM sections WHERE site_id = ? AND grade = ? AND name = ?", UUID.class, site, grade, name).stream().findFirst().orElse(null);
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("INSERT INTO sections (id, created_at, org_id, site_id, grade, name) VALUES (?, now(), ?, ?, ?, ?)", id, org, site, grade, name);
        }
        return id;
    }

    private UUID subject(UUID org, UUID site, String... names) {
        for (String n : names) {
            UUID id = jdbc.queryForList("SELECT id FROM subjects WHERE site_id = ? AND lower(name) = lower(?)", UUID.class, site, n).stream().findFirst().orElse(null);
            if (id != null) {
                return id;
            }
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO subjects (id, created_at, org_id, site_id, name) VALUES (?, now(), ?, ?, ?)", id, org, site, names[names.length - 1]);
        return id;
    }

    private void enrol(UUID org, UUID site, UUID section, UUID student) {
        if (count("SELECT count(*) FROM enrolments WHERE student_id = ?", student) == 0) {
            jdbc.update("INSERT INTO enrolments (id, created_at, org_id, site_id, section_id, student_id, stage) VALUES (?, now(), ?, ?, ?, ?, 'SIJIL')",
                    UUID.randomUUID(), org, site, section, student);
        }
    }

    /** Eight classmates per class, with real names; skipped once the class has them. */
    private void classmates(UUID org, UUID site, String label, UUID section, int offset) {
        int have = count("SELECT count(*) FROM enrolments WHERE section_id = ?", section);
        for (int k = 0; have < 8 && k < 8; k++) {
            String name = DemoContent.STUDENT_NAMES.get((offset + k) % DemoContent.STUDENT_NAMES.size());
            String email = name.toLowerCase().replace("al-", "").replace(' ', '.') + "." + label.toLowerCase() + "@" + DEMO_MAIL;
            UUID id = jdbc.queryForList("SELECT id FROM people WHERE lower(email) = lower(?)", UUID.class, email).stream().findFirst().orElse(null);
            if (id == null) {
                id = UUID.randomUUID();
                jdbc.update("INSERT INTO people (id, created_at, org_id, site_id, email, display_name, role, status) VALUES (?, now(), ?, ?, ?, ?, 'STUDENT', 'ACTIVE')",
                        id, org, site, email, name);
            }
            if (count("SELECT count(*) FROM enrolments WHERE student_id = ?", id) == 0) {
                jdbc.update("INSERT INTO enrolments (id, created_at, org_id, site_id, section_id, student_id, stage) VALUES (?, now(), ?, ?, ?, ?, 'SIJIL')",
                        UUID.randomUUID(), org, site, section, id);
                have++;
            }
        }
    }

    private void assign(UUID org, UUID site, UUID section, UUID subject, UUID teacher) {
        if (teacher != null && count("SELECT count(*) FROM teaching_assignments WHERE section_id = ? AND subject_id = ?", section, subject) == 0) {
            jdbc.update("INSERT INTO teaching_assignments (id, created_at, org_id, site_id, section_id, subject_id, teacher_id) VALUES (?, now(), ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), org, site, section, subject, teacher);
        }
    }

    /** Each subject on four days a week in every class; no class or teacher is ever in two places at once. */
    private void timetable(UUID org, UUID site, Map<String, UUID> sections) {
        Set<String> busy = new HashSet<>();
        for (Map<String, Object> l : jdbc.queryForList("SELECT section_id, teacher_id, day_of_week, period FROM timetable_entries WHERE site_id = ?", site)) {
            busy.add("s" + l.get("section_id") + "/" + l.get("day_of_week") + "/" + l.get("period"));
            busy.add("t" + l.get("teacher_id") + "/" + l.get("day_of_week") + "/" + l.get("period"));
        }
        int i = 0;
        for (Map.Entry<String, UUID> sec : sections.entrySet()) {
            UUID section = sec.getValue();
            if (count("SELECT count(*) FROM timetable_entries WHERE section_id = ?", section) >= 8) {
                i++;
                continue;
            }
            List<Map<String, Object>> tas = jdbc.queryForList("SELECT subject_id, teacher_id FROM teaching_assignments WHERE section_id = ? ORDER BY subject_id", section);
            for (int day = 0; day < 5; day++) {
                for (int k = 0; k < tas.size(); k++) {
                    if ((day + k + i) % 5 == 4) {
                        continue;                                   // each subject has one day off a week
                    }
                    UUID teacher = (UUID) tas.get(k).get("teacher_id");
                    for (int period = 1; period <= 8; period++) {
                        String s = "s" + section + "/" + day + "/" + period, t = "t" + teacher + "/" + day + "/" + period;
                        if (busy.contains(s) || busy.contains(t)) {
                            continue;
                        }
                        jdbc.update("INSERT INTO timetable_entries (id, created_at, org_id, site_id, section_id, subject_id, teacher_id, day_of_week, period, room) "
                                        + "VALUES (?, now(), ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), org, site, section, tas.get(k).get("subject_id"), teacher, day, period,
                                "Room " + sec.getKey());
                        busy.add(s);
                        busy.add(t);
                        break;
                    }
                }
            }
            i++;
        }
    }

    /** The term that covers today; one is added if there is none, so published marks have a term to sit in. */
    private Integer currentTermNumber(UUID site) {
        return jdbc.queryForList("SELECT number FROM terms WHERE site_id = ? AND starts_on <= current_date AND ends_on >= current_date ORDER BY number LIMIT 1", Integer.class, site)
                .stream().findFirst().orElse(null);
    }

    private UUID currentTerm(UUID org, UUID site) {
        Integer n = currentTermNumber(site);
        if (n == null) {
            List<Integer> used = jdbc.queryForList("SELECT number FROM terms WHERE site_id = ?", Integer.class, site);
            for (int k = 1; k <= 3 && n == null; k++) {
                if (!used.contains(k)) {
                    n = k;
                }
            }
            if (n == null) {
                n = 3;
                jdbc.update("UPDATE terms SET ends_on = current_date + 90 WHERE site_id = ? AND number = 3 AND ends_on < current_date", site);
            } else {
                jdbc.update("INSERT INTO terms (id, created_at, org_id, site_id, number, name, starts_on, ends_on) VALUES (?, now(), ?, ?, ?, ?, current_date - 14, current_date + 90)",
                        UUID.randomUUID(), org, site, n, "Term " + n);
            }
        }
        return jdbc.queryForObject("SELECT id FROM terms WHERE site_id = ? AND number = ?", UUID.class, site, n);
    }

    /** A published unit test with marks for everyone, and a new test still open for the teacher to mark. */
    private void assessments(UUID org, UUID site, UUID section, UUID subject, UUID termId, UUID student, Random rnd) {
        int term = jdbc.queryForObject("SELECT number FROM terms WHERE id = ?", Integer.class, termId);
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        if (count("SELECT count(*) FROM assessments WHERE section_id = ? AND subject_id = ? AND name = 'Unit test 1'", section, subject) == 0) {
            UUID a = UUID.randomUUID();
            Instant submitted = Instant.now().minusSeconds(4 * 86400L);
            jdbc.update("INSERT INTO assessments (id, created_at, org_id, site_id, section_id, subject_id, term, name, max_mark, submitted_at, week_start, published_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'Unit test 1', 20, ?, ?, ?)", a, Timestamp.from(submitted.minusSeconds(86400)), org, site, section, subject, term,
                    Timestamp.from(submitted), Date.valueOf(monday.minusWeeks(1)), Timestamp.from(submitted.plusSeconds(86400)));
            for (UUID st : jdbc.queryForList("SELECT student_id FROM enrolments WHERE section_id = ?", UUID.class, section)) {
                boolean absent = !st.equals(student) && rnd.nextDouble() < 0.04;
                double mark = st.equals(student) ? 17 : 10 + rnd.nextInt(11);
                jdbc.update("INSERT INTO marks (id, created_at, org_id, site_id, assessment_id, student_id, mark_value, absent) VALUES (?, now(), ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), org, site, a, st, absent ? null : mark, absent);
            }
        }
        if (count("SELECT count(*) FROM assessments WHERE section_id = ? AND subject_id = ? AND name = 'Unit test 2'", section, subject) == 0) {
            jdbc.update("INSERT INTO assessments (id, created_at, org_id, site_id, section_id, subject_id, term, name, max_mark, week_start) VALUES (?, now(), ?, ?, ?, ?, ?, 'Unit test 2', 20, ?)",
                    UUID.randomUUID(), org, site, section, subject, term, Date.valueOf(monday));
        }
    }

    /** Four weeks of attendance: mostly present, a few absences and leave. */
    private void attendance(UUID org, UUID site, UUID section, UUID recorder, Random rnd) {
        List<UUID> students = jdbc.queryForList("SELECT student_id FROM enrolments WHERE section_id = ?", UUID.class, section);
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = LocalDate.now().minusDays(28); d.isBefore(LocalDate.now()); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.FRIDAY && d.getDayOfWeek() != DayOfWeek.SATURDAY) {
                days.add(d);
            }
        }
        for (LocalDate d : days) {
            for (UUID st : students) {
                double r = rnd.nextDouble();
                String status = r < 0.92 ? "PRESENT" : r < 0.97 ? "ABSENT" : "LEAVE";
                jdbc.update("INSERT INTO attendance_records (id, created_at, org_id, site_id, section_id, student_id, attendance_date, status, recorded_by, reason) "
                                + "VALUES (?, now(), ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (student_id, attendance_date) DO NOTHING",
                        UUID.randomUUID(), org, site, section, st, Date.valueOf(d), status, recorder, "LEAVE".equals(status) ? "Medical appointment" : null);
            }
        }
    }

    private int count(String sql, Object... args) {
        Number n = jdbc.queryForObject(sql, Number.class, args);
        return n == null ? 0 : n.intValue();
    }
}
