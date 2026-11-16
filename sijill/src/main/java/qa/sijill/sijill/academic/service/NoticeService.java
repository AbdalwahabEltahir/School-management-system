package qa.sijill.sijill.academic.service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.academic.dto.AcademicDtos.ClassNoticeRequest;
import qa.sijill.sijill.academic.dto.AcademicDtos.NoticeRequest;
import qa.sijill.sijill.academic.model.Enrolment;
import qa.sijill.sijill.academic.model.Section;
import qa.sijill.sijill.academic.repository.EnrolmentRepository;
import qa.sijill.sijill.people.service.ParentLinks;
import qa.sijill.sijill.academic.dto.AcademicDtos.NoticeView;
import qa.sijill.sijill.academic.model.Notice;
import qa.sijill.sijill.academic.model.NoticeAudience;
import qa.sijill.sijill.academic.repository.NoticeRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.events.PersonNotice;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Notices (SMS FR-004). School IT writes a bilingual notice with a start and an end and chooses who sees it; a notice
 * shows only between its start and end (BR-009), and only when its Arabic version is complete (BR-010).
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository notices;
    private final AcademicAccess access;
    private final AuditPublisher audit;
    private final Clock clock;
    private final PersonRepository people;
    private final ApplicationEventPublisher publisher;
    private final EnrolmentRepository enrolments;
    private final ParentLinks parentLinks;

    @Transactional
    public NoticeView create(CurrentUser user, NoticeRequest request) {
        access.requireAdmin(user);
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BadRequestException("The end of a notice must be after its start.");
        }
        if (blank(request.titleAr()) || blank(request.bodyAr())) {
            throw new BadRequestException("A notice needs its Arabic title and text before it can be published.");
        }
        Notice n = new Notice();
        n.setOrgId(user.orgId());
        n.setSiteId(user.siteId());
        n.setTitleEn(request.titleEn().trim());
        n.setTitleAr(request.titleAr().trim());
        n.setBodyEn(request.bodyEn().trim());
        n.setBodyAr(request.bodyAr().trim());
        n.setStartsAt(request.startsAt());
        n.setEndsAt(request.endsAt());
        n.setAudience(request.audience());
        n.setCreatedBy(user.personId());
        notices.save(n);
        audit.log(user.orgId(), user.siteId(), ActivityActions.NOTICE_PUBLISHED).by(user).on("Notice", n.getId())
                .change(null, n.getTitleEn()).save();
        notifyAudience(user, n);
        return view(n);
    }

    /** A bell entry for everyone the notice is meant for (not the author), so staff and families see it without looking for it. */
    private void notifyAudience(CurrentUser author, Notice n) {
        Set<Role> roles = switch (n.getAudience()) {
            case ALL -> EnumSet.of(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL, Role.PRINCIPAL_ASSISTANT, Role.SCHOOL_IT, Role.STUDENT, Role.PARENT);
            case STAFF -> EnumSet.of(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL, Role.PRINCIPAL_ASSISTANT, Role.SCHOOL_IT);
            case STUDENTS -> EnumSet.of(Role.STUDENT, Role.PARENT);
        };
        for (Role role : roles) {
            for (Person p : people.findBySiteIdAndRole(n.getSiteId(), role)) {
                if (p.getId().equals(author.personId()) || p.getStatus() == PersonStatus.LEFT) continue;
                publisher.publishEvent(new PersonNotice(p.getId(), NotificationKind.NOTICE, "إشعار جديد: " + n.getTitleAr(),
                        "New notice: " + n.getTitleEn(), n.getBodyEn().length() > 900 ? n.getBodyEn().substring(0, 900) + "…" : n.getBodyEn(),
                        n.getBodyAr().length() > 900 ? n.getBodyAr().substring(0, 900) + "…" : n.getBodyAr()));
            }
        }
    }

    /**
     * A teacher's notice for one class they teach. It shows at the top of the portal of that class's students and of
     * their parents, beside the exam banner. Arabic is optional here: the English text is used when it is left empty.
     */
    @Transactional
    public NoticeView createForClass(CurrentUser user, ClassNoticeRequest request) {
        Section section = access.requireTeacherOf(user, request.sectionId());
        Instant start = request.startsAt() == null ? Instant.now(clock) : request.startsAt();
        if (!request.endsAt().isAfter(start)) {
            throw new BadRequestException("The end of a notice must be after its start.");
        }
        Notice n = new Notice();
        n.setOrgId(user.orgId());
        n.setSiteId(user.siteId());
        n.setTitleEn(request.titleEn().trim());
        n.setTitleAr(blank(request.titleAr()) ? request.titleEn().trim() : request.titleAr().trim());
        n.setBodyEn(request.bodyEn().trim());
        n.setBodyAr(blank(request.bodyAr()) ? request.bodyEn().trim() : request.bodyAr().trim());
        n.setStartsAt(start);
        n.setEndsAt(request.endsAt());
        n.setAudience(NoticeAudience.STUDENTS);
        n.setSectionId(section.getId());
        n.setCreatedBy(user.personId());
        notices.save(n);
        audit.log(user.orgId(), user.siteId(), ActivityActions.NOTICE_PUBLISHED).by(user).on("Notice", n.getId())
                .change(null, n.getTitleEn() + " for " + AcademicAccess.label(section)).save();
        List<UUID> students = enrolments.findBySectionId(section.getId()).stream().map(Enrolment::getStudentId).toList();
        Set<UUID> audience = new LinkedHashSet<>(students);
        students.forEach(st -> audience.addAll(parentLinks.parentsOf(st)));
        for (UUID id : audience) {
            publisher.publishEvent(new PersonNotice(id, NotificationKind.CLASSWORK, "إشعار من المعلم: " + n.getTitleAr(),
                    "Class notice: " + n.getTitleEn(), cut(n.getBodyEn()), cut(n.getBodyAr())));
        }
        return view(n);
    }

    private static String cut(String s) {
        return s.length() > 900 ? s.substring(0, 900) + "…" : s;
    }

    /** The class notices this teacher wrote, newest first. */
    @Transactional(readOnly = true)
    public List<NoticeView> mineForClasses(CurrentUser user) {
        if (!user.isAny(Role.TEACHER, Role.COORDINATOR)) {
            throw new ForbiddenException("Class notices are written by teachers.");
        }
        return notices.findBySiteIdOrderByStartsAtDesc(user.siteId()).stream()
                .filter(n -> n.getSectionId() != null && n.getCreatedBy().equals(user.personId())).map(this::view).toList();
    }

    /** The classes whose notices this person may see: a student's own, a parent's children's. */
    private Set<UUID> classesOf(CurrentUser user) {
        Set<UUID> out = new HashSet<>();
        if (user.is(Role.STUDENT)) {
            enrolments.findByStudentId(user.personId()).ifPresent(e -> out.add(e.getSectionId()));
        } else if (user.is(Role.PARENT)) {
            for (UUID child : parentLinks.childrenOf(user.personId())) {
                enrolments.findByStudentId(child).ifPresent(e -> out.add(e.getSectionId()));
            }
        }
        return out;
    }

    /** The notices to show this person right now: inside their dates and meant for them. */
    @Transactional(readOnly = true)
    public List<NoticeView> current(CurrentUser user) {
        if (user.isNetwork()) {
            throw new ForbiddenException("Notices are shown to the school's own people.");
        }
        return activeFor(user);
    }

    List<NoticeView> activeFor(CurrentUser user) {
        Instant now = Instant.now(clock);
        Set<UUID> classes = classesOf(user);
        return notices.findBySiteIdOrderByStartsAtDesc(user.siteId()).stream()
                .filter(n -> !n.getStartsAt().isAfter(now) && n.getEndsAt().isAfter(now))
                .filter(n -> n.getSectionId() == null || classes.contains(n.getSectionId()))
                .filter(n -> n.getAudience() == NoticeAudience.ALL
                        || (n.getAudience() == NoticeAudience.STUDENTS) == user.isAny(Role.STUDENT, Role.PARENT))
                .map(this::view).toList();
    }

    /** All of the school's notices, past, current and future, for School IT to manage. */
    @Transactional(readOnly = true)
    public List<NoticeView> manage(CurrentUser user) {
        access.requireAdmin(user);
        return notices.findBySiteIdOrderByStartsAtDesc(user.siteId()).stream().map(this::view).toList();
    }

    @Transactional
    public void remove(CurrentUser user, UUID noticeId) {
        Notice n = notices.findById(noticeId).filter(x -> x.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new NotFoundException("The notice"));
        if (!(n.getSectionId() != null && n.getCreatedBy().equals(user.personId()))) {
            access.requireAdmin(user);   // School IT removes any notice; a teacher only their own class notices
        }
        notices.delete(n);
        audit.log(user.orgId(), user.siteId(), ActivityActions.NOTICE_REMOVED).by(user).on("Notice", n.getId())
                .change(n.getTitleEn(), null).save();
    }

    private NoticeView view(Notice n) {
        String section = null, author = null;
        if (n.getSectionId() != null) {
            section = AcademicAccess.label(access.sectionsById(n.getSiteId()).get(n.getSectionId()));
            author = people.findById(n.getCreatedBy()).map(Person::getDisplayName).orElse(null);
        }
        return new NoticeView(n.getId(), n.getTitleEn(), n.getTitleAr(), n.getBodyEn(), n.getBodyAr(), n.getStartsAt(),
                n.getEndsAt(), n.getAudience(), n.getSectionId(), section, author);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
