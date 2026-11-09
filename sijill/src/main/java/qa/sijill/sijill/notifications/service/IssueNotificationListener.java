package qa.sijill.sijill.notifications.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.files.model.SchoolFile;
import qa.sijill.sijill.files.repository.FileProfileRepository;
import qa.sijill.sijill.files.repository.SchoolFileRepository;
import qa.sijill.sijill.issues.event.IssueEvents;
import qa.sijill.sijill.issues.model.Incident;
import qa.sijill.sijill.issues.model.Issue;
import qa.sijill.sijill.issues.repository.IncidentRepository;
import qa.sijill.sijill.issues.repository.IssueRepository;
import qa.sijill.sijill.notifications.model.Message;
import qa.sijill.sijill.notifications.repository.MessageRepository;
import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.model.Subject;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.organisation.repository.SubjectRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.rules.model.RuleVersion;
import qa.sijill.sijill.rules.repository.RuleVersionRepository;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.model.Sensitivity;
import qa.sijill.sijill.shared.model.Severity;
import qa.sijill.sijill.shared.util.MessageTemplates;
import qa.sijill.sijill.shared.util.SchoolCalendar;

/**
 * <b>Who is told what</b> when something happens to an issue (proposal 5.4 notification table). It listens
 * to the {@link IssueEvents} of the issues module; that module never calls it.
 *
 * <pre>
 *  Event                        Owner     Coordinator   Principal   Leadership
 *  Normal issue / reminder      message   list          feed        –
 *  Urgent (R2, R3)              at once   copied        alert       only if Restricted student/health data
 *  Exam leak, important delete  yes       alert         alert       ALERT
 *  Urgent still open 1 day      reminder  follow-up     alert       ALERT
 * </pre>
 * "List" means the issue simply appears in the coordinator's follow-up list (no push). Messages respect
 * quiet hours (20:00–07:00 and the weekend) except urgent ones; {@link MessageDispatchService} sends them
 * later. Handlers run in the publisher's transaction, so a notification and the change that caused it
 * succeed or fail together.
 */
@Component
@RequiredArgsConstructor
public class IssueNotificationListener {

    private final NotificationService bell;
    private final MessageRepository messages;
    private final PersonRepository people;
    private final SiteRepository sites;
    private final SubjectRepository subjects;
    private final SchoolFileRepository files;
    private final FileProfileRepository profiles;
    private final RuleVersionRepository ruleVersions;
    private final IssueRepository issues;
    private final IncidentRepository incidents;
    private final Clock clock;

    // =============================================================== issue events

    /** A new issue: the owner is told (or, for an uncertain file, only the coordinator). */
    @EventListener
    @Transactional
    public void on(IssueEvents.Opened event) {
        Issue issue = issue(event.issueId());
        SchoolFile file = file(issue);
        Person coordinator = coordinatorFor(issue.getSiteId(), issue.getSubjectId()).orElse(null);

        if (event.needsReview()) {
            // Uncertain file: never tell the owner as a guess (design principle 4).
            if (coordinator != null) {
                bell.send(coordinator, NotificationKind.FOLLOW_UP, title(coordinator, "ملف بحاجة إلى مراجعتك",
                        "A file needs your review"), file.getName(), issue.getId());
            }
            return;
        }

        RuleVersion version = ruleVersions.findById(issue.getRuleVersionId()).orElseThrow();
        boolean urgent = issue.getSeverity() == Severity.URGENT;

        // Owner: a logged message (copying the coordinator on urgent issues) and a bell entry.
        Person owner = issue.getOwnerId() == null ? null : people.findById(issue.getOwnerId()).orElse(null);
        if (owner != null) {
            MessageTemplates.Text text = MessageTemplates.issue(owner.getLanguage(), file.getName(),
                    issue.getDetailEn(), issue.getDetailAr(),
                    version.getWhyItMatters(), version.getWhyItMattersAr(),
                    version.getFixStepsEn(), version.getFixStepsAr());
            saveMessage(issue, owner, urgent ? coordinator : null, text, urgent);
            bell.send(owner, NotificationKind.ISSUE, title(owner,
                    urgent ? "عاجل: ملف يحتاج إلى انتباهك" : "ملف يحتاج إلى انتباهك",
                    urgent ? "Urgent: a file needs your attention" : "A file needs your attention"),
                    file.getName(), issue.getId());
        }

        // Coordinator: copied (and pushed) only when urgent; normal issues just appear in her list.
        if (urgent && coordinator != null) {
            bell.send(coordinator, NotificationKind.ISSUE, title(coordinator, "عاجل: ملف يحتاج إلى متابعة",
                    "Urgent: a file needs follow-up"), file.getName(), issue.getId());
        }

        // Principal: sees every school notification (feed for normal, alert for urgent).
        principalFor(issue.getSiteId()).ifPresent(p -> bell.send(p, NotificationKind.ISSUE,
                title(p, urgent ? "تنبيه عاجل في مدرستك" : "ملاحظة جديدة على ملف في مدرستك",
                        urgent ? "Urgent alert in your school" : "A new file issue in your school"),
                file.getName(), issue.getId()));

        // Leadership: only for urgent issues on Restricted student or health data.
        if (urgent && isRestrictedPersonalData(issue.getFileId())) {
            leadership(issue.getOrgId()).forEach(l -> bell.send(l, NotificationKind.INCIDENT,
                    title(l, "تنبيه: ملف مقيّد يحتوي بيانات طلاب أو صحة مكشوف",
                            "Alert: a Restricted file with student or health data is exposed"),
                    null, issue.getId()));
        }
    }

    /** Reminder to the owner after the rule's reminder days (FR-1.5), or sent by the coordinator. */
    @EventListener
    @Transactional
    public void on(IssueEvents.ReminderDue event) {
        Issue issue = issue(event.issueId());
        if (issue.getOwnerId() == null) {
            return;
        }
        people.findById(issue.getOwnerId()).ifPresent(owner -> {
            SchoolFile file = file(issue);
            saveMessage(issue, owner, null, MessageTemplates.reminder(owner.getLanguage(), file.getName()),
                    issue.getSeverity() == Severity.URGENT);
            bell.send(owner, NotificationKind.REMINDER, title(owner, "تذكير: ملف ما زال يحتاج إلى انتباهك",
                    "Reminder: a file still needs your attention"), file.getName(), issue.getId());
        });
    }

    /** The issue is still open a school day after the reminder: the coordinator should follow up. */
    @EventListener
    @Transactional
    public void on(IssueEvents.NeedsFollowUp event) {
        Issue issue = issue(event.issueId());
        coordinatorFor(issue.getSiteId(), issue.getSubjectId()).ifPresent(c -> bell.send(c, NotificationKind.FOLLOW_UP,
                title(c, "يحتاج إلى متابعة", "Needs follow-up"), file(issue).getName(), issue.getId()));
    }

    /** "I've fixed it" was pressed but the re-check still finds the problem: say what is still wrong (FR-2.6). */
    @EventListener
    @Transactional
    public void on(IssueEvents.StillBroken event) {
        Issue issue = issue(event.issueId());
        if (issue.getOwnerId() == null) {
            return;
        }
        people.findById(issue.getOwnerId()).ifPresent(owner -> {
            SchoolFile file = file(issue);
            saveMessage(issue, owner, null, MessageTemplates.stillBroken(owner.getLanguage(), file.getName(),
                    issue.getDetailEn(), issue.getDetailAr()), true);
            bell.send(owner, NotificationKind.ISSUE, title(owner, "ما زال الملف بحاجة إلى تصحيح",
                    "The file still needs a fix"), file.getName(), issue.getId());
        });
    }

    /** The owner disagreed or asked for help: the coordinator is told so she can answer. */
    @EventListener
    @Transactional
    public void on(IssueEvents.OwnerResponded event) {
        Issue issue = issue(event.issueId());
        coordinatorFor(issue.getSiteId(), issue.getSubjectId()).ifPresent(c -> bell.send(c, NotificationKind.FOLLOW_UP,
                title(c, event.titleAr(), event.titleEn()), file(issue).getName(), issue.getId()));
    }

    /** The coordinator decided something about the issue: the owner is told. */
    @EventListener
    @Transactional
    public void on(IssueEvents.DecisionMade event) {
        tellOwner(issue(event.issueId()), event.titleAr(), event.titleEn());
    }

    /** An exception was allowed for the owner's file until a date. */
    @EventListener
    @Transactional
    public void on(IssueEvents.ExceptionGranted event) {
        tellOwner(issue(event.issueId()), "تم السماح باستثناء لملفك حتى " + event.endsOn(),
                "An exception was allowed for your file until " + event.endsOn());
    }

    /** An issue moved to the coordinator because its owner left. */
    @EventListener
    @Transactional
    public void on(IssueEvents.Reassigned event) {
        Issue issue = issue(event.issueId());
        coordinatorFor(issue.getSiteId(), issue.getSubjectId()).ifPresent(c -> bell.send(c, NotificationKind.FOLLOW_UP,
                title(c, "ملف بحاجة إلى مراجعتك", "A file needs your review"), file(issue).getName(), issue.getId()));
    }

    // =============================================================== incidents

    /**
     * A major incident: owner, coordinator, principal <i>and leadership</i> are all alerted
     * (the bold "Alert" rows of the table).
     */
    @EventListener
    @Transactional
    public void on(IssueEvents.IncidentRaised event) {
        Incident incident = incidents.findById(event.incidentId()).orElseThrow();
        String ar = "حادثة مهمة: " + incidentTitleAr(incident);
        String en = "Major incident: " + incidentTitleEn(incident);

        Map<UUID, Person> audience = new LinkedHashMap<>(); // de-duplicated by person
        if (incident.getFileId() != null) {
            files.findById(incident.getFileId()).map(SchoolFile::getOwnerEmail)
                    .flatMap(email -> email == null ? Optional.<Person>empty() : people.findByEmailIgnoreCase(email))
                    .ifPresent(o -> audience.put(o.getId(), o));
        }
        coordinatorFor(incident.getSiteId(), null).ifPresent(c -> audience.put(c.getId(), c));
        principalFor(incident.getSiteId()).ifPresent(p -> audience.put(p.getId(), p));
        leadership(incident.getOrgId()).forEach(l -> audience.put(l.getId(), l));

        audience.values().forEach(p -> bell.send(p, NotificationKind.INCIDENT, title(p, ar, en),
                incident.getDescription(), incident.getIssueId()));
    }

    private String incidentTitleEn(Incident i) {
        return switch (i.getType()) {
            case EXAM_LEAK -> "an exam was shared before its date";
            case IMPORTANT_DELETION -> "an important file was deleted";
            case BULK_DELETION -> "many files were deleted in a short time";
            case URGENT_UNRESOLVED -> "an urgent issue is still open";
            case CONNECTION_LOST -> "the connection to the workspace is lost";
        };
    }

    private String incidentTitleAr(Incident i) {
        return switch (i.getType()) {
            case EXAM_LEAK -> "تمت مشاركة اختبار قبل موعده";
            case IMPORTANT_DELETION -> "تم حذف ملف مهم";
            case BULK_DELETION -> "حُذفت ملفات كثيرة في وقت قصير";
            case URGENT_UNRESOLVED -> "ما زالت مشكلة عاجلة مفتوحة";
            case CONNECTION_LOST -> "انقطع الاتصال بمساحة العمل";
        };
    }

    // =============================================================== recipients

    /** The subject's coordinator, else the school's coordinator, else any active coordinator of the school. */
    Optional<Person> coordinatorFor(UUID siteId, UUID subjectId) {
        if (subjectId != null) {
            Optional<Person> bySubject = subjects.findById(subjectId).map(Subject::getCoordinatorId)
                    .flatMap(id -> id == null ? Optional.<Person>empty() : people.findById(id));
            if (bySubject.isPresent()) {
                return bySubject;
            }
        }
        Optional<Person> bySite = sites.findById(siteId).map(Site::getCoordinatorId)
                .flatMap(id -> id == null ? Optional.<Person>empty() : people.findById(id));
        if (bySite.isPresent()) {
            return bySite;
        }
        return people.findBySiteIdAndRoleAndStatusNot(siteId, Role.COORDINATOR, PersonStatus.LEFT).stream().findFirst();
    }

    Optional<Person> principalFor(UUID siteId) {
        Optional<Person> byId = sites.findById(siteId).map(Site::getPrincipalId)
                .flatMap(id -> id == null ? Optional.<Person>empty() : people.findById(id));
        if (byId.isPresent()) {
            return byId;
        }
        return people.findBySiteIdAndRoleAndStatusNot(siteId, Role.PRINCIPAL, PersonStatus.LEFT).stream().findFirst();
    }

    List<Person> leadership(UUID orgId) {
        return people.findByOrgIdAndRole(orgId, Role.LEADERSHIP).stream()
                .filter(p -> p.getStatus() != PersonStatus.LEFT).toList();
    }

    // =============================================================== helpers

    private void tellOwner(Issue issue, String titleAr, String titleEn) {
        if (issue.getOwnerId() == null) {
            return;
        }
        people.findById(issue.getOwnerId()).ifPresent(owner -> bell.send(owner, NotificationKind.ISSUE,
                title(owner, titleAr, titleEn), file(issue).getName(), issue.getId()));
    }

    private Issue issue(UUID id) {
        return issues.findById(id).orElseThrow(() -> new NotFoundException("The issue"));
    }

    private SchoolFile file(Issue issue) {
        return files.findById(issue.getFileId()).orElseThrow(() -> new NotFoundException("The file"));
    }

    private boolean isRestrictedPersonalData(UUID fileId) {
        return profiles.findByFileId(fileId)
                .filter(p -> p.getSensitivity() == Sensitivity.RESTRICTED && (p.isHasStudentData() || p.isHasHealthData()))
                .isPresent();
    }

    private String title(Person person, String ar, String en) {
        return MessageTemplates.title(person.getLanguage(), ar, en);
    }

    /** Stores the message with its earliest delivery time (quiet hours applied unless urgent). */
    private void saveMessage(Issue issue, Person recipient, Person copyTo, MessageTemplates.Text text, boolean urgent) {
        Message m = new Message();
        m.setOrgId(issue.getOrgId());
        m.setSiteId(issue.getSiteId());
        m.setIssueId(issue.getId());
        m.setRecipientId(recipient.getId());
        m.setCopyToId(copyTo == null ? null : copyTo.getId());
        m.setLanguage(recipient.getLanguage());
        m.setSubject(text.subject());
        m.setBody(text.body());
        m.setDeliverAt(SchoolCalendar.nextDeliveryTime(Instant.now(clock), urgent));
        messages.save(m);
    }
}
