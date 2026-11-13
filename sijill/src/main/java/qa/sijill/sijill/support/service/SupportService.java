package qa.sijill.sijill.support.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.repository.PersonRepository;
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
import qa.sijill.sijill.support.dto.SupportDtos;
import qa.sijill.sijill.support.model.SupportTicket;
import qa.sijill.sijill.support.model.TicketAttachment;
import qa.sijill.sijill.support.model.TicketAudience;
import qa.sijill.sijill.support.model.TicketCategory;
import qa.sijill.sijill.support.model.TicketReply;
import qa.sijill.sijill.support.model.TicketStatus;
import qa.sijill.sijill.support.model.TicketUrgency;
import qa.sijill.sijill.support.repository.SupportTicketRepository;
import qa.sijill.sijill.support.repository.TicketAttachmentRepository;
import qa.sijill.sijill.support.repository.TicketReplyRepository;

/**
 * Support tickets.
 *
 * <ul>
 *   <li><b>To School IT:</b> a teacher, coordinator, attendance officer or principal writes to their own school's School
 *       IT, who assigns, replies and closes it.</li>
 *   <li><b>To Leadership:</b> a principal can also write to Leadership at head office. Leadership and EAA administrators
 *       see these tickets from every school, assign, reply and close them.</li>
 *   <li>A principal can see every ticket of their own school (to follow what is happening), and replies on their own.</li>
 * </ul>
 * Every step is written to the activity log, and the people involved get a bell notification.
 */
@Service
@RequiredArgsConstructor
public class SupportService {

    static final int MAX_ATTACHMENT_BYTES = 2 * 1024 * 1024;
    /** Allowed attachment types by extension; the type is taken from the extension, never from the client. */
    private static final Map<String, String> TYPES = Map.of(
            "pdf", "application/pdf", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "txt", "text/plain", "csv", "text/csv",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SupportTicketRepository tickets;
    private final TicketReplyRepository replies;
    private final TicketAttachmentRepository attachments;
    private final PersonRepository people;
    private final SiteRepository sites;
    private final AuditPublisher audit;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    // =============================================================== opening a ticket

    @Transactional
    public SupportDtos.TicketView create(CurrentUser user, SupportDtos.CreateRequest in) {
        TicketAudience audience = parse(TicketAudience.class, in.audience() == null ? "SCHOOL_IT" : in.audience(), "recipient");
        if (audience == TicketAudience.LEADERSHIP) {
            if (!user.is(Role.PRINCIPAL)) {
                throw new ForbiddenException("Only a principal can write to Leadership. Write to your School IT instead.");
            }
        } else if (!user.isAny(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL, Role.ATTENDANCE_OFFICER)) {
            throw new ForbiddenException("Your role cannot open a support ticket.");
        }
        String title = clean(in.title(), 200, "A title is required.");
        String description = clean(in.description(), 4000, "A description is required.");
        SupportTicket t = new SupportTicket();
        t.setOrgId(user.orgId());
        t.setSiteId(user.siteId());
        t.setTitle(title);
        t.setDescription(description);
        t.setCategory(parse(TicketCategory.class, in.category(), "category"));
        t.setUrgency(parse(TicketUrgency.class, in.urgency(), "urgency"));
        t.setAudience(audience);
        t.setCreatedBy(user.personId());
        t.setCreatedByName(user.name());
        byte[] bytes = null;
        String type = null;
        if (in.attachmentData() != null && !in.attachmentData().isBlank()) {
            String name = safeName(in.attachmentName());
            type = TYPES.get(extension(name));
            if (type == null) {
                throw new BadRequestException("This kind of attachment is not accepted. Use PDF, PNG, JPG, DOCX, XLSX, TXT or CSV.");
            }
            bytes = decode(in.attachmentData());
            t.setAttachmentName(name);
        }
        tickets.save(t);
        if (bytes != null) {
            TicketAttachment a = new TicketAttachment();
            a.setTicketId(t.getId());
            a.setContentType(type);
            a.setData(bytes);
            attachments.save(a);
        }
        audit.log(t.getOrgId(), t.getSiteId(), ActivityActions.TICKET_CREATED).by(user).on("SupportTicket", t.getId())
                .change(null, audience + " / " + t.getUrgency() + ": " + t.getTitle()).save();
        String school = schoolName(t.getSiteId());
        for (Person handler : handlers(t)) {
            notify(handler, "تذكرة دعم جديدة من " + user.name() + " (" + school + ")", "New support ticket from " + user.name() + " (" + school + ")",
                    t.getTitle() + " (" + t.getUrgency().name().toLowerCase(Locale.ROOT) + ")");
        }
        return SupportDtos.view(t, school, 0);
    }

    // =============================================================== reading

    /** The tickets this person may see: their own; a principal's whole school; School IT's school (their tickets); Leadership's Leadership tickets. */
    @Transactional(readOnly = true)
    public List<SupportDtos.TicketView> list(CurrentUser user) {
        List<SupportTicket> rows;
        if (user.isNetwork()) {
            rows = tickets.findTop500ByOrgIdOrderByUpdatedAtDesc(user.orgId()).stream().filter(t -> t.getAudience() == TicketAudience.LEADERSHIP).toList();
        } else if (user.is(Role.PRINCIPAL)) {
            rows = tickets.findTop200BySiteIdOrderByUpdatedAtDesc(user.siteId());
        } else if (user.is(Role.SCHOOL_IT)) {
            rows = tickets.findTop200BySiteIdOrderByUpdatedAtDesc(user.siteId()).stream().filter(t -> t.getAudience() == TicketAudience.SCHOOL_IT).toList();
        } else if (user.isAny(Role.TEACHER, Role.COORDINATOR, Role.ATTENDANCE_OFFICER)) {
            rows = tickets.findTop200ByCreatedByOrderByUpdatedAtDesc(user.personId());
        } else {
            throw new ForbiddenException("Your role does not use support tickets.");
        }
        Map<UUID, String> names = new HashMap<>();
        return rows.stream().map(t -> SupportDtos.view(t,
                names.computeIfAbsent(t.getSiteId(), this::schoolName), replies.findByTicketIdOrderByCreatedAt(t.getId()).size())).toList();
    }

    @Transactional(readOnly = true)
    public SupportDtos.TicketDetail get(CurrentUser user, UUID id) {
        SupportTicket t = visible(user, id);
        List<TicketReply> thread = replies.findByTicketIdOrderByCreatedAt(t.getId());
        return new SupportDtos.TicketDetail(SupportDtos.view(t, schoolName(t.getSiteId()), thread.size()), t.getDescription(),
                thread.stream().map(SupportDtos.ReplyView::from).toList(), canReply(user, t), canManage(user, t));
    }

    @Transactional(readOnly = true)
    public TicketAttachment attachment(CurrentUser user, UUID id) {
        SupportTicket t = visible(user, id);
        return attachments.findById(t.getId()).orElseThrow(() -> new NotFoundException("The attachment"));
    }

    /** Who a ticket can be assigned to: School IT of the school for School IT, Leadership and EAA administrators for head office. */
    @Transactional(readOnly = true)
    public List<SupportDtos.Assignee> assignees(CurrentUser user) {
        List<Person> pool;
        if (user.is(Role.SCHOOL_IT)) {
            pool = people.findBySiteIdAndRole(user.siteId(), Role.SCHOOL_IT);
        } else if (user.isNetwork()) {
            pool = headOffice(user.orgId());
        } else {
            throw new ForbiddenException("Only School IT, Leadership and EAA administrators assign tickets.");
        }
        return pool.stream().filter(p -> p.getStatus() != PersonStatus.LEFT).map(p -> new SupportDtos.Assignee(p.getId(), p.getDisplayName())).toList();
    }

    // =============================================================== replying and triage

    @Transactional
    public SupportDtos.ReplyView reply(CurrentUser user, UUID id, String body) {
        SupportTicket t = visible(user, id);
        if (!canReply(user, t)) {
            throw new ForbiddenException(t.getStatus() == TicketStatus.CLOSED
                    ? "This ticket is closed. Open a new ticket for a new question." : "You cannot reply to this ticket.");
        }
        TicketReply r = new TicketReply();
        r.setOrgId(t.getOrgId());
        r.setSiteId(t.getSiteId());
        r.setTicketId(t.getId());
        r.setAuthorId(user.personId());
        r.setAuthorName(user.name());
        r.setAuthorRole(user.role().name());
        r.setBody(clean(body, 4000, "Write a reply first."));
        replies.save(r);
        boolean fromHandler = canManage(user, t);
        if (fromHandler && t.getStatus() == TicketStatus.OPEN) {
            t.setStatus(TicketStatus.IN_PROGRESS);       // someone has started on it
        }
        t.setUpdatedAt(Instant.now(clock));
        audit.log(t.getOrgId(), t.getSiteId(), ActivityActions.TICKET_REPLIED).by(user).on("SupportTicket", t.getId())
                .change(null, t.getTitle()).save();
        if (fromHandler && !user.personId().equals(t.getCreatedBy())) {
            people.findById(t.getCreatedBy()).ifPresent(p -> notify(p, "رد جديد على تذكرتك", "New reply on your ticket", t.getTitle()));
        } else if (!fromHandler) {
            for (Person p : handlers(t)) {
                notify(p, "رد جديد من " + user.name(), "New reply from " + user.name(), t.getTitle());
            }
        }
        return SupportDtos.ReplyView.from(r);
    }

    @Transactional
    public SupportDtos.TicketView assign(CurrentUser user, UUID id, UUID assigneeId) {
        SupportTicket t = visible(user, id);
        requireManager(user, t);
        Person assignee = assignees(user).stream().filter(a -> a.id().equals(assigneeId)).findFirst()
                .flatMap(a -> people.findById(a.id()))
                .orElseThrow(() -> new BadRequestException("Choose someone from the list."));
        t.setAssignedTo(assignee.getId());
        t.setAssignedToName(assignee.getDisplayName());
        if (t.getStatus() == TicketStatus.OPEN) {
            t.setStatus(TicketStatus.IN_PROGRESS);
        }
        t.setUpdatedAt(Instant.now(clock));
        audit.log(t.getOrgId(), t.getSiteId(), ActivityActions.TICKET_ASSIGNED).by(user).on("SupportTicket", t.getId())
                .change(null, assignee.getDisplayName()).save();
        if (!assignee.getId().equals(user.personId())) {
            notify(assignee, "تذكرة دعم أُسندت إليك", "A support ticket was assigned to you", t.getTitle());
        }
        people.findById(t.getCreatedBy()).ifPresent(p -> notify(p, "تذكرتك قيد المعالجة", "Your ticket is being handled", t.getTitle()));
        return SupportDtos.view(t, schoolName(t.getSiteId()), replies.findByTicketIdOrderByCreatedAt(t.getId()).size());
    }

    @Transactional
    public SupportDtos.TicketView setStatus(CurrentUser user, UUID id, String status) {
        SupportTicket t = visible(user, id);
        requireManager(user, t);
        TicketStatus next = parse(TicketStatus.class, status, "status");
        if (next == t.getStatus()) {
            throw new ConflictException("The ticket already has this status.");
        }
        TicketStatus before = t.getStatus();
        t.setStatus(next);
        t.setClosedAt(next == TicketStatus.CLOSED ? Instant.now(clock) : null);
        t.setUpdatedAt(Instant.now(clock));
        audit.log(t.getOrgId(), t.getSiteId(), ActivityActions.TICKET_STATUS_CHANGED).by(user).on("SupportTicket", t.getId())
                .change(before.name(), next.name()).save();
        people.findById(t.getCreatedBy()).ifPresent(p -> notify(p, "تغيّرت حالة تذكرتك", "Your ticket changed status",
                t.getTitle() + ": " + next.name().replace('_', ' ').toLowerCase(Locale.ROOT)));
        return SupportDtos.view(t, schoolName(t.getSiteId()), replies.findByTicketIdOrderByCreatedAt(t.getId()).size());
    }

    // =============================================================== who may do what

    /** The ticket if the person may see it (otherwise "not found", so other people's tickets are never revealed). */
    private SupportTicket visible(CurrentUser user, UUID id) {
        SupportTicket t = tickets.findById(id).filter(x -> x.getOrgId().equals(user.orgId())).orElseThrow(() -> new NotFoundException("The ticket"));
        boolean ok = t.getCreatedBy().equals(user.personId())
                || canManage(user, t)
                || (user.is(Role.PRINCIPAL) && t.getSiteId().equals(user.siteId()));
        if (!ok) {
            throw new NotFoundException("The ticket");
        }
        return t;
    }

    /** School IT handles School IT tickets of their own school; Leadership and EAA administrators handle Leadership tickets. */
    private boolean canManage(CurrentUser user, SupportTicket t) {
        return t.getAudience() == TicketAudience.SCHOOL_IT
                ? user.is(Role.SCHOOL_IT) && t.getSiteId().equals(user.siteId())
                : user.isNetwork();
    }

    private void requireManager(CurrentUser user, SupportTicket t) {
        if (!canManage(user, t)) {
            throw new ForbiddenException(t.getAudience() == TicketAudience.SCHOOL_IT
                    ? "Only School IT of this school can do this." : "Only Leadership or an EAA administrator can do this.");
        }
    }

    private boolean canReply(CurrentUser user, SupportTicket t) {
        return t.getStatus() != TicketStatus.CLOSED && (t.getCreatedBy().equals(user.personId()) || canManage(user, t));
    }

    // =============================================================== helpers

    /** The people a new ticket is for: the school's School IT, or head office. */
    private List<Person> handlers(SupportTicket t) {
        if (t.getAssignedTo() != null) {
            return people.findById(t.getAssignedTo()).filter(p -> p.getStatus() != PersonStatus.LEFT).map(List::of).orElseGet(() -> pool(t));
        }
        return pool(t);
    }

    private List<Person> pool(SupportTicket t) {
        List<Person> all = t.getAudience() == TicketAudience.SCHOOL_IT ? people.findBySiteIdAndRole(t.getSiteId(), Role.SCHOOL_IT) : headOffice(t.getOrgId());
        return all.stream().filter(p -> p.getStatus() != PersonStatus.LEFT && p.canSignIn()).toList();
    }

    /** Leadership and EAA administrators of the network. */
    private List<Person> headOffice(UUID orgId) {
        List<Person> out = new java.util.ArrayList<>(people.findByOrgIdAndRole(orgId, Role.LEADERSHIP));
        out.addAll(people.findByOrgIdAndRole(orgId, Role.EAA_ADMIN));
        return out;
    }

    private void notify(Person to, String titleAr, String titleEn, String body) {
        publisher.publishEvent(new PersonNotice(to.getId(), NotificationKind.TICKET, titleAr, titleEn, body));
    }

    private String schoolName(UUID siteId) {
        return sites.findById(siteId).map(Site::getName).orElse("");
    }

    private static String clean(String s, int max, String missing) {
        String v = s == null ? "" : s.strip();
        if (v.isEmpty()) {
            throw new BadRequestException(missing);
        }
        if (v.length() > max) {
            throw new BadRequestException("This is too long (at most " + max + " characters).");
        }
        return v;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String what) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Choose a valid " + what + ".");
        }
    }

    private static String safeName(String name) {
        String n = name == null ? "" : name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>:*?|]", "").strip();
        if (n.isEmpty()) {
            throw new BadRequestException("The attachment needs a file name.");
        }
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] decode(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("The attachment could not be read.");
        }
        if (bytes.length == 0) {
            throw new BadRequestException("The attachment is empty.");
        }
        if (bytes.length > MAX_ATTACHMENT_BYTES) {
            throw new BadRequestException("The attachment is too large (at most 2 MB).");
        }
        return bytes;
    }
}
