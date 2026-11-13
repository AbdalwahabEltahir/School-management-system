package qa.sijill.sijill.setup.dev;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.academic.service.AcademicSetupService;
import qa.sijill.sijill.files.model.FilePermission;
import qa.sijill.sijill.files.model.FileProfile;
import qa.sijill.sijill.files.model.SchoolFile;
import qa.sijill.sijill.files.repository.FilePermissionRepository;
import qa.sijill.sijill.files.repository.FileProfileRepository;
import qa.sijill.sijill.files.repository.SchoolFileRepository;
import qa.sijill.sijill.dashboard.service.ScoreService;
import qa.sijill.sijill.organisation.model.Org;
import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.model.Subject;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.organisation.repository.SubjectRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.people.service.ParentLinks;
import qa.sijill.sijill.rules.service.RuleEngineService;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.model.Sensitivity;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.shared.util.SchoolCalendar;
import qa.sijill.sijill.workspace.AccessLevel;
import qa.sijill.sijill.workspace.DriveType;
import qa.sijill.sijill.workspace.ShareTargetType;

/**
 * <b>Demo only.</b> Builds one complete invented school from a {@link DemoSchools.Profile}: a person in every role,
 * two classes with a timetable, files checked by the real rule engine, and four months of history from
 * {@link DemoHistory}. Used for schools B to E (school A is built by the dev seeder).
 */
@Component
@RequiredArgsConstructor
public class DemoSchoolBuilder {

    private static final Logger log = LoggerFactory.getLogger(DemoSchoolBuilder.class);

    private static final String[] FIRST = {"Omar", "Layla", "Yusuf", "Mona", "Tariq", "Hind", "Khalid", "Noor", "Ibrahim", "Reem", "Faisal", "Dana",
            "Hamad", "Sara", "Nasser", "Aisha", "Majid", "Huda", "Salem", "Maryam", "Jassim", "Fatima", "Rashid", "Amina", "Saif", "Lulwa",
            "Zayed", "Haya", "Ali", "Moza"};
    private static final String[] FAMILY = {"Al-Thani", "Al-Kuwari", "Al-Naimi", "Al-Sulaiti", "Al-Mohannadi", "Al-Marri", "Al-Dosari", "Al-Hajri",
            "Al-Emadi", "Al-Mansouri", "Al-Obaidly", "Al-Khater", "Al-Ansari", "Al-Fardan", "Al-Malki", "Al-Attiyah", "Al-Misnad", "Al-Sada",
            "Al-Jaber", "Al-Muhannadi"};

    private final SiteRepository sites;
    private final SubjectRepository subjects;
    private final PersonRepository people;
    private final SchoolFileRepository files;
    private final FileProfileRepository profiles;
    private final FilePermissionRepository permissions;
    private final AcademicSetupService academic;
    private final ParentLinks parentLinks;
    private final RuleEngineService engine;
    private final ScoreService scores;
    private final DemoHistory history;
    private final DemoPickup pickup;
    private final Clock clock;

    private record Sub(Subject subject, Person coordinator, Person teacher) {}

    /** Hands out names that are unique inside one school. */
    private static final class Names {
        private final int seed;
        private final Set<String> used = new HashSet<>();
        private int next;

        Names(int seed) {
            this.seed = seed;
        }

        String person() {
            String n;
            do {
                n = FIRST[(seed * 7 + next * 3) % FIRST.length] + " " + FAMILY[(seed * 5 + next * 7 + next / FIRST.length) % FAMILY.length];
                next++;
            } while (!used.add(n));
            return n;
        }

        String withFamily(String familyOf) {
            String family = familyOf.substring(familyOf.indexOf(' ') + 1);
            String n;
            do {
                n = FIRST[(seed * 11 + next * 5) % FIRST.length] + " " + family;
                next++;
            } while (!used.add(n));
            return n;
        }
    }

    @Transactional
    public Site build(Org org, DemoSchools.Profile profile, List<Person> admins) {
        Site site = new Site();
        site.setOrgId(org.getId());
        site.setName(profile.name());
        site.setDomain(profile.domain());
        site.setChecksEnabled(false);              // invented school: the nightly Drive check must not read it
        site.setFirstCheckAt(Instant.now(clock));
        sites.save(site);
        Names names = new Names(profile.letter().charAt(0) - 'A' + 1);

        Person it = person(org, site, names.person(), Role.SCHOOL_IT, null);
        Person principal = person(org, site, names.person(), Role.PRINCIPAL, null);
        Person attendanceOfficer = person(org, site, names.person(), Role.ATTENDANCE_OFFICER, null);

        List<Sub> subs = new ArrayList<>();
        for (String name : new String[] {"Mathematics", "English", "Science"}) {
            Subject subject = new Subject();
            subject.setOrgId(org.getId());
            subject.setSiteId(site.getId());
            subject.setName(name);
            subjects.save(subject);
            Person coordinator = person(org, site, names.person(), Role.COORDINATOR, subject);
            Person teacher = person(org, site, names.person(), Role.TEACHER, subject);
            subject.setCoordinatorId(coordinator.getId());
            subjects.save(subject);
            subs.add(new Sub(subject, coordinator, teacher));
        }
        site.setPrincipalId(principal.getId());
        site.setCoordinatorId(subs.get(0).coordinator().getId());
        sites.save(site);

        List<Person> students = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            students.add(person(org, site, names.person(), Role.STUDENT, null));
        }
        List<Person> parents = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Person parent = person(org, site, names.withFamily(students.get(i * 3).getDisplayName()), Role.PARENT, null);
            parents.add(parent);
            parentLinks.link(parent.getId(), students.get(i * 3).getId());
            if (i == 0) {
                parentLinks.link(parent.getId(), students.get(1).getId());        // one parent with two children
            }
        }

        CurrentUser coord = user(subs.get(0).coordinator(), org, site);
        LocalDate today = LocalDate.now(clock.withZone(SchoolCalendar.ZONE));
        academic.saveTerm(coord, 1, "Term 1", today.minusMonths(4), today.minusDays(45));
        academic.saveTerm(coord, 2, "Term 2", today.minusDays(44), today.plusDays(45));
        academic.saveTerm(coord, 3, "Term 3", today.plusDays(46), today.plusMonths(4));

        var sectionA = academic.createSection(coord, 8, "A");
        var sectionB = academic.createSection(coord, 8, "B");
        var sections = List.of(sectionA, sectionB);
        for (int i = 0; i < students.size(); i++) {
            academic.enrol(coord, (i < 6 ? sectionA : sectionB).id(), students.get(i).getEmail());
        }
        int room = 1;
        for (var section : sections) {
            for (Sub sub : subs) {
                academic.assign(coord, section.id(), sub.subject().getId(), sub.teacher().getId());
            }
            int offset = section == sectionA ? 0 : 3;     // the other class's lessons sit in different periods, so no teacher is double-booked
            for (int s = 0; s < subs.size(); s++) {
                Sub sub = subs.get(s);
                academic.addLesson(coord, section.id(), sub.subject().getId(), s, 1 + offset, "Room " + (10 + room));
                academic.addLesson(coord, section.id(), sub.subject().getId(), (s + 2) % 5, 2 + offset, "Room " + (10 + room));
            }
            room++;
        }

        List<String> labels = new ArrayList<>();
        List<UUID> sectionIds = new ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            var sec = i < 6 ? sectionA : sectionB;
            labels.add(sec.grade() + sec.name());
            sectionIds.add(sec.id());
        }
        List<Person> parentOf = new ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            parentOf.add(i <= 1 ? parents.get(0) : i == 3 ? parents.get(1) : i == 6 ? parents.get(2) : null);
        }
        pickup.seed(site, students, parentOf, labels, sectionIds, subs.get(0).coordinator());

        history.academics(site, profile, attendanceOfficer, it);
        history.notices(site, it);
        seedFiles(site, subs, profile);
        history.scores(site, profile);
        history.tickets(site, profile, principal, admins);
        history.schoolItTickets(site, subs.get(0).teacher(), it);
        log.info("{} created. Sign in by name: {} (principal), {} (School IT), {} (coordinator), {} (teacher), {} (student).",
                profile.name(), principal.getDisplayName(), it.getDisplayName(), subs.get(0).coordinator().getDisplayName(),
                subs.get(0).teacher().getDisplayName(), students.get(0).getDisplayName());
        return site;
    }

    // ------------------------------------------------------------------ files and issues

    /** The profile decides how many files are clean and how many break a rule; the real rule engine finds the issues. */
    private void seedFiles(Site site, List<Sub> subs, DemoSchools.Profile p) {
        List<UUID> all = new ArrayList<>();
        String[] kinds = {"Algebra worksheet", "Reading list", "Lab notes"};
        for (int i = 0; i < p.clean(); i++) {
            Sub sub = subs.get(i % subs.size());
            all.add(file(site, sub, kinds[i % kinds.length] + " " + (i / subs.size() + 1) + ".docx", "Academic/" + sub.subject().getName(),
                    DriveType.SHARED, "Policy", Sensitivity.INTERNAL, false).getId());
        }
        for (int i = 0; i < p.personal(); i++) {
            Sub sub = subs.get(i % subs.size());
            all.add(file(site, sub, "Class contacts " + (i + 1) + ".xlsx", null, DriveType.PERSONAL, "Contact list", Sensitivity.RESTRICTED, true).getId());
        }
        for (int i = 0; i < p.publicLink(); i++) {
            Sub sub = subs.get(i % subs.size());
            SchoolFile book = file(site, sub, "Grade 8 marks " + (i + 1) + ".xlsx", sub.subject().getName(), DriveType.SHARED, "Mark book", Sensitivity.RESTRICTED, true);
            grant(book, ShareTargetType.ANYONE_WITH_LINK, null);                // urgent: public link
            all.add(book.getId());
        }
        for (UUID id : all) {
            engine.evaluateFile(id);
        }
        scores.snapshotToday(site);
    }

    // ------------------------------------------------------------------ helpers

    private Person person(Org org, Site site, String name, Role role, Subject subject) {
        Person p = new Person();
        p.setOrgId(org.getId());
        p.setSiteId(site.getId());
        p.setEmail(name.toLowerCase().replace("al-", "").replace(' ', '.') + "@" + site.getDomain());
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
