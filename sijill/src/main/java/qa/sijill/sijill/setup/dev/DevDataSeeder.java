package qa.sijill.sijill.setup.dev;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import qa.sijill.sijill.academic.dto.AcademicDtos;
import qa.sijill.sijill.academic.model.AttendanceStatus;
import qa.sijill.sijill.academic.model.NoticeAudience;
import qa.sijill.sijill.academic.service.AcademicSetupService;
import qa.sijill.sijill.academic.service.AttendanceService;
import qa.sijill.sijill.academic.service.MarksService;
import qa.sijill.sijill.academic.service.MaterialService;
import qa.sijill.sijill.academic.service.NoticeService;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.shared.util.SchoolCalendar;
import qa.sijill.sijill.files.model.FilePermission;
import qa.sijill.sijill.files.model.FileProfile;
import qa.sijill.sijill.files.model.SchoolFile;
import qa.sijill.sijill.files.repository.FilePermissionRepository;
import qa.sijill.sijill.files.repository.FileProfileRepository;
import qa.sijill.sijill.files.repository.SchoolFileRepository;
import qa.sijill.sijill.organisation.model.Org;
import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.model.Subject;
import qa.sijill.sijill.organisation.repository.OrgRepository;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.organisation.repository.SubjectRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.rules.service.RuleEngineService;
import qa.sijill.sijill.setup.OrganisationSetupService;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.model.Sensitivity;
import qa.sijill.sijill.workspace.AccessLevel;
import qa.sijill.sijill.workspace.DriveType;
import qa.sijill.sijill.workspace.ShareTargetType;

/**
 * <b>Development only</b> ({@code dev} profile): creates a small demo network the first time the app
 * starts on an empty database, so every role has something to sign in as and something to look at.
 *
 * <ul>
 *   <li>Head office with Leadership: {@code lina.sabbagh@eaa.test};</li>
 *   <li>School B with an IT admin, a principal, a coordinator (Mathematics) and a teacher;</li>
 *   <li>four files owned by the teacher, three of which break rules, checked by the real rule engine.</li>
 * </ul>
 * Does nothing if an organisation already exists, so it never touches real data. All names and files are
 * invented.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String SCHOOL_DOMAIN = "school-b.eaa.test";

    private final OrgRepository orgs;
    private final SiteRepository sites;
    private final SubjectRepository subjects;
    private final PersonRepository people;
    private final SchoolFileRepository files;
    private final FileProfileRepository profiles;
    private final FilePermissionRepository permissions;
    private final OrganisationSetupService setup;
    private final RuleEngineService engine;
    private final AcademicSetupService academic;
    private final AttendanceService attendance;
    private final MarksService marks;
    private final NoticeService notices;
    private final MaterialService materials;
    private final Clock clock;
    private final qa.sijill.sijill.people.service.ParentLinks parentLinks;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (orgs.count() > 0) {
            log.info("Dev seed skipped: an organisation already exists.");
            return;
        }
        Org org = setup.createOrganisation("Education Above All (demo)", "EAA Central", "eaa.test",
                "lina.sabbagh@eaa.test", "Lina Sabbagh");

        // Head-office team: a second EAA administrator (four-eyes needs two) and a read-only Leadership member.
        Site head = sites.findByDomainIgnoreCase("eaa.test").orElseThrow();
        for (String[] n : new String[][] {{"eaa.admin2", "Maha Al-Thani", "EAA_ADMIN"}, {"noora.leader", "Noora Al-Kuwari", "LEADERSHIP"}}) {
            Person p = new Person();
            p.setOrgId(org.getId());
            p.setSiteId(head.getId());
            p.setEmail(n[0] + "@eaa.test");
            p.setDisplayName(n[1]);
            p.setRole(Role.valueOf(n[2]));
            p.setStatus(PersonStatus.ACTIVE);
            people.saveAndFlush(p);
        }

        Site school = new Site();
        school.setOrgId(org.getId());
        school.setName("School B");
        school.setDomain(SCHOOL_DOMAIN);
        school.setFirstCheckAt(Instant.now(clock));
        sites.save(school);

        Subject maths = new Subject();
        maths.setOrgId(org.getId());
        maths.setSiteId(school.getId());
        maths.setName("Mathematics");
        subjects.save(maths);

        Person it = person(org, school, "omar.fathi", "Omar Fathi", Role.SCHOOL_IT, null);
        Person principal = person(org, school, "reem.khalil", "Reem Khalil", Role.PRINCIPAL, null);
        Person coordinator = person(org, school, "huda.saleh", "Huda Saleh", Role.COORDINATOR, maths);
        Person teacher = person(org, school, "sara.mansour", "Sara Mansour", Role.TEACHER, maths);
        maths.setCoordinatorId(coordinator.getId());
        school.setPrincipalId(principal.getId());
        school.setCoordinatorId(coordinator.getId());

        // Three files that break rules, one that is fine. The real rule engine decides what is wrong.
        SchoolFile marks = file(school, maths, teacher, "Grade 7B marks – Term 1.xlsx", "Maths", DriveType.SHARED,
                "Mark book", Sensitivity.RESTRICTED, true);
        grant(marks, ShareTargetType.ANYONE_WITH_LINK, null);               // R2 (public link) and R4 (not in Student Records)

        SchoolFile contacts = file(school, maths, teacher, "Parent contacts 7B.xlsx", null, DriveType.PERSONAL,
                "Contact list", Sensitivity.RESTRICTED, true);               // R1 (personal drive) and R4

        SchoolFile exam = file(school, maths, teacher, "Grade 9 Exam (December).pdf", "Maths/Exams", DriveType.SHARED,
                "Exam", Sensitivity.CONFIDENTIAL, false);
        exam.setExamDate(LocalDate.now(clock).plusDays(7));
        files.save(exam);
        grant(exam, ShareTargetType.EXTERNAL_USER, "moderator@outside.example");   // R6 (exam shared early): an incident

        SchoolFile handbook = file(school, maths, teacher, "Maths department handbook.docx", "Academic/Maths",
                DriveType.SHARED, "Policy", Sensitivity.INTERNAL, false);         // clean

        for (SchoolFile f : new SchoolFile[] {marks, contacts, exam, handbook}) {
            engine.evaluateFile(f.getId());
        }
        seedSchoolPortal(org, school, maths, it, coordinator, teacher);
        log.info("Dev seed created. Sign in at /dev/login (leadership, it, principal, coordinator, teacher, student, parent).");
    }

    /** A class, its students and teachers, a timetable, marks, a notice and a study material, so both portals have content. */
    private void seedSchoolPortal(Org org, Site school, Subject maths, Person it, Person coordinator, Person teacher) {
        Subject english = new Subject();
        english.setOrgId(org.getId());
        english.setSiteId(school.getId());
        english.setName("English");
        subjects.save(english);
        Person englishTeacher = person(org, school, "layla.haddad", "Layla Haddad", Role.TEACHER, english);
        Person amal = person(org, school, "amal.student", "Amal Student", Role.STUDENT, null);
        Person bader = person(org, school, "bader.student", "Bader Student", Role.STUDENT, null);
        Person cara = person(org, school, "cara.student", "Cara Student", Role.STUDENT, null);
        Person parent = person(org, school, "maha.parent", "Maha Parent", Role.PARENT, null); // follows Amal and Bader
        log.info("Seeded student IDs (a parent registers with one): Amal {}, Bader {}, Cara {}", amal.getStudentCode(), bader.getStudentCode(), cara.getStudentCode());
        parentLinks.link(parent.getId(), amal.getId());
        parentLinks.link(parent.getId(), bader.getId());

        CurrentUser admin = new CurrentUser(it.getId(), org.getId(), school.getId(), Role.SCHOOL_IT, null, it.getEmail(), it.getDisplayName());
        CurrentUser sara = new CurrentUser(teacher.getId(), org.getId(), school.getId(), Role.TEACHER, maths.getId(), teacher.getEmail(), teacher.getDisplayName());
        // The coordinator creates terms, classes and sections; the teacher sets assessments and grades; IT unlocks and publishes.
        CurrentUser coord = new CurrentUser(coordinator.getId(), org.getId(), school.getId(), Role.COORDINATOR, coordinator.getSubjectId(), coordinator.getEmail(), coordinator.getDisplayName());
        LocalDate year = LocalDate.now(clock.withZone(SchoolCalendar.ZONE));
        academic.saveTerm(coord, 1, "Term 1", year.minusMonths(3), year.plusDays(30));
        academic.saveTerm(coord, 2, "Term 2", year.plusDays(60), year.plusMonths(5));
        academic.saveTerm(coord, 3, "Term 3", year.plusMonths(6), year.plusMonths(9));
        var section = academic.createSection(coord, 7, "B");
        for (Person s : List.of(amal, bader, cara)) {
            academic.enrol(coord, section.id(), s.getEmail());
        }
        academic.assign(coord, section.id(), maths.getId(), teacher.getId());
        academic.assign(coord, section.id(), english.getId(), englishTeacher.getId());
        academic.addLesson(coord, section.id(), maths.getId(), 0, 1, "Room 12");
        academic.addLesson(coord, section.id(), maths.getId(), 2, 1, "Room 12");
        academic.addLesson(coord, section.id(), english.getId(), 1, 2, "Room 4");
        academic.addLesson(coord, section.id(), english.getId(), 3, 2, "Room 4");

        var quiz = marks.create(sara, section.id(), maths.getId(), 1, "Quiz 1", new BigDecimal("40"));
        marks.enter(sara, quiz.id(), List.of(new AcademicDtos.MarkInput(amal.getId(), new BigDecimal("36"), false),
                new AcademicDtos.MarkInput(bader.getId(), new BigDecimal("28.5"), false),
                new AcademicDtos.MarkInput(cara.getId(), new BigDecimal("33"), false)));
        marks.submit(sara, quiz.id());
        marks.executePublish(sara, section.id(), 1);
        marks.create(sara, section.id(), maths.getId(), 2, "Mid-term exam", new BigDecimal("100")); // still open: the teacher has work to do

        Instant now = Instant.now(clock);
        notices.create(admin, new AcademicDtos.NoticeRequest("Parent-teacher meetings", "اجتماع أولياء الأمور",
                "Meetings run next Thursday afternoon. Book a slot through the school.", "تُعقد الاجتماعات يوم الخميس القادم بعد الظهر.",
                now.minusSeconds(3600), now.plusSeconds(14L * 24 * 3600), NoticeAudience.ALL));
        materials.add(sara, section.id(), maths.getId(), "Fractions revision notes", "https://drive.google.com/file/d/demo-fractions");

        if (SchoolCalendar.isSchoolDay(LocalDate.now(clock.withZone(SchoolCalendar.ZONE)))) {
            attendance.record(admin, section.id(), LocalDate.now(clock.withZone(SchoolCalendar.ZONE)),
                    Map.of(bader.getId(), AttendanceStatus.ABSENT), null);
        }
    }

    private Person person(Org org, Site site, String localPart, String name, Role role, Subject subject) {
        Person p = new Person();
        p.setOrgId(org.getId());
        p.setSiteId(site.getId());
        p.setEmail(localPart + "@" + SCHOOL_DOMAIN);
        p.setDisplayName(name);
        p.setRole(role);
        p.setStatus(PersonStatus.ACTIVE);
        p.setSubjectId(subject == null ? null : subject.getId());
        return people.saveAndFlush(p);
    }

    private SchoolFile file(Site site, Subject subject, Person owner, String name, String folder, DriveType drive,
                            String docType, Sensitivity sensitivity, boolean studentData) {
        SchoolFile f = new SchoolFile();
        f.setOrgId(site.getOrgId());
        f.setSiteId(site.getId());
        f.setWorkspaceFileId("dev-" + UUID.randomUUID());
        f.setName(name);
        f.setOwnerEmail(owner.getEmail());
        f.setDriveType(drive);
        f.setFolderPath(folder);
        f.setSubjectId(subject.getId());
        f.setSensitivity(sensitivity);
        files.save(f);

        FileProfile profile = new FileProfile();
        profile.setOrgId(f.getOrgId());
        profile.setSiteId(f.getSiteId());
        profile.setFileId(f.getId());
        profile.setDocType(docType);
        profile.setSubjectId(subject.getId());
        profile.setSensitivity(sensitivity);
        profile.setHasStudentData(studentData);
        profile.setConfidence(0.96);
        profile.setModelVersion("dev-seed");
        profile.getEvidence().add("Invented demo file");
        profiles.save(profile);
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
