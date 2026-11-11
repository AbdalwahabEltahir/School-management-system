package qa.sijill.sijill.academic.service;

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

import qa.sijill.sijill.academic.dto.AcademicDtos.AssessmentView;
import qa.sijill.sijill.academic.dto.AcademicDtos.MarkInput;
import qa.sijill.sijill.academic.dto.AcademicDtos.MarkRow;
import qa.sijill.sijill.academic.dto.AcademicDtos.ResultRow;
import qa.sijill.sijill.academic.model.Assessment;
import qa.sijill.sijill.academic.model.Mark;
import qa.sijill.sijill.academic.model.ResultPublication;
import qa.sijill.sijill.academic.model.Section;
import qa.sijill.sijill.academic.model.TeachingAssignment;
import qa.sijill.sijill.academic.repository.AssessmentRepository;
import qa.sijill.sijill.academic.repository.MarkRepository;
import qa.sijill.sijill.academic.repository.ResultPublicationRepository;
import qa.sijill.sijill.academic.repository.TermRepository;
import qa.sijill.sijill.academic.repository.TeachingAssignmentRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.events.PersonNotice;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

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
        assessments.save(a);
        audit.log(user.orgId(), user.siteId(), ActivityActions.ASSESSMENT_CREATED).by(user).on("Section", sectionId)
                .subject(subjectId).change(null, a.getName() + " (term " + term + ", max " + maxMark.stripTrailingZeros().toPlainString() + ")").save();
        return view(a, access.sectionsById(user.siteId()), access.subjectNames(user.siteId()), false);
    }

    /** Assessments the user may see: school staff for the school, a teacher only for the sections and subjects they teach. */
    @Transactional(readOnly = true)
    public List<AssessmentView> list(CurrentUser user, UUID sectionId) {
        if (user.isAny(Role.STUDENT, Role.PARENT, Role.LEADERSHIP)) {
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
    @Transactional(readOnly = true)
    public List<MarkRow> sheet(CurrentUser user, UUID assessmentId) {
        Assessment a = find(user, assessmentId);
        if (!user.is(Role.SCHOOL_IT) && !access.teaches(user, a.getSectionId(), a.getSubjectId())) {
            throw new ForbiddenException("You do not teach this subject to this section.");
        }
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

    /** A teacher of the section makes its term visible to the students. Every assessment of that term must be submitted first. */
    @Transactional
    public void publish(CurrentUser user, UUID sectionId, int term) {
        Section section = access.requireTeacherOf(user, sectionId);
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
        ResultPublication p = new ResultPublication();
        p.setOrgId(user.orgId());
        p.setSiteId(user.siteId());
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

    private AssessmentView view(Assessment a, Map<UUID, Section> sectionById, Map<UUID, String> subjectNames, boolean published) {
        return new AssessmentView(a.getId(), a.getSectionId(), AcademicAccess.label(sectionById.get(a.getSectionId())),
                a.getSubjectId(), subjectNames.getOrDefault(a.getSubjectId(), ""), a.getTerm(), a.getName(), a.getMaxMark(),
                a.getSubmittedAt() != null, published);
    }

    private static String describe(Mark m) {
        return m.isAbsent() ? "absent" : m.getValue() == null ? "" : m.getValue().stripTrailingZeros().toPlainString();
    }
}
