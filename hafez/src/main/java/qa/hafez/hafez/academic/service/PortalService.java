package qa.hafez.hafez.academic.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.academic.dto.AcademicDtos.LessonView;
import qa.hafez.hafez.academic.dto.AcademicDtos.SectionView;
import qa.hafez.hafez.academic.dto.AcademicDtos.StudentDashboard;
import qa.hafez.hafez.academic.dto.AcademicDtos.TeacherToday;
import qa.hafez.hafez.academic.model.Section;
import qa.hafez.hafez.academic.repository.EnrolmentRepository;
import qa.hafez.hafez.shared.exception.ForbiddenException;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.security.CurrentUser;
import qa.hafez.hafez.shared.util.SchoolCalendar;

/**
 * The home screens of the two portals. A teacher sees today's lessons and which sections still need attendance
 * (FR-024); a student sees today's timetable, notices, their own attendance, published results and section materials
 * (FR-042). Each part comes from the same services the other screens use, so the same access rules apply.
 */
@Service
@RequiredArgsConstructor
public class PortalService {

    private final AcademicSetupService setup;
    private final AttendanceService attendance;
    private final MarksService marks;
    private final NoticeService notices;
    private final MaterialService materials;
    private final EnrolmentRepository enrolments;
    private final AcademicAccess access;

    @Transactional(readOnly = true)
    public TeacherToday teacherToday(CurrentUser user) {
        if (!user.isAny(Role.TEACHER, Role.COORDINATOR)) {
            throw new ForbiddenException("This is the teacher's home.");
        }
        LocalDate today = access.today();
        boolean schoolDay = SchoolCalendar.isSchoolDay(today);
        int day = AcademicSetupService.dayIndex(today);
        List<LessonView> lessons = setup.timetable(user, null).stream().filter(l -> l.day() == day).toList();
        List<SectionView> sections = setup.sections(user);
        List<UUID> taken = sections.stream().filter(s -> attendance.taken(s.id(), today)).map(SectionView::id).toList();
        return new TeacherToday(today, schoolDay, lessons, sections, taken);
    }

    @Transactional(readOnly = true)
    public StudentDashboard studentDashboard(CurrentUser user) {
        if (!user.is(Role.STUDENT)) {
            throw new ForbiddenException("This is the student's home.");
        }
        LocalDate today = access.today();
        int day = AcademicSetupService.dayIndex(today);
        Section section = access.studentSection(user);
        List<LessonView> week = setup.timetable(user, null);
        int count = enrolments.findBySectionId(section.getId()).size();
        return new StudentDashboard(new SectionView(section.getId(), section.getGrade(), section.getName(), count), today,
                SchoolCalendar.isSchoolDay(today), week.stream().filter(l -> l.day() == day).toList(), week,
                notices.activeFor(user), attendance.mine(user), marks.myResults(user), materials.list(user, null));
    }
}
