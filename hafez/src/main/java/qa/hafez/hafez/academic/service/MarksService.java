package qa.hafez.hafez.academic.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.academic.dto.AcademicDtos.AssessmentView;
import qa.hafez.hafez.academic.dto.AcademicDtos.MarkInput;
import qa.hafez.hafez.academic.dto.AcademicDtos.MarkRow;
import qa.hafez.hafez.academic.dto.AcademicDtos.ResultRow;
import qa.hafez.hafez.academic.model.Assessment;
import qa.hafez.hafez.academic.model.Mark;
import qa.hafez.hafez.academic.model.ResultPublication;
import qa.hafez.hafez.academic.model.Section;
import qa.hafez.hafez.academic.model.TeachingAssignment;
import qa.hafez.hafez.academic.repository.AssessmentRepository;
import qa.hafez.hafez.academic.repository.MarkRepository;
import qa.hafez.hafez.academic.repository.ResultPublicationRepository;
import qa.hafez.hafez.academic.repository.TermRepository;
import qa.hafez.hafez.academic.repository.TeachingAssignmentRepository;
import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.shared.events.ActivityActions;
import qa.hafez.hafez.shared.events.AuditPublisher;
import qa.hafez.hafez.shared.events.PersonNotice;
import qa.hafez.hafez.shared.exception.BadRequestException;
import qa.hafez.hafez.shared.exception.ConflictException;
import qa.hafez.hafez.shared.exception.ForbiddenException;
import qa.hafez.hafez.shared.exception.NotFoundException;
import qa.hafez.hafez.shared.util.SchoolCalendar;
import qa.hafez.hafez.shared.model.NotificationKind;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.security.CurrentUser;

/**
 * Marks and results (SMS FR-028 to FR-033).
 *
 * <pre>
 *  Teacher defines an assessment →  teacher enters marks  →  teacher submits (locked)
 *                                       ↑                          │
 *                       Admin unlocks, with a reason  ←────────────┘
 *                                                                  ↓
 *                          Admin publishes a section's term  →  students see their results
 * </pre>
 * A mark is a number from 0 to the assessment's maximum, or Absent (BR-005); if any row is invalid nothing is saved and
 * every bad row is listed (FR-030). Submitted marks are read-only for the teacher (BR-006). Students see a term only
 * after it is published and only their own marks (BR-007, BR-012).
 */
@Service
@RequiredArgsConstructor
public class MarksService {

    private static final BigDecimal MAX_ALLOWED = new BigDecimal("1000");

    private final AssessmentRepository assessments;
    private final TermRepository terms;
    private final MarkRepository marks;
    private final ResultPublicationRepository publications;
    private final TeachingAssignmentRepository assignments;
    private final AcademicAccess access;
    private final AuditPublisher audit;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final qa.hafez.hafez.approvals.service.ApprovalService approvals;
    private final qa.hafez.hafez.academic.repository.EnrolmentRepository enrolments;
    private final qa.hafez.hafez.academic.repository.SectionRepository sectionRepo;
    private final qa.hafez.hafez.organisation.repository.SiteRepository siteRepo;
    private final qa.hafez.hafez.people.repository.PersonRepository peopleRepo;

    // ================================================================== defining

    @Transactional
    public AssessmentView create(CurrentUser user, UUID sectionId, UUID subjectId, int term, String name, BigDecimal maxMark) {
        Section section = access.section(user, sectionId);
        // The teacher of this subject for this section sets the assessments and enters the grades.
        if (!access.teaches(user, sectionId, subjectId)) {
            throw new ForbiddenException("Only the teacher of this subject for this section can create its assessments.");
        }
        if (term < 1 || term > 3) {
            throw new BadRequestException("The term must be 1, 2 or 3.");
        }
        if (terms.findBySiteIdAndNumber(user.siteId(), term).isEmpty()) {
            throw new BadRequestException("Term " + term + " has not been created yet. Ask the coordinator to create it.");
        }
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Please name the assessment.");
        }
        if (maxMark == null || maxMark.signum() <= 0 || maxMark.compareTo(MAX_ALLOWED) > 0) {
            throw new BadRequestException("The maximum mark must be more than 0 and at most 1000.");
        }
        assignments.findBySectionIdAndSubjectId(sectionId, subjectId)
                .orElseThrow(() -> new BadRequestException("Assign a teacher to this subject for the section first."));
        if (publications.findBySectionIdAndTerm(sectionId, term).isPresent()) {
            throw new ConflictException("Term " + term + " of section " + AcademicAccess.label(section)
                    + " is already published. Unpublish it to add an assessment.");
        }
        Assessment a = new Assessment();
        a.setOrgId(user.orgId());
        a.setSiteId(user.siteId());
        a.setSectionId(sectionId);
        a.setSubjectId(subjectId);
        a.setTerm(term);
        a.setName(name.trim());
        a.setMaxMark(maxMark);
        a.setWeekStart(SchoolCalendar.weekStart(access.today()));
        assessments.save(a);
        audit.log(user.orgId(), user.siteId(), ActivityActions.ASSESSMENT_CREATED).by(user).on("Section", sectionId)
                .subject(subjectId).change(null, a.getName() + " (term " + term + ", max " + maxMark.stripTrailingZeros().toPlainString() + ")").save();
        return view(a, access.sectionsById(user.siteId()), access.subjectNames(user.siteId()), false);
    }

    /** Assessments the user may see: school staff for the school, a teacher only for the sections and subjects they teach. */
    @Transactional(readOnly = true)
    public List<AssessmentView> list(CurrentUser user, UUID sectionId) {
        if (user.isAny(Role.STUDENT, Role.PARENT, Role.LEADERSHIP, Role.EAA_ADMIN)) {
            throw new ForbiddenException("Assessments are not available for your role.");
        }
        List<Assessment> rows;
        if (sectionId != null) {
            access.requireView(user, sectionId);
            rows = assessments.findBySectionIdOrderByTermAscNameAsc(sectionId);
        } else {
            rows = assessments.findBySiteIdOrderByTermAscNameAsc(user.siteId());
        }
        if (user.isAny(Role.TEACHER, Role.COORDINATOR)) {
            rows = rows.stream().filter(a -> access.teaches(user, a.getSectionId(), a.getSubjectId())).toList();
        }
        Map<UUID, Section> sectionById = access.sectionsById(user.siteId());
        Map<UUID, String> subjectNames = access.subjectNames(user.siteId());
        List<AssessmentView> out = new ArrayList<>();
        for (Assessment a : rows) {
            out.add(view(a, sectionById, subjectNames, publications.findBySectionIdAndTerm(a.getSectionId(), a.getTerm()).isPresent()));
        }
        return out;
    }

    // ================================================================== entering

    /** Every student of the section with their mark so far. School IT may look; only the teacher enters. */
    @Transactional
    public List<MarkRow> sheet(CurrentUser user, UUID assessmentId) {
        Assessment a = find(user, assessmentId);
        if (!user.is(Role.SCHOOL_IT) && !access.teaches(user, a.getSectionId(), a.getSubjectId())) {
            throw new ForbiddenException("You do not teach this subject to this section.");
        }
        audit.log(user.orgId(), user.siteId(), ActivityActions.STUDENT_RECORD_VIEWED).by(user).on("Section", a.getSectionId())
                .change(null, "marks sheet").save();
        Map<UUID, Mark> existing = new HashMap<>();
        marks.findByAssessmentId(assessmentId).forEach(m -> existing.put(m.getStudentId(), m));
        List<MarkRow> rows = new ArrayList<>();
        for (Person student : access.students(a.getSectionId())) {
            Mark m = existing.get(student.getId());
            rows.add(new MarkRow(student.getId(), student.getDisplayName(), m == null ? null : m.getValue(), m != null && m.isAbsent()));
        }
        return rows;
    }

    /**
     * Saves marks for the listed students. All or nothing: if any row is invalid, nothing is saved and each bad row is
     * named (FR-030). Refused once the marks are submitted.
     */
    @Transactional
    public int enter(CurrentUser user, UUID assessmentId, List<MarkInput> inputs) {
        Assessment a = find(user, assessmentId);
        requireTeacher(user, a);
        if (a.getSubmittedAt() != null) {
            throw new ConflictException("These marks are submitted and locked. Ask School IT to unlock them to make a change.");
        }
        Map<UUID, Person> students = new HashMap<>();
        access.students(a.getSectionId()).forEach(p -> students.put(p.getId(), p));

        List<String> problems = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (MarkInput in : inputs) {
            Person student = students.get(in.studentId());
            String who = student == null ? "An unknown student" : student.getDisplayName();
            if (student == null) {
                problems.add(who + " is not in this section");
            } else if (!seen.add(in.studentId())) {
                problems.add(who + " is listed twice");
            } else if (in.absent() && in.value() != null) {
                problems.add(who + ": choose either a mark or Absent, not both");
            } else if (!in.absent() && in.value() == null) {
                problems.add(who + ": enter a mark or tick Absent");
            } else if (!in.absent() && (in.value().signum() < 0 || in.value().compareTo(a.getMaxMark()) > 0)) {
                problems.add(who + ": the mark must be from 0 to " + a.getMaxMark().stripTrailingZeros().toPlainString());
            }
        }
        if (!problems.isEmpty()) {
            throw new BadRequestException("Nothing was saved. " + String.join("; ", problems) + ".");
        }

        Map<UUID, Mark> existing = new HashMap<>();
        marks.findByAssessmentId(assessmentId).forEach(m -> existing.put(m.getStudentId(), m));
        int changed = 0;
        for (MarkInput in : inputs) {
            Mark m = existing.get(in.studentId());
            String before = m == null ? null : describe(m);
            Mark mark = m == null ? new Mark() : m;
            mark.setOrgId(user.orgId());
            mark.setSiteId(user.siteId());
            mark.setAssessmentId(assessmentId);
            mark.setStudentId(in.studentId());
            mark.setAbsent(in.absent());
            mark.setValue(in.absent() ? null : in.value());
            String after = describe(mark);
            if (after.equals(before)) {
                continue;
            }
            marks.save(mark);
            changed++;
            audit.log(user.orgId(), user.siteId(), ActivityActions.MARK_ENTERED).by(user).on("Person", in.studentId())
                    .subject(a.getSubjectId()).change(before, a.getName() + ": " + after).save();
        }
        return changed;
    }

    /** Locks the marks. Every student needs a mark or Absent first. */
    @Transactional
    public void submit(CurrentUser user, UUID assessmentId) {
        Assessment a = find(user, assessmentId);
        requireTeacher(user, a);
        if (a.getSubmittedAt() != null) {
            throw new ConflictException("These marks are already submitted.");
        }
        int students = access.students(a.getSectionId()).size();
        long entered = marks.findByAssessmentId(assessmentId).size();
        if (students == 0) {
            throw new ConflictException("There are no students in this section yet.");
        }
        if (entered < students) {
            throw new ConflictException((students - entered) + " student(s) have no mark yet. Enter a mark or Absent for everyone, then submit.");
        }
        a.setSubmittedAt(Instant.now(clock));
        audit.log(user.orgId(), user.siteId(), ActivityActions.MARKS_SUBMITTED).by(user).on("Section", a.getSectionId())
                .subject(a.getSubjectId()).change("open", "submitted: " + a.getName()).save();
    }

    /** The teacher of the subject reopens their own submitted marks for correction. The reason is logged (BR-006). */
    @Transactional
    public void unlock(CurrentUser user, UUID assessmentId, String reason) {
        Assessment a = find(user, assessmentId);
        requireTeacher(user, a);
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Please write the reason for unlocking the marks.");
        }
        if (a.getSubmittedAt() == null) {
            throw new ConflictException("These marks are not submitted, so there is nothing to unlock.");
        }
        if (publications.findBySectionIdAndTerm(a.getSectionId(), a.getTerm()).isPresent()) {
            throw new ConflictException("The results of this term are published. Unpublish them first, then unlock the marks.");
        }
        a.setSubmittedAt(null);
        audit.log(user.orgId(), user.siteId(), ActivityActions.MARKS_UNLOCKED).by(user).on("Section", a.getSectionId())
                .subject(a.getSubjectId()).change("submitted", "unlocked: " + reason.trim()).save();
    }

    // ================================================================== publishing

    /**
     * A teacher of the section asks to make its term visible to the students. Every assessment of that term must be
     * submitted first. Four-eyes: nothing is published until a second person (principal, coordinator or School IT)
     * approves it.
     */
    @Transactional
    public void publish(CurrentUser user, UUID sectionId, int term) {
        Section section = access.requireTeacherOf(user, sectionId);
        checkPublishable(sectionId, term, section);
        approvals.request(user, user.siteId(), qa.hafez.hafez.approvals.ApprovalActions.MARKS_PUBLISH, sectionId,
                String.valueOf(term), "Publish term " + term + " results of section " + AcademicAccess.label(section));
    }

    /** Whether a second person from this school may approve the publication (never the requester; checked elsewhere). */
    public boolean sameSchool(CurrentUser user, UUID sectionId) {
        return sectionRepo.findById(sectionId).map(s -> s.getSiteId().equals(user.siteId())).orElse(false);
    }

    /** Publishes after approval (called by the approval handler). */
    @Transactional
    public void executePublish(CurrentUser approver, UUID sectionId, int term) {
        Section section = sectionRepo.findById(sectionId).orElseThrow(() -> new NotFoundException("The section"));
        checkPublishable(sectionId, term, section);
        CurrentUser user = approver;
        publishNow(user, section, term);
    }

    private void checkPublishable(UUID sectionId, int term, Section section) {
        List<Assessment> ofTerm = assessments.findBySectionIdAndTerm(sectionId, term);
        if (ofTerm.isEmpty()) {
            throw new ConflictException("There are no assessments for term " + term + " of section " + AcademicAccess.label(section) + ".");
        }
        List<String> open = ofTerm.stream().filter(a -> a.getSubmittedAt() == null).map(Assessment::getName).toList();
        if (!open.isEmpty()) {
            throw new ConflictException("Marks are not submitted yet for: " + String.join(", ", open) + ".");
        }
        if (publications.findBySectionIdAndTerm(sectionId, term).isPresent()) {
            throw new ConflictException("These results are already published.");
        }
    }

    private void publishNow(CurrentUser user, Section section, int term) {
        UUID sectionId = section.getId();
        ResultPublication p = new ResultPublication();
        p.setOrgId(section.getOrgId());
        p.setSiteId(section.getSiteId());
        p.setSectionId(sectionId);
        p.setTerm(term);
        p.setPublishedAt(Instant.now(clock));
        p.setPublishedBy(user.personId());
        publications.save(p);
        audit.log(user.orgId(), user.siteId(), ActivityActions.RESULTS_PUBLISHED).by(user).on("Section", sectionId)
                .change(null, "term " + term).save();
        for (Person student : access.students(sectionId)) {
            publisher.publishEvent(new PersonNotice(student.getId(), NotificationKind.SYSTEM, "نتائجك منشورة الآن",
                    "Your results are published", "Term " + term));
        }
    }

    /** Hides a published term again. A reason is required and logged (BR-007). */
    @Transactional
    public void unpublish(CurrentUser user, UUID sectionId, int term, String reason) {
        access.requireTeacherOf(user, sectionId);
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Please write the reason for unpublishing the results.");
        }
        ResultPublication p = publications.findBySectionIdAndTerm(sectionId, term)
                .orElseThrow(() -> new NotFoundException("The published results"));
        publications.delete(p);
        audit.log(user.orgId(), user.siteId(), ActivityActions.RESULTS_UNPUBLISHED).by(user).on("Section", sectionId)
                .change("term " + term + " published", "unpublished: " + reason.trim()).save();
    }

    /** A student's own published results. Terms that are not published do not appear at all. */
    @Transactional(readOnly = true)
    public List<ResultRow> myResults(CurrentUser user) {
        Section section = access.studentSection(user);
        Set<Integer> published = new HashSet<>();
        publications.findBySectionId(section.getId()).forEach(p -> published.add(p.getTerm()));
        Map<UUID, Mark> mine = new HashMap<>();
        marks.findByStudentId(user.personId()).forEach(m -> mine.put(m.getAssessmentId(), m));
        Map<UUID, String> subjectNames = access.subjectNames(user.siteId());
        List<ResultRow> rows = new ArrayList<>();
        for (Assessment a : assessments.findBySectionIdOrderByTermAscNameAsc(section.getId())) {
            Mark m = mine.get(a.getId());
            if (published.contains(a.getTerm()) && m != null) {
                rows.add(new ResultRow(a.getTerm(), subjectNames.getOrDefault(a.getSubjectId(), ""), a.getName(),
                        m.getValue(), a.getMaxMark(), m.isAbsent()));
            }
        }
        return rows;
    }

    // ================================================================== helpers

    private Assessment find(CurrentUser user, UUID id) {
        return assessments.findById(id).filter(a -> a.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new NotFoundException("The assessment"));
    }

    private void requireTeacher(CurrentUser user, Assessment a) {
        if (!access.teaches(user, a.getSectionId(), a.getSubjectId())) {
            throw new ForbiddenException("Only the teacher of this subject for this section can enter or submit marks.");
        }
    }

    // ================================================================== weekly submissions

    /** Who has submitted this week's grades, per teacher, section and subject. */
    @Transactional(readOnly = true)
    public List<qa.hafez.hafez.academic.dto.AcademicDtos.WeeklyRow> weekly(CurrentUser user, java.time.LocalDate week) {
        if (!user.isAny(Role.SCHOOL_IT, Role.PRINCIPAL, Role.COORDINATOR, Role.LEADERSHIP, Role.EAA_ADMIN)) {
            throw new ForbiddenException("The weekly grade overview is for school leadership.");
        }
        java.time.LocalDate start = SchoolCalendar.weekStart(week == null ? access.today() : week);
        boolean overdue = access.today().isAfter(start.plusDays(4)); // the school week runs Sunday to Thursday
        List<UUID> siteIds = user.isNetwork()
                ? siteRepo.findByOrgIdOrderByName(user.orgId()).stream().filter(s -> s.isActive() && !s.isHeadOffice()).map(s -> s.getId()).toList()
                : List.of(user.siteId());
        List<qa.hafez.hafez.academic.dto.AcademicDtos.WeeklyRow> out = new ArrayList<>();
        for (UUID siteId : siteIds) {
            String school = siteRepo.findById(siteId).map(s -> s.getName()).orElse("");
            Map<UUID, Section> sectionById = access.sectionsById(siteId);
            Map<UUID, String> subjectNames = access.subjectNames(siteId);
            List<Assessment> all = assessments.findBySiteIdOrderByTermAscNameAsc(siteId);
            for (var ta : assignments.findBySiteId(siteId)) {
                List<Assessment> mine = all.stream().filter(a -> a.getSectionId().equals(ta.getSectionId())
                        && a.getSubjectId().equals(ta.getSubjectId()) && start.equals(a.getWeekStart())).toList();
                int submitted = (int) mine.stream().filter(a -> a.getSubmittedAt() != null).count();
                String status = mine.isEmpty() ? (overdue ? "OVERDUE" : "NOT_STARTED")
                        : submitted == mine.size() ? "DONE" : (overdue ? "OVERDUE" : "IN_PROGRESS");
                String teacher = peopleRepo.findById(ta.getTeacherId()).map(p -> p.getDisplayName()).orElse("");
                out.add(new qa.hafez.hafez.academic.dto.AcademicDtos.WeeklyRow(school,
                        AcademicAccess.label(sectionById.get(ta.getSectionId())), subjectNames.getOrDefault(ta.getSubjectId(), ""),
                        teacher, mine.size(), submitted, status));
            }
        }
        return out;
    }

    // ================================================================== performance matrix and stage

    /** Students of a section against every assessment: the centralised performance matrix. */
    @Transactional
    public qa.hafez.hafez.academic.dto.AcademicDtos.MatrixView matrix(CurrentUser user, UUID sectionId) {
        if (user.isAny(Role.STUDENT, Role.PARENT, Role.LEADERSHIP, Role.EAA_ADMIN, Role.ATTENDANCE_OFFICER)) {
            throw new ForbiddenException("The performance matrix is for school staff.");
        }
        access.requireView(user, sectionId);
        List<Assessment> columns = assessments.findBySectionIdOrderByTermAscNameAsc(sectionId).stream()
                .filter(a -> user.isAny(Role.SCHOOL_IT, Role.PRINCIPAL) || access.teaches(user, sectionId, a.getSubjectId())
                        || (user.is(Role.COORDINATOR) && access.teaches(user, sectionId)))
                .sorted(java.util.Comparator.comparing(Assessment::getWeekStart).thenComparing(Assessment::getName)).toList();
        Map<UUID, Map<UUID, Mark>> byAssessment = new HashMap<>();
        for (Assessment a : columns) {
            Map<UUID, Mark> m = new HashMap<>();
            marks.findByAssessmentId(a.getId()).forEach(x -> m.put(x.getStudentId(), x));
            byAssessment.put(a.getId(), m);
        }
        Map<UUID, qa.hafez.hafez.academic.model.Stage> stages = new HashMap<>();
        enrolments.findBySectionId(sectionId).forEach(e -> stages.put(e.getStudentId(), e.getStage()));
        List<qa.hafez.hafez.academic.dto.AcademicDtos.MatrixRow> rows = new ArrayList<>();
        for (Person student : access.students(sectionId)) {
            List<BigDecimal> values = new ArrayList<>();
            BigDecimal pct = BigDecimal.ZERO;
            int counted = 0;
            for (Assessment a : columns) {
                Mark m = byAssessment.get(a.getId()).get(student.getId());
                BigDecimal v = m == null || m.isAbsent() ? null : m.getValue();
                values.add(v);
                if (v != null) {
                    pct = pct.add(v.multiply(BigDecimal.valueOf(100)).divide(a.getMaxMark(), 2, java.math.RoundingMode.HALF_UP));
                    counted++;
                }
            }
            rows.add(new qa.hafez.hafez.academic.dto.AcademicDtos.MatrixRow(student.getId(), student.getDisplayName(),
                    stages.getOrDefault(student.getId(), qa.hafez.hafez.academic.model.Stage.SIJIL).name(), values,
                    counted == 0 ? null : pct.divide(BigDecimal.valueOf(counted), 1, java.math.RoundingMode.HALF_UP)));
        }
        audit.log(user.orgId(), user.siteId(), ActivityActions.STUDENT_RECORD_VIEWED).by(user).on("Section", sectionId)
                .change(null, "performance matrix").save();
        return new qa.hafez.hafez.academic.dto.AcademicDtos.MatrixView(columns.stream()
                .map(a -> new qa.hafez.hafez.academic.dto.AcademicDtos.MatrixColumn(a.getId(), a.getName(), a.getTerm(), a.getWeekStart(), a.getMaxMark())).toList(), rows);
    }

    /** The coordinator moves a student from Sijil to Hifz (or back). Audited. */
    @Transactional
    public void setStage(CurrentUser user, UUID studentId, String stage) {
        access.requireCoordinator(user);
        qa.hafez.hafez.academic.model.Stage wanted;
        try {
            wanted = qa.hafez.hafez.academic.model.Stage.valueOf(stage);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("The stage must be SIJIL or HIFZ.");
        }
        var e = enrolments.findByStudentId(studentId).filter(x -> x.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new NotFoundException("The student"));
        String before = e.getStage().name();
        e.setStage(wanted);
        enrolments.save(e);
        audit.log(user.orgId(), user.siteId(), "STUDENT_STAGE_CHANGED").by(user).on("Person", studentId).change(before, wanted.name()).save();
    }

    private AssessmentView view(Assessment a, Map<UUID, Section> sectionById, Map<UUID, String> subjectNames, boolean published) {
        return new AssessmentView(a.getId(), a.getSectionId(), AcademicAccess.label(sectionById.get(a.getSectionId())),
                a.getSubjectId(), subjectNames.getOrDefault(a.getSubjectId(), ""), a.getTerm(), a.getName(), a.getMaxMark(),
                a.getSubmittedAt() != null, published, a.getWeekStart());
    }

    private static String describe(Mark m) {
        return m.isAbsent() ? "absent" : m.getValue() == null ? "" : m.getValue().stripTrailingZeros().toPlainString();
    }
}
