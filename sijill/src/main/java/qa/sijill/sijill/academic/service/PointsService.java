package qa.sijill.sijill.academic.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.academic.dto.AcademicDtos;
import qa.sijill.sijill.academic.model.Enrolment;
import qa.sijill.sijill.academic.model.PointEntry;
import qa.sijill.sijill.academic.model.PointSource;
import qa.sijill.sijill.academic.repository.EnrolmentRepository;
import qa.sijill.sijill.academic.repository.PointEntryRepository;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.people.service.ParentLinks;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Recognition points. One point is added automatically for every day a student is present (and taken back if the day
 * is corrected); a teacher can add 1 to 10 points with a short reason. Students see their total and the reasons, teachers
 * see their class, school leadership sees the school-wide picture.
 */
@Service
@RequiredArgsConstructor
public class PointsService {

    private static final int MAX_MANUAL = 10;

    private final PointEntryRepository points;
    private final EnrolmentRepository enrolments;
    private final PersonRepository people;
    private final SiteRepository sites;
    private final ParentLinks parentLinks;
    private final AcademicAccess access;
    private final AuditPublisher audit;

    /** Called by attendance: present earns one point for the day, any other status takes it back. */
    @Transactional
    public void syncAttendance(UUID orgId, UUID siteId, UUID studentId, LocalDate day, boolean present) {
        var existing = points.findByStudentIdAndAwardedOnAndSource(studentId, day, PointSource.ATTENDANCE);
        if (present && existing.isEmpty()) {
            PointEntry p = new PointEntry();
            p.setOrgId(orgId);
            p.setSiteId(siteId);
            p.setStudentId(studentId);
            p.setAmount(1);
            p.setReason("Present at school");
            p.setSource(PointSource.ATTENDANCE);
            p.setAwardedOn(day);
            points.save(p);
        } else if (!present && existing.isPresent()) {
            points.delete(existing.get());
        }
    }

    /** A teacher (or coordinator, principal) rewards a student of their school. */
    @Transactional
    public AcademicDtos.PointRow award(CurrentUser user, UUID studentId, int amount, String reason) {
        if (!user.isAny(Role.TEACHER, Role.COORDINATOR)) {
            throw new ForbiddenException("Only teachers award points. The principal can view them.");
        }
        if (amount < 1 || amount > MAX_MANUAL) {
            throw new BadRequestException("Points must be between 1 and " + MAX_MANUAL + ".");
        }
        if (reason == null || reason.isBlank() || reason.length() > 200) {
            throw new BadRequestException("Please give a short reason (up to 200 characters).");
        }
        Enrolment e = enrolments.findByStudentId(studentId).filter(x -> x.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new NotFoundException("The student"));
        if (user.isAny(Role.TEACHER, Role.COORDINATOR) && !access.teaches(user, e.getSectionId())) {
            throw new ForbiddenException("You can award points only to students you teach.");
        }
        PointEntry p = new PointEntry();
        p.setOrgId(user.orgId());
        p.setSiteId(user.siteId());
        p.setStudentId(studentId);
        p.setAmount(amount);
        p.setReason(reason.trim());
        p.setSource(PointSource.MANUAL);
        p.setAwardedBy(user.personId());
        p.setAwardedOn(access.today());
        points.save(p);
        audit.log(user.orgId(), user.siteId(), "POINTS_AWARDED").by(user).on("Person", studentId)
                .change(null, "+" + amount + " " + p.getReason()).save();
        return row(p, user.name());
    }

    /** The student's own points. */
    @Transactional(readOnly = true)
    public AcademicDtos.PointsView mine(CurrentUser user) {
        if (!user.is(Role.STUDENT)) {
            throw new ForbiddenException("These are a student's own points.");
        }
        return of(user.personId());
    }

    /** A parent sees the points of a linked child. */
    @Transactional(readOnly = true)
    public AcademicDtos.PointsView ofChild(CurrentUser user, UUID studentId) {
        if (!user.is(Role.PARENT) || !parentLinks.isLinked(user.personId(), studentId)) {
            throw new NotFoundException("The child");
        }
        return of(studentId);
    }

    private AcademicDtos.PointsView of(UUID studentId) {
        List<PointEntry> all = points.findByStudentIdOrderByAwardedOnDescCreatedAtDesc(studentId);
        Map<UUID, String> names = access.personNames(all.stream().map(PointEntry::getAwardedBy).filter(x -> x != null).toList());
        return new AcademicDtos.PointsView(all.stream().mapToInt(PointEntry::getAmount).sum(),
                all.stream().limit(30).map(p -> row(p, p.getAwardedBy() == null ? null : names.get(p.getAwardedBy()))).toList());
    }

    private static AcademicDtos.PointRow row(PointEntry p, String by) {
        return new AcademicDtos.PointRow(p.getAmount(), p.getReason(), p.getSource().name(), p.getAwardedOn(), by);
    }

    /** Class totals for a section: every student with their points. */
    @Transactional(readOnly = true)
    public List<AcademicDtos.ClassPoints> classTotals(CurrentUser user, UUID sectionId) {
        if (!user.isAny(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL, Role.SCHOOL_IT)) {
            throw new ForbiddenException("Class points are for school staff.");
        }
        access.requireView(user, sectionId);
        Map<UUID, Integer> totals = totalsBySite(user.siteId());
        List<AcademicDtos.ClassPoints> out = new ArrayList<>();
        for (Person s : access.students(sectionId)) {
            out.add(new AcademicDtos.ClassPoints(s.getId(), s.getDisplayName(), totals.getOrDefault(s.getId(), 0)));
        }
        out.sort(Comparator.comparingInt(AcademicDtos.ClassPoints::total).reversed());
        return out;
    }

    /** School-wide recognition: each school's total, average per student and top students. */
    @Transactional(readOnly = true)
    public List<AcademicDtos.SchoolPoints> recognition(CurrentUser user) {
        if (!user.isAny(Role.PRINCIPAL, Role.COORDINATOR, Role.SCHOOL_IT, Role.LEADERSHIP)) {
            throw new ForbiddenException("Recognition analytics are for school leadership.");
        }
        var schools = user.isNetwork()
                ? sites.findByOrgIdOrderByName(user.orgId()).stream().filter(s -> s.isActive() && !s.isHeadOffice()).toList()
                : sites.findById(user.siteId()).stream().toList();
        List<AcademicDtos.SchoolPoints> out = new ArrayList<>();
        for (var school : schools) {
            Map<UUID, Integer> totals = totalsBySite(school.getId());
            int students = enrolments.findBySiteId(school.getId()).size();
            int sum = totals.values().stream().mapToInt(Integer::intValue).sum();
            Map<UUID, String> names = access.personNames(totals.keySet());
            List<AcademicDtos.ClassPoints> top = totals.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).limit(5)
                    .map(e -> new AcademicDtos.ClassPoints(e.getKey(), names.getOrDefault(e.getKey(), ""), e.getValue())).toList();
            out.add(new AcademicDtos.SchoolPoints(school.getName(), sum, students == 0 ? 0 : Math.round(sum * 10f / students) / 10f, top));
        }
        return out;
    }

    private Map<UUID, Integer> totalsBySite(UUID siteId) {
        Map<UUID, Integer> totals = new HashMap<>();
        points.findBySiteId(siteId).forEach(p -> totals.merge(p.getStudentId(), p.getAmount(), Integer::sum));
        return totals;
    }
}
