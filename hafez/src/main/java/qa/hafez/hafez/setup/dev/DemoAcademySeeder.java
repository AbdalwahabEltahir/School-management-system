package qa.hafez.hafez.setup.dev;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.academic.dto.AcademicDtos;
import qa.hafez.hafez.academic.model.AttendanceRecord;
import qa.hafez.hafez.academic.model.AttendanceStatus;
import qa.hafez.hafez.academic.model.NoticeAudience;
import qa.hafez.hafez.academic.repository.AttendanceRecordRepository;
import qa.hafez.hafez.academic.service.AcademicSetupService;
import qa.hafez.hafez.academic.service.MarksService;
import qa.hafez.hafez.academic.service.NoticeService;
import qa.hafez.hafez.dashboard.model.HealthScoreSnapshot;
import qa.hafez.hafez.dashboard.repository.HealthScoreRepository;
import qa.hafez.hafez.dashboard.service.ScoreService;
import qa.hafez.hafez.files.model.FilePermission;
import qa.hafez.hafez.files.model.FileProfile;
import qa.hafez.hafez.files.model.SchoolFile;
import qa.hafez.hafez.files.repository.FilePermissionRepository;
import qa.hafez.hafez.files.repository.FileProfileRepository;
import qa.hafez.hafez.files.repository.SchoolFileRepository;
import qa.hafez.hafez.organisation.model.Org;
import qa.hafez.hafez.organisation.model.Site;
import qa.hafez.hafez.organisation.model.Subject;
import qa.hafez.hafez.organisation.repository.OrgRepository;
import qa.hafez.hafez.organisation.repository.SiteRepository;
import qa.hafez.hafez.organisation.repository.SubjectRepository;
import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.people.model.PersonStatus;
import qa.hafez.hafez.people.repository.PersonRepository;
import qa.hafez.hafez.people.service.ParentLinks;
import qa.hafez.hafez.rules.service.RuleEngineService;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.model.Sensitivity;
import qa.hafez.hafez.shared.security.CurrentUser;
import qa.hafez.hafez.shared.util.SchoolCalendar;
import qa.hafez.hafez.workspace.AccessLevel;
import qa.hafez.hafez.workspace.DriveType;
import qa.hafez.hafez.workspace.ShareTargetType;

/**
 * <b>Demo only</b> (same switch as {@link DemoNetworkSeeder}: {@code hafez.demo-network=true}). Builds one complete,
 * invented school, <b>Demo Academy</b>, with a person in every role, so each role's screens have something to show:
 *
 * <ul>
 *   <li>School IT, principal, three coordinators, three teachers, twelve students in two classes, three parents;</li>
 *   <li>Term 1 marks (several assessments per subject, submitted and published) and one open assessment in Term 2;</li>
 *   <li>three weeks of attendance with a couple of students who are often absent;</li>
 *   <li>a weekly timetable, two notices;</li>
 *   <li>about 28 files checked by the real rule engine, owned by the three teachers, giving each subject a different
 *       picture (so the coordinator and teacher views differ), one urgent issue and one incident;</li>
 *   <li>two weeks of health-score history for the principal's trend.</li>
 * </ul>
 * Sign in by name on the sign-in page. Skipped if Demo Academy already exists, so it is safe to run twice.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "hafez.demo-network", havingValue = "true")
@RequiredArgsConstructor
public class DemoAcademySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAcademySeeder.class);
    private static final String NAME = "Demo Academy";
    private static final String DOMAIN = "demo-academy.eaa.test";

    private final OrgRepository orgs;
    private final SiteRepository sites;
    private final SubjectRepository subjects;
    private final PersonRepository people;
    private final SchoolFileRepository files;
    private final FileProfileRepository profiles;
    private final FilePermissionRepository permissions;
    private final HealthScoreRepository snapshots;
    private final AttendanceRecordRepository attendance;
    private final AcademicSetupService academic;
    private final MarksService marks;
    private final NoticeService notices;
    private final ParentLinks parentLinks;
    private final RuleEngineService engine;
    private final ScoreService scores;
    private final Clock clock;

    private record Sub(Subject subject, Person coordinator, Person teacher) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Org org = orgs.findAll().stream().findFirst().orElse(null);
        if (org == null) {
            return;
        }
        if (sites.findByOrgIdOrderByName(org.getId()).stream().anyMatch(s -> NAME.equalsIgnoreCase(s.getName()))
                || sites.findByDomainIgnoreCase(DOMAIN).isPresent()) {
            return;
        }
        Site site = new Site();
        site.setOrgId(org.getId());
        site.setName(NAME);
        site.setDomain(DOMAIN);
        site.setChecksEnabled(false);              // invented school: the nightly Drive check must not read it
        site.setFirstCheckAt(Instant.now(clock));
        sites.save(site);

        Person it = person(org, site, "nadia.farouk", "Nadia Farouk", Role.SCHOOL_IT, null);
        Person principal = person(org, site, "hind.alnaimi", "Hind Al-Naimi", Role.PRINCIPAL, null);

        List<Sub> subs = new ArrayList<>();
        String[][] defs = {
                {"Mathematics", "huda.saleh", "Huda Saleh", "sara.mansour", "Sara Mansour"},
                {"English", "tariq.aziz", "Tariq Aziz", "layla.haddad", "Layla Haddad"},
                {"Science", "mona.yousef", "Mona Yousef", "yusuf.karim", "Yusuf Karim"}};
        for (String[] d : defs) {
            Subject subject = new Subject();
            subject.setOrgId(org.getId());
            subject.setSiteId(site.getId());
            subject.setName(d[0]);
            subjects.save(subject);
            Person coord = person(org, site, d[1], d[2], Role.COORDINATOR, subject);
            Person teacher = person(org, site, d[3], d[4], Role.TEACHER, subject);
            subject.setCoordinatorId(coord.getId());
            subjects.save(subject);
            subs.add(new Sub(subject, coord, teacher));
        }
        site.setPrincipalId(principal.getId());
        site.setCoordinatorId(subs.get(0).coordinator().getId());
        sites.save(site);

        String[] studentNames = {"Amal Hassan", "Bader Ibrahim", "Cara Nasser", "Dana Khoury", "Elias Barakat", "Farah Mourad",
                "Ghazi Taha", "Hala Samara", "Idris Qasim", "Jana Rahal", "Karim Zayed", "Lubna Darwish"};
        List<Person> students = new ArrayList<>();
        for (String n : studentNames) {
            students.add(person(org, site, n.toLowerCase().replace(' ', '.'), n, Role.STUDENT, null));
        }
        String[] parentNames = {"Maha Hassan", "Nour Ibrahim", "Samir Taha"};
        for (int i = 0; i < parentNames.length; i++) {
            Person parent = person(org, site, parentNames[i].toLowerCase().replace(' ', '.') + ".parent", parentNames[i], Role.PARENT, null);
            parentLinks.link(parent.getId(), students.get(i).getId());                // Amal, Bader, Ghazi
            if (i == 0) {
                parentLinks.link(parent.getId(), students.get(1).getId());            // one parent with two children
            }
            if (i == 2) {
                parentLinks.link(parent.getId(), students.get(6).getId());
            }
        }

        CurrentUser admin = user(it, org, site);
        CurrentUser coord = user(subs.get(0).coordinator(), org, site);
        LocalDate today = LocalDate.now(clock.withZone(SchoolCalendar.ZONE));
        academic.saveTerm(coord, 1, "Term 1", today.minusMonths(3), today.plusDays(30));
        academic.saveTerm(coord, 2, "Term 2", today.plusDays(60), today.plusMonths(5));
        academic.saveTerm(coord, 3, "Term 3", today.plusMonths(6), today.plusMonths(9));

        var sectionA = academic.createSection(coord, 8, "A");
        var sectionB = academic.createSection(coord, 8, "B");
        var sections = List.of(sectionA, sectionB);
        for (int i = 0; i < students.size(); i++) {
            academic.enrol(coord, (i < 6 ? sectionA : sectionB).id(), students.get(i).getEmail());
        }
        int room = 1;
        for (var section : sections) {
            int period = 1;
            for (Sub sub : subs) {
                academic.assign(coord, section.id(), sub.subject().getId(), sub.teacher().getId());
            }
            // Each section has its own lessons; the other section's lessons sit in different periods, so no teacher is double-booked.
            int offset = section == sectionA ? 0 : 3;
            for (int s = 0; s < subs.size(); s++) {
                Sub sub = subs.get(s);
                academic.addLesson(coord, section.id(), sub.subject().getId(), s, 1 + offset, "Room " + (10 + room));
                academic.addLesson(coord, section.id(), sub.subject().getId(), (s + 2) % 5, 2 + offset, "Room " + (10 + room));
            }
            room++;
        }

        seedMarks(sections.stream().map(s -> s.id()).toList(), students, subs, org, site);
        seedAttendance(sections.get(0).id(), students.subList(0, 6), subs.get(0).teacher(), today);
        seedAttendance(sections.get(1).id(), students.subList(6, 12), subs.get(0).teacher(), today);

        Instant now = Instant.now(clock);
        notices.create(admin, new AcademicDtos.NoticeRequest("Term 1 results are out", "نتائج الفصل الأول صدرت",
                "Term 1 results are published. Parents can see them from the Results page.", "نُشرت نتائج الفصل الأول ويمكن لأولياء الأمور الاطلاع عليها.",
                now.minusSeconds(86400), now.plusSeconds(21L * 24 * 3600), NoticeAudience.ALL));
        notices.create(admin, new AcademicDtos.NoticeRequest("Science fair", "معرض العلوم",
                "The Science fair is next month. Students should talk to their Science teacher about projects.", "معرض العلوم الشهر القادم. على الطلاب التحدث مع معلم العلوم.",
                now.minusSeconds(3600), now.plusSeconds(30L * 24 * 3600), NoticeAudience.ALL));

        seedFiles(site, subs);
        log.info("Demo Academy created. Sign in by name: Hind Al-Naimi (principal), Nadia Farouk (IT), Huda Saleh (coordinator), "
                + "Sara Mansour (teacher), Amal Hassan (student), Maha Hassan (parent).");
    }

    // ------------------------------------------------------------------ marks

    /** Three Term 1 assessments per subject and class, submitted and published; one open Term 2 assessment for Maths. */
    private void seedMarks(List<UUID> sectionIds, List<Person> students, List<Sub> subs, Org org, Site site) {
        Random rnd = new Random(42);
        double[] ability = {.92, .74, .58, .83, .66, .88, .79, .45, .71, .95, .62, .77};   // one struggling, a few strong
        String[] names = {"Quiz 1", "Mid-term exam", "Project"};
        int[] max = {20, 100, 40};
        for (int sIdx = 0; sIdx < sectionIds.size(); sIdx++) {
            UUID section = sectionIds.get(sIdx);
            List<Person> mine = students.subList(sIdx * 6, sIdx * 6 + 6);
            for (Sub sub : subs) {
                CurrentUser teacher = user(sub.teacher(), org, site);
                for (int a = 0; a < names.length; a++) {
                    var assessment = marks.create(teacher, section, sub.subject().getId(), 1, names[a], BigDecimal.valueOf(max[a]));
                    List<AcademicDtos.MarkInput> inputs = new ArrayList<>();
                    for (Person st : mine) {
                        double base = ability[students.indexOf(st)] + (rnd.nextDouble() - .5) * .2 - (a == 1 ? .04 : 0);
                        double v = Math.max(0, Math.min(1, base)) * max[a];
                        inputs.add(new AcademicDtos.MarkInput(st.getId(),
                                BigDecimal.valueOf(v).multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.HALF_UP).divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP), false));
                    }
                    marks.enter(teacher, assessment.id(), inputs);
                    marks.submit(teacher, assessment.id());
                }
                if (sub == subs.get(0)) {
                    marks.create(teacher, section, sub.subject().getId(), 2, "Quiz 1", BigDecimal.valueOf(20)); // still open: the teacher has work
                }
            }
            // Any subject teacher may publish; use the Maths teacher.
            marks.publish(user(subs.get(0).teacher(), org, site), section, 1);
        }
    }

    // ------------------------------------------------------------------ attendance

    /** Roughly three school weeks. Two students are absent often, most are almost always present. */
    private void seedAttendance(UUID section, List<Person> students, Person recorder, LocalDate today) {
        Random rnd = new Random(7);
        List<AttendanceRecord> out = new ArrayList<>();
        for (int d = 21; d >= 0; d--) {
            LocalDate date = today.minusDays(d);
            if (!SchoolCalendar.isSchoolDay(date)) {
                continue;
            }
            for (int i = 0; i < students.size(); i++) {
                double absentChance = i == 1 ? .30 : i == 4 ? .18 : .04;       // student 2 and 5 miss the most
                double r = rnd.nextDouble();
                AttendanceStatus status = r < absentChance ? AttendanceStatus.ABSENT
                        : r < absentChance + .03 ? AttendanceStatus.LEAVE : AttendanceStatus.PRESENT;
                AttendanceRecord rec = new AttendanceRecord();
                rec.setOrgId(recorder.getOrgId());
                rec.setSiteId(recorder.getSiteId());
                rec.setSectionId(section);
                rec.setStudentId(students.get(i).getId());
                rec.setAttendanceDate(date);
                rec.setStatus(status);
                rec.setRecordedBy(recorder.getId());
                out.add(rec);
            }
        }
        attendance.saveAll(out);
    }

    // ------------------------------------------------------------------ files, issues, health history

    /** Each subject gets a different picture: Maths one urgent + incident, English the most clutter, Science nearly clean. */
    private void seedFiles(Site site, List<Sub> subs) {
        List<UUID> all = new ArrayList<>();
        Sub maths = subs.get(0), english = subs.get(1), science = subs.get(2);
        for (int i = 1; i <= 6; i++) {
            all.add(file(site, maths, "Algebra worksheet " + i + ".docx", "Academic/Maths", DriveType.SHARED, "Policy", Sensitivity.INTERNAL, false).getId());
        }
        SchoolFile marksBook = file(site, maths, "Grade 8A marks.xlsx", "Maths", DriveType.SHARED, "Mark book", Sensitivity.RESTRICTED, true);
        grant(marksBook, ShareTargetType.ANYONE_WITH_LINK, null);                              // urgent: public link
        all.add(marksBook.getId());
        all.add(file(site, maths, "Parent contacts 8A.xlsx", null, DriveType.PERSONAL, "Contact list", Sensitivity.RESTRICTED, true).getId());
        SchoolFile exam = file(site, maths, "Grade 8 Exam (December).pdf", "Maths/Exams", DriveType.SHARED, "Exam", Sensitivity.CONFIDENTIAL, false);
        exam.setExamDate(LocalDate.now(clock).plusDays(7));
        files.save(exam);
        grant(exam, ShareTargetType.EXTERNAL_USER, "moderator@outside.example");               // exam shared early: an incident
        all.add(exam.getId());

        for (int i = 1; i <= 5; i++) {
            all.add(file(site, english, "Reading list " + i + ".docx", "Academic/English", DriveType.SHARED, "Policy", Sensitivity.INTERNAL, false).getId());
        }
        for (int i = 1; i <= 3; i++) {
            all.add(file(site, english, "Class list " + i + ".xlsx", null, DriveType.PERSONAL, "Contact list", Sensitivity.RESTRICTED, true).getId());
        }
        for (int i = 1; i <= 7; i++) {
            all.add(file(site, science, "Lab notes " + i + ".docx", "Academic/Science", DriveType.SHARED, "Policy", Sensitivity.INTERNAL, false).getId());
        }
        all.add(file(site, science, "Science fair groups.xlsx", null, DriveType.PERSONAL, "Contact list", Sensitivity.RESTRICTED, true).getId());

        for (UUID id : all) {
            engine.evaluateFile(id);
        }
        scores.snapshotToday(site);
        history(site);
    }

    /** Two weeks of earlier scores that slowly improve, so the principal's trend has a shape. */
    private void history(Site site) {
        LocalDate today = LocalDate.now(clock);
        HealthScoreSnapshot now = snapshots.findBySiteIdAndScoreDate(site.getId(), today).orElse(null);
        if (now == null) {
            return;
        }
        for (int d = 1; d <= 14; d++) {
            int normal = Math.max(0, now.getOpenNormal() + d * 2 / 3 + (d % 3 == 0 ? 1 : 0));
            int urgent = Math.max(0, now.getOpenUrgent() + (d > 8 ? 1 : 0));
            HealthScoreSnapshot s = new HealthScoreSnapshot();
            s.setOrgId(site.getOrgId());
            s.setSiteId(site.getId());
            s.setScoreDate(today.minusDays(d));
            s.setFilesChecked(now.getFilesChecked());
            s.setOpenNormal(normal);
            s.setOpenUrgent(urgent);
            s.setScore(ScoreService.calculate(normal, urgent, now.getFilesChecked()));
            snapshots.save(s);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Person person(Org org, Site site, String localPart, String name, Role role, Subject subject) {
        Person p = new Person();
        p.setOrgId(org.getId());
        p.setSiteId(site.getId());
        p.setEmail(localPart + "@" + DOMAIN);
        p.setDisplayName(name);
        p.setRole(role);
        p.setStatus(PersonStatus.ACTIVE);
        p.setSubjectId(subject == null ? null : subject.getId());
        return people.saveAndFlush(p);
    }

    private CurrentUser user(Person p, Org org, Site site) {
        return new CurrentUser(p.getId(), org.getId(), site.getId(), p.getRole(), p.getSubjectId(), p.getEmail(), p.getDisplayName());
    }

    private SchoolFile file(Site site, Sub sub, String name, String folder, DriveType drive, String docType,
                            Sensitivity sensitivity, boolean studentData) {
        SchoolFile f = new SchoolFile();
        f.setOrgId(site.getOrgId());
        f.setSiteId(site.getId());
        f.setWorkspaceFileId("demo-" + UUID.randomUUID());
        f.setName(name);
        f.setOwnerEmail(sub.teacher().getEmail());
        f.setDriveType(drive);
        f.setFolderPath(folder);
        f.setSubjectId(sub.subject().getId());
        f.setSensitivity(sensitivity);
        files.save(f);

        FileProfile p = new FileProfile();
        p.setOrgId(f.getOrgId());
        p.setSiteId(f.getSiteId());
        p.setFileId(f.getId());
        p.setDocType(docType);
        p.setSubjectId(sub.subject().getId());
        p.setSensitivity(sensitivity);
        p.setHasStudentData(studentData);
        p.setConfidence(0.96);
        p.setModelVersion("demo-seed");
        p.getEvidence().add("Invented demo file");
        profiles.save(p);
        return f;
    }

    private void grant(SchoolFile file, ShareTargetType type, String grantee) {
        FilePermission p = new FilePermission();
        p.setOrgId(file.getOrgId());
        p.setSiteId(file.getSiteId());
        p.setFileId(file.getId());
        p.setGrantee(grantee);
        p.setTargetType(type);
        p.setAccessLevel(AccessLevel.VIEWER);
        p.setExternal(type == ShareTargetType.EXTERNAL_USER);
        permissions.save(p);
    }
}
