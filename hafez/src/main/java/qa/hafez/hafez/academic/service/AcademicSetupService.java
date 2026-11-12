package qa.hafez.hafez.academic.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.academic.dto.AcademicDtos.AssignmentView;
import qa.hafez.hafez.academic.dto.AcademicDtos.LessonView;
import qa.hafez.hafez.academic.dto.AcademicDtos.SectionView;
import qa.hafez.hafez.academic.dto.AcademicDtos.StudentView;
import qa.hafez.hafez.academic.dto.AcademicDtos.TermView;
import qa.hafez.hafez.academic.model.Enrolment;
import qa.hafez.hafez.academic.model.Section;
import qa.hafez.hafez.academic.model.TeachingAssignment;
import qa.hafez.hafez.academic.model.Term;
import qa.hafez.hafez.academic.model.TimetableEntry;
import qa.hafez.hafez.academic.repository.EnrolmentRepository;
import qa.hafez.hafez.academic.repository.SectionRepository;
import qa.hafez.hafez.academic.repository.TeachingAssignmentRepository;
import qa.hafez.hafez.academic.repository.TermRepository;
import qa.hafez.hafez.academic.repository.TimetableEntryRepository;
import qa.hafez.hafez.organisation.model.Subject;
import qa.hafez.hafez.organisation.repository.SubjectRepository;
import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.people.repository.PersonRepository;
import qa.hafez.hafez.shared.events.ActivityActions;
import qa.hafez.hafez.shared.events.AuditPublisher;
import qa.hafez.hafez.shared.exception.BadRequestException;
import qa.hafez.hafez.shared.exception.ConflictException;
import qa.hafez.hafez.shared.exception.ForbiddenException;
import qa.hafez.hafez.shared.exception.NotFoundException;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.security.CurrentUser;

/**
 * The school's academic structure (SMS FR-017 to FR-021): sections, who is enrolled in them, who teaches what, and
 * the weekly timetable. The coordinator sets it up; teachers and students read their own part.
 *
 * <p>Rules enforced here: a student is in one section (BR-002); a section, a teacher or a room can be in only one
 * lesson per period and a refused save names the clash (BR-003); school days are Sunday to Thursday (BR-004).
 */
@Service
@RequiredArgsConstructor
public class AcademicSetupService {

    private static final String[] DAYS = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday"};

    private final SectionRepository sections;
    private final TermRepository terms;
    private final EnrolmentRepository enrolments;
    private final TeachingAssignmentRepository assignments;
    private final TimetableEntryRepository timetable;
    private final PersonRepository people;
    private final SubjectRepository subjects;
    private final AcademicAccess access;
    private final AuditPublisher audit;

    // ================================================================== sections and enrolment

    @Transactional
    public SectionView createSection(CurrentUser user, int grade, String name) {
        access.requireCoordinator(user);
        if (grade < 1 || grade > 12 || name == null || name.isBlank()) {
            throw new BadRequestException("Please give a grade from 1 to 12 and a section name.");
        }
        String clean = name.trim();
        if (sections.findBySiteIdAndGradeAndName(user.siteId(), grade, clean).isPresent()) {
            throw new ConflictException("Section " + grade + clean + " already exists.");
        }
        Section section = new Section();
        section.setOrgId(user.orgId());
        section.setSiteId(user.siteId());
        section.setGrade(grade);
        section.setName(clean);
        sections.save(section);
        audit.log(user.orgId(), user.siteId(), ActivityActions.SECTION_CREATED).by(user).on("Section", section.getId())
                .change(null, AcademicAccess.label(section)).save();
        return new SectionView(section.getId(), grade, clean, 0);
    }

    /** The sections the user may see: all for School IT, principal and coordinator; theirs for a teacher or student. */
    @Transactional(readOnly = true)
    public List<SectionView> sections(CurrentUser user) {
        if (user.is(Role.LEADERSHIP)) {
            throw new ForbiddenException("Sections are not available for your role.");
        }
        List<Section> all = sections.findBySiteIdOrderByGradeAscNameAsc(user.siteId());
        List<Section> visible = switch (user.role()) {
            case SCHOOL_IT, PRINCIPAL, COORDINATOR -> all;
            case TEACHER -> all.stream().filter(s -> access.teaches(user, s.getId())).toList();
            case STUDENT -> {
                UUID own = enrolments.findByStudentId(user.personId()).map(Enrolment::getSectionId).orElse(null);
                yield all.stream().filter(s -> s.getId().equals(own)).toList();
            }
            case LEADERSHIP, PARENT -> List.of();
        };
        Map<UUID, Long> counts = enrolments.findBySiteId(user.siteId()).stream()
                .collect(Collectors.groupingBy(Enrolment::getSectionId, Collectors.counting()));
        return visible.stream().map(s -> new SectionView(s.getId(), s.getGrade(), s.getName(),
                counts.getOrDefault(s.getId(), 0L).intValue())).toList();
    }

    // ================================================================== terms

    /** Creates or updates term 1, 2 or 3 of the school year. Only the coordinator does this (FR-016). */
    @Transactional
    public TermView saveTerm(CurrentUser user, int number, String name, LocalDate startsOn, LocalDate endsOn) {
        access.requireCoordinator(user);
        if (number < 1 || number > 3) {
            throw new BadRequestException("The term must be 1, 2 or 3.");
        }
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Please name the term.");
        }
        if (startsOn == null || endsOn == null || !endsOn.isAfter(startsOn)) {
            throw new BadRequestException("The term must end after it starts.");
        }
        Term term = terms.findBySiteIdAndNumber(user.siteId(), number).orElseGet(Term::new);
        String before = term.getName() == null ? null : term.getName() + " " + term.getStartsOn() + " to " + term.getEndsOn();
        term.setOrgId(user.orgId());
        term.setSiteId(user.siteId());
        term.setNumber(number);
        term.setName(name.trim());
        term.setStartsOn(startsOn);
        term.setEndsOn(endsOn);
        terms.save(term);
        audit.log(user.orgId(), user.siteId(), ActivityActions.TERM_SAVED).by(user).on("Term", term.getId())
                .change(before, term.getName() + " " + startsOn + " to " + endsOn).save();
        return termView(term);
    }

    /** The school's terms, for every signed-in school role. */
    @Transactional(readOnly = true)
    public List<TermView> terms(CurrentUser user) {
        if (user.is(Role.LEADERSHIP)) {
            throw new ForbiddenException("Terms are not available for your role.");
        }
        return terms.findBySiteIdOrderByNumberAsc(user.siteId()).stream().map(AcademicSetupService::termView).toList();
    }

    private static TermView termView(Term t) {
        return new TermView(t.getId(), t.getNumber(), t.getName(), t.getStartsOn(), t.getEndsOn());
    }

    /** Puts a student in a section, moving them if they were in another one (BR-002). */
    @Transactional
    public StudentView enrol(CurrentUser user, UUID sectionId, String studentEmail) {
        access.requireCoordinator(user);
        Section section = access.section(user, sectionId);
        Person student = people.findByEmailIgnoreCase(studentEmail == null ? "" : studentEmail.trim())
                .filter(p -> p.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new NotFoundException("A person with this e-mail in your school"));
        if (student.getRole() != Role.STUDENT) {
            throw new BadRequestException("Only a student can be enrolled in a section.");
        }
        Enrolment enrolment = enrolments.findByStudentId(student.getId()).orElseGet(Enrolment::new);
        String before = enrolment.getSectionId() == null ? null
                : AcademicAccess.label(access.section(user, enrolment.getSectionId()));
        enrolment.setOrgId(user.orgId());
        enrolment.setSiteId(user.siteId());
        enrolment.setSectionId(section.getId());
        enrolment.setStudentId(student.getId());
        enrolments.save(enrolment);
        audit.log(user.orgId(), user.siteId(), ActivityActions.STUDENT_ENROLLED).by(user).on("Person", student.getId())
                .change(before, AcademicAccess.label(section)).save();
        return new StudentView(student.getId(), student.getDisplayName(), student.getEmail());
    }

    /** The students of a section. Not available to students (they never see their classmates). */
    @Transactional(readOnly = true)
    public List<StudentView> roster(CurrentUser user, UUID sectionId) {
        if (user.isAny(Role.STUDENT, Role.PARENT)) {
            throw new ForbiddenException("Students and parents cannot see the class list.");
        }
        access.requireView(user, sectionId);
        return access.students(sectionId).stream().map(p -> new StudentView(p.getId(), p.getDisplayName(), p.getEmail())).toList();
    }

    // ================================================================== who teaches what

    /** Sets the teacher of a subject for a section. Moving it to another teacher also moves its timetable lessons. */
    @Transactional
    public AssignmentView assign(CurrentUser user, UUID sectionId, UUID subjectId, UUID teacherId) {
        access.requireCoordinator(user);
        access.section(user, sectionId);
        Subject subject = subjects.findById(subjectId).filter(s -> s.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new BadRequestException("Please choose one of your school's subjects."));
        Person teacher = people.findById(teacherId).filter(p -> p.getSiteId().equals(user.siteId()))
                .filter(p -> p.getRole() == Role.TEACHER || p.getRole() == Role.COORDINATOR)
                .orElseThrow(() -> new BadRequestException("Please choose a teacher of your school."));

        TeachingAssignment assignment = assignments.findBySectionIdAndSubjectId(sectionId, subjectId).orElseGet(TeachingAssignment::new);
        UUID previous = assignment.getTeacherId();
        if (previous != null && !previous.equals(teacherId)) {
            for (TimetableEntry lesson : timetable.findBySectionIdOrderByDayOfWeekAscPeriodAsc(sectionId)) {
                if (lesson.getSubjectId().equals(subjectId)) {
                    timetable.findByTeacherIdAndDayOfWeekAndPeriod(teacherId, lesson.getDayOfWeek(), lesson.getPeriod()).ifPresent(busy -> {
                        throw new ConflictException(teacher.getDisplayName() + " already teaches in period " + lesson.getPeriod()
                                + " on " + DAYS[lesson.getDayOfWeek()] + ", so " + subject.getName() + " cannot move to them.");
                    });
                    lesson.setTeacherId(teacherId);
                }
            }
        }
        assignment.setOrgId(user.orgId());
        assignment.setSiteId(user.siteId());
        assignment.setSectionId(sectionId);
        assignment.setSubjectId(subjectId);
        assignment.setTeacherId(teacherId);
        assignments.save(assignment);
        audit.log(user.orgId(), user.siteId(), ActivityActions.TEACHER_ASSIGNED).by(user).on("Section", sectionId)
                .subject(subjectId).change(previous == null ? null : previous.toString(), teacher.getDisplayName()).save();
        return new AssignmentView(sectionId, subjectId, subject.getName(), teacherId, teacher.getDisplayName());
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> assignmentsOf(CurrentUser user, UUID sectionId) {
        if (user.isAny(Role.STUDENT, Role.PARENT, Role.LEADERSHIP)) {
            throw new ForbiddenException("Teaching assignments are not available for your role.");
        }
        access.requireView(user, sectionId);
        Map<UUID, String> subjectNames = access.subjectNames(user.siteId());
        List<TeachingAssignment> rows = assignments.findBySectionId(sectionId);
        Map<UUID, String> teachers = access.personNames(rows.stream().map(TeachingAssignment::getTeacherId).toList());
        return rows.stream().map(a -> new AssignmentView(sectionId, a.getSubjectId(), subjectNames.get(a.getSubjectId()),
                a.getTeacherId(), teachers.get(a.getTeacherId()))).toList();
    }

    // ================================================================== timetable

    /**
     * Adds a lesson to a section's week. The teacher is the one assigned to that subject. A save that would put the
     * section, the teacher or the room in two places in one period is refused and the message names the clash.
     */
    @Transactional
    public LessonView addLesson(CurrentUser user, UUID sectionId, UUID subjectId, int day, int period, String room) {
        access.requireCoordinator(user);
        Section section = access.section(user, sectionId);
        if (day < 0 || day >= DAYS.length) {
            throw new BadRequestException("School days are Sunday to Thursday.");
        }
        if (period < 1 || period > 12) {
            throw new BadRequestException("The period must be from 1 to 12.");
        }
        TeachingAssignment assignment = assignments.findBySectionIdAndSubjectId(sectionId, subjectId)
                .orElseThrow(() -> new BadRequestException("Assign a teacher to this subject for the section first."));
        String where = " in period " + period + " on " + DAYS[day] + ".";
        Map<UUID, String> subjectNames = access.subjectNames(user.siteId());

        timetable.findBySectionIdAndDayOfWeekAndPeriod(sectionId, day, period).ifPresent(t -> {
            throw new ConflictException("Section " + AcademicAccess.label(section) + " already has "
                    + subjectNames.get(t.getSubjectId()) + where);
        });
        timetable.findByTeacherIdAndDayOfWeekAndPeriod(assignment.getTeacherId(), day, period).ifPresent(t -> {
            throw new ConflictException(access.personNames(List.of(assignment.getTeacherId())).get(assignment.getTeacherId())
                    + " already teaches another section" + where);
        });
        String cleanRoom = room == null || room.isBlank() ? null : room.trim();
        if (cleanRoom != null) {
            timetable.findBySiteIdAndRoomIgnoreCaseAndDayOfWeekAndPeriod(user.siteId(), cleanRoom, day, period).ifPresent(t -> {
                throw new ConflictException("Room " + cleanRoom + " is already booked" + where);
            });
        }
        TimetableEntry entry = new TimetableEntry();
        entry.setOrgId(user.orgId());
        entry.setSiteId(user.siteId());
        entry.setSectionId(sectionId);
        entry.setSubjectId(subjectId);
        entry.setTeacherId(assignment.getTeacherId());
        entry.setDayOfWeek(day);
        entry.setPeriod(period);
        entry.setRoom(cleanRoom);
        timetable.save(entry);
        audit.log(user.orgId(), user.siteId(), ActivityActions.TIMETABLE_CHANGED).by(user).on("Section", sectionId)
                .subject(subjectId).change(null, "added " + DAYS[day] + " period " + period).save();
        return views(user.siteId(), List.of(entry)).get(0);
    }

    @Transactional
    public void removeLesson(CurrentUser user, UUID lessonId) {
        access.requireCoordinator(user);
        TimetableEntry entry = timetable.findById(lessonId).filter(t -> t.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new NotFoundException("The lesson"));
        timetable.delete(entry);
        audit.log(user.orgId(), user.siteId(), ActivityActions.TIMETABLE_CHANGED).by(user).on("Section", entry.getSectionId())
                .subject(entry.getSubjectId()).change("removed " + DAYS[entry.getDayOfWeek()] + " period " + entry.getPeriod(), null).save();
    }

    /**
     * The timetable the user is allowed to see (FR-021): a teacher's own lessons, a student's section, or, for school
     * staff, the section asked for.
     */
    @Transactional(readOnly = true)
    public List<LessonView> timetable(CurrentUser user, UUID sectionId) {
        List<TimetableEntry> entries;
        if (user.is(Role.STUDENT)) {
            entries = timetable.findBySectionIdOrderByDayOfWeekAscPeriodAsc(access.studentSection(user).getId());
        } else if (user.is(Role.TEACHER)) {
            entries = timetable.findByTeacherIdOrderByDayOfWeekAscPeriodAsc(user.personId());
        } else if (user.isAny(Role.SCHOOL_IT, Role.PRINCIPAL, Role.COORDINATOR)) {
            if (sectionId == null) {
                throw new BadRequestException("Choose a section.");
            }
            access.requireView(user, sectionId);
            entries = timetable.findBySectionIdOrderByDayOfWeekAscPeriodAsc(sectionId);
        } else {
            throw new ForbiddenException("The timetable is not available for your role.");
        }
        return views(user.siteId(), entries);
    }

    /** Lessons with the names of their section, subject and teacher. */
    List<LessonView> views(UUID siteId, List<TimetableEntry> entries) {
        Map<UUID, String> subjectNames = access.subjectNames(siteId);
        Map<UUID, Section> sectionById = access.sectionsById(siteId);
        Map<UUID, String> teachers = access.personNames(entries.stream().map(TimetableEntry::getTeacherId).toList());
        List<LessonView> out = new ArrayList<>();
        for (TimetableEntry t : entries) {
            out.add(new LessonView(t.getId(), t.getSectionId(), AcademicAccess.label(sectionById.get(t.getSectionId())),
                    t.getSubjectId(), subjectNames.getOrDefault(t.getSubjectId(), ""), t.getTeacherId(),
                    teachers.getOrDefault(t.getTeacherId(), ""), t.getDayOfWeek(), t.getPeriod(), t.getRoom()));
        }
        return out;
    }

    /** Index of today in the Sunday-based school week, or -1 on Friday and Saturday. */
    public static int dayIndex(java.time.LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SUNDAY -> 0;
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 3;
            case THURSDAY -> 4;
            default -> -1;
        };
    }

}
