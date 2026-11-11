package qa.sijill.sijill.academic.service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.academic.dto.AcademicDtos.NoticeRequest;
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
            case ALL -> EnumSet.of(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL, Role.SCHOOL_IT, Role.STUDENT, Role.PARENT);
            case STAFF -> EnumSet.of(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL, Role.SCHOOL_IT);
            case STUDENTS -> EnumSet.of(Role.STUDENT, Role.PARENT);
        };
        for (Role role : roles) {
            for (Person p : people.findBySiteIdAndRole(n.getSiteId(), role)) {
                if (p.getId().equals(author.personId()) || p.getStatus() == PersonStatus.LEFT) continue;
                publisher.publishEvent(new PersonNotice(p.getId(), NotificationKind.SYSTEM, "إشعار جديد: " + n.getTitleAr(),
                        "New notice: " + n.getTitleEn(), n.getBodyEn().length() > 900 ? n.getBodyEn().substring(0, 900) + "…" : n.getBodyEn()));
            }
        }
    }

    /** The notices to show this person right now: inside their dates and meant for them. */
    @Transactional(readOnly = true)
    public List<NoticeView> current(CurrentUser user) {
        if (user.is(Role.LEADERSHIP)) {
            throw new ForbiddenException("Notices are shown to the school's own people.");
        }
        return activeFor(user);
    }

    List<NoticeView> activeFor(CurrentUser user) {
        Instant now = Instant.now(clock);
        return notices.findBySiteIdOrderByStartsAtDesc(user.siteId()).stream()
                .filter(n -> !n.getStartsAt().isAfter(now) && n.getEndsAt().isAfter(now))
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
        access.requireAdmin(user);
        Notice n = notices.findById(noticeId).filter(x -> x.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new NotFoundException("The notice"));
        notices.delete(n);
        audit.log(user.orgId(), user.siteId(), ActivityActions.NOTICE_REMOVED).by(user).on("Notice", n.getId())
                .change(n.getTitleEn(), null).save();
    }

    private NoticeView view(Notice n) {
        return new NoticeView(n.getId(), n.getTitleEn(), n.getTitleAr(), n.getBodyEn(), n.getBodyAr(), n.getStartsAt(),
                n.getEndsAt(), n.getAudience());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
