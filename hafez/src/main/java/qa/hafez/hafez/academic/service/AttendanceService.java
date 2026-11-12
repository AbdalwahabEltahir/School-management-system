package qa.hafez.hafez.academic.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.academic.dto.AcademicDtos.AttendanceDay;
import qa.hafez.hafez.academic.dto.AcademicDtos.AttendanceSummary;
import qa.hafez.hafez.academic.dto.AcademicDtos.RosterRow;
import qa.hafez.hafez.academic.dto.AcademicDtos.StudentAttendance;
import qa.hafez.hafez.academic.model.AttendanceRecord;
import qa.hafez.hafez.academic.model.AttendanceStatus;
import qa.hafez.hafez.academic.model.Section;
import qa.hafez.hafez.academic.repository.AttendanceRecordRepository;
import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.shared.events.ActivityActions;
import qa.hafez.hafez.shared.events.AuditPublisher;
import qa.hafez.hafez.shared.exception.BadRequestException;
import qa.hafez.hafez.shared.exception.ForbiddenException;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.security.CurrentUser;
import qa.hafez.hafez.shared.util.SchoolCalendar;

/**
 * Attendance (SMS FR-024 to FR-027).
 *
 * <ul>
 *   <li>A teacher of the section records Present, Absent or Leave for each student; everyone defaults to Present
 *       (FR-025). Only school days (Sunday to Thursday) and never a future date.</li>
 *   <li>The teacher can record and correct only on that same school day (BR-008). Afterwards only School IT can
 *       change it, and must give a written reason. Every change is in the audit log (FR-049).</li>
 *   <li>A student sees only their own record (BR-012).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final int RECENT_DAYS = 10;

    private final AttendanceRecordRepository records;
    private final AcademicAccess access;
    private final AuditPublisher audit;

    /** Every student of the section with their status that day (present when nothing is recorded yet). */
    @Transactional
    public List<RosterRow> roster(CurrentUser user, UUID sectionId, LocalDate date) {
        requireAttendanceStaff(user, sectionId);
        audit.log(user.orgId(), user.siteId(), ActivityActions.STUDENT_RECORD_VIEWED).by(user).on("Section", sectionId)
                .change(null, "attendance " + date).save();
        Map<UUID, AttendanceStatus> recorded = new HashMap<>();
        records.findBySectionIdAndAttendanceDate(sectionId, date).forEach(r -> recorded.put(r.getStudentId(), r.getStatus()));
        List<RosterRow> rows = new ArrayList<>();
        for (Person student : access.students(sectionId)) {
            rows.add(new RosterRow(student.getId(), student.getDisplayName(),
                    recorded.getOrDefault(student.getId(), AttendanceStatus.PRESENT)));
        }
        return rows;
    }

    /** Whether attendance has been taken for the section that day. */
    @Transactional(readOnly = true)
    public boolean taken(UUID sectionId, LocalDate date) {
        return !records.findBySectionIdAndAttendanceDate(sectionId, date).isEmpty();
    }

    /**
     * Saves the statuses of a section for one day. {@code statuses} may list only the students to change; the others
     * keep their status (or default to Present the first time).
     *
     * @param reason required when School IT changes a past day
     */
    @Transactional
    public int record(CurrentUser user, UUID sectionId, LocalDate date, Map<UUID, AttendanceStatus> statuses, String reason) {
        Section section = requireAttendanceStaff(user, sectionId);
        LocalDate today = access.today();
        if (date.isAfter(today)) {
            throw new BadRequestException("Attendance cannot be recorded for a future day.");
        }
        if (!SchoolCalendar.isSchoolDay(date)) {
            throw new BadRequestException("Attendance is recorded on school days, Sunday to Thursday.");
        }
        boolean admin = user.is(Role.SCHOOL_IT);
        if (!admin && !date.equals(today)) {
            throw new ForbiddenException("Teachers can record attendance only on the same school day. "
                    + "After that, School IT can change it with a written reason.");
        }
        if (admin && !date.equals(today) && (reason == null || reason.isBlank())) {
            throw new BadRequestException("Please write the reason for changing attendance after the day.");
        }

        List<Person> students = access.students(sectionId);
        Map<UUID, Person> byId = new HashMap<>();
        students.forEach(s -> byId.put(s.getId(), s));
        for (UUID id : statuses.keySet()) {
            if (!byId.containsKey(id)) {
                throw new BadRequestException("Some of these students are not in section " + AcademicAccess.label(section) + ".");
            }
        }

        Map<UUID, AttendanceRecord> existing = new HashMap<>();
        records.findBySectionIdAndAttendanceDate(sectionId, date).forEach(r -> existing.put(r.getStudentId(), r));
        int changed = 0;
        for (Person student : students) {
            AttendanceStatus wanted = statuses.get(student.getId());
            AttendanceRecord current = existing.get(student.getId());
            if (wanted == null && current != null) {
                continue; // not mentioned: unchanged
            }
            if (wanted == null) {
                wanted = AttendanceStatus.PRESENT; // first time: everyone defaults to present
            }
            if (current != null && current.getStatus() == wanted) {
                continue;
            }
            AttendanceStatus before = current == null ? null : current.getStatus();
            AttendanceRecord rec = current == null ? new AttendanceRecord() : current;
            rec.setOrgId(user.orgId());
            rec.setSiteId(user.siteId());
            rec.setSectionId(sectionId);
            rec.setStudentId(student.getId());
            rec.setAttendanceDate(date);
            rec.setStatus(wanted);
            rec.setRecordedBy(user.personId());
            rec.setReason(reason == null || reason.isBlank() ? null : reason.trim());
            records.save(rec);
            changed++;
            if (before != null) { // a correction: who, when, old and new value (FR-049)
                audit.log(user.orgId(), user.siteId(), ActivityActions.ATTENDANCE_CHANGED).by(user)
                        .on("Person", student.getId()).change(date + " " + before, date + " " + wanted).save();
            }
        }
        audit.log(user.orgId(), user.siteId(), ActivityActions.ATTENDANCE_RECORDED).by(user).on("Section", sectionId)
                .change(null, date + ": " + changed + " of " + students.size() + " students saved").save();
        return changed;
    }

    /** A student's own totals and recent days. */
    @Transactional(readOnly = true)
    public AttendanceSummary mine(CurrentUser user) {
        if (!user.is(Role.STUDENT)) {
            throw new ForbiddenException("This is the student's own attendance.");
        }
        return summaryOf(user.personId());
    }

    AttendanceSummary summaryOf(UUID studentId) {
        List<AttendanceRecord> all = records.findByStudentIdOrderByAttendanceDateDesc(studentId);
        long present = all.stream().filter(r -> r.getStatus() == AttendanceStatus.PRESENT).count();
        long absent = all.stream().filter(r -> r.getStatus() == AttendanceStatus.ABSENT).count();
        long leave = all.stream().filter(r -> r.getStatus() == AttendanceStatus.LEAVE).count();
        List<AttendanceDay> recent = all.stream().limit(RECENT_DAYS)
                .map(r -> new AttendanceDay(r.getAttendanceDate(), r.getStatus())).toList();
        return new AttendanceSummary(present, absent, leave, recent);
    }

    /** The Admin's summary of a section over a date range (FR-027): counts per student. */
    @Transactional(readOnly = true)
    public List<StudentAttendance> summary(CurrentUser user, UUID sectionId, LocalDate from, LocalDate to) {
        access.requireView(user, sectionId);
        if (!user.isAny(Role.SCHOOL_IT, Role.PRINCIPAL, Role.COORDINATOR) && !access.teaches(user, sectionId)) {
            throw new ForbiddenException("Attendance summaries are not available for your role.");
        }
        if (from == null || to == null || to.isBefore(from)) {
            throw new BadRequestException("Please choose a start date and an end date after it.");
        }
        Map<UUID, long[]> totals = new HashMap<>();
        for (AttendanceRecord r : records.findBySectionIdAndAttendanceDateBetween(sectionId, from, to)) {
            long[] t = totals.computeIfAbsent(r.getStudentId(), k -> new long[3]);
            t[r.getStatus().ordinal()]++;
        }
        List<StudentAttendance> out = new ArrayList<>();
        for (Person student : access.students(sectionId)) {
            long[] t = totals.getOrDefault(student.getId(), new long[3]);
            out.add(new StudentAttendance(student.getId(), student.getDisplayName(), t[0], t[1], t[2]));
        }
        return out;
    }

    /** School IT, or a teacher of the section. Students and other roles are refused. */
    private Section requireAttendanceStaff(CurrentUser user, UUID sectionId) {
        if (user.is(Role.SCHOOL_IT)) {
            return access.section(user, sectionId);
        }
        return access.requireTeacherOf(user, sectionId);
    }
}
