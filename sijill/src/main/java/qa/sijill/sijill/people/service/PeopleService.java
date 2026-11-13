package qa.sijill.sijill.people.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.organisation.repository.SubjectRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.model.RoleGroup;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.people.repository.RoleGroupRepository;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.events.PersonNotice;
import qa.sijill.sijill.shared.events.PersonStatusChanged;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.DigestMode;
import qa.sijill.sijill.shared.model.Language;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.AccessPolicy;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.workspace.WorkspaceConnector;
import qa.sijill.sijill.workspace.WorkspacePerson;

/**
 * People: the directory sync, School IT overrides, the People screen and "My preferences"
 * (FR-11.2, FR-11.5, proposal section 4).
 *
 * <ul>
 *   <li><b>Directory sync</b> reads people and their groups from the workspace (read-only) and maps
 *       groups to roles through {@link RoleGroup}.</li>
 *   <li><b>Overrides:</b> School IT may change role, status or subject by hand; these are audited and
 *       last <i>until the directory changes</i> (the last directory values are kept on the person).</li>
 *   <li><b>Status changes have consequences</b>, announced with {@link PersonStatusChanged}: <i>Leaving</i>
 *       re-checks the person's files (rule R8); <i>Left</i> moves their open issues to the coordinator.
 *       This module does not know who listens.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PeopleService {

    /** What School IT can change on a person. Null fields are left as they are. */
    public record UpdateCommand(Role role, PersonStatus status, UUID subjectId, Boolean generic, String name, String email) {
        public UpdateCommand(Role role, PersonStatus status, UUID subjectId, Boolean generic) {
            this(role, status, subjectId, generic, null, null);
        }
    }

    /** Order of precedence when a person is in several role groups: the most privileged wins. */
    private static final List<Role> ROLE_PRECEDENCE = List.of(
            Role.EAA_ADMIN, Role.LEADERSHIP, Role.SCHOOL_IT, Role.PRINCIPAL, Role.COORDINATOR, Role.TEACHER);

    private final PersonRepository people;
    private final ParentLinks parentLinks;
    private final SiteRepository sites;
    private final SubjectRepository subjects;
    private final RoleGroupRepository roleGroups;
    private final AuditPublisher audit;
    private final ApplicationEventPublisher publisher;
    private final AccessPolicy policy;
    private final ObjectProvider<WorkspaceConnector> connector;
    private final Clock clock;

    // ================================================================== reading

    /** The People screen: School IT and coordinators see their own school's people; the principal reads them (no edits). */
    @Transactional(readOnly = true)
    public List<Person> list(CurrentUser user) {
        policy.requireRole(user, Role.SCHOOL_IT, Role.COORDINATOR, Role.PRINCIPAL);
        return people.findBySiteIdOrderByDisplayName(user.siteId());
    }

    // ================================================================== my preferences

    /** The signed-in person's own record (name, role, language, summary mode). */
    @Transactional(readOnly = true)
    public Person me(CurrentUser user) {
        return people.findById(user.personId()).orElseThrow(() -> new NotFoundException("Your account"));
    }

    /** "My preferences": message language, per-issue vs daily summary, and which kinds of alert to receive. Saved at once. */
    @Transactional
    public Person updatePreferences(CurrentUser user, Language language, DigestMode digestMode, List<String> muted) {
        Person person = me(user);
        if (language != null) {
            person.setLanguage(language);
        }
        if (digestMode != null) {
            person.setDigestMode(digestMode);
        }
        if (muted != null) {
            java.util.Set<NotificationKind> kinds = java.util.EnumSet.noneOf(NotificationKind.class);
            for (String k : muted) {
                try {
                    kinds.add(NotificationKind.valueOf(k));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Unknown kind of alert.");
                }
            }
            person.setMuted(kinds);
        }
        return person;
    }

    /** The alert kinds a person of this role can receive, so the settings page lists only what applies to them. */
    public static List<String> alertKindsFor(Role role) {
        return switch (role) {
            case STUDENT, ATTENDANCE_OFFICER -> List.of("NOTICE", "SYSTEM", "SECURITY");
            case PARENT -> List.of("COLLECTION", "NOTICE", "SYSTEM", "SECURITY");
            case LEADERSHIP, EAA_ADMIN -> List.of("INCIDENT", "TICKET", "NOTICE", "SYSTEM", "SECURITY");
            case PRINCIPAL -> List.of("ISSUE", "INCIDENT", "GRADES", "TICKET", "NOTICE", "PLANNING", "SYSTEM", "SECURITY");
            case SCHOOL_IT -> List.of("ISSUE", "INCIDENT", "NOTICE", "SYSTEM", "SECURITY");
            case COORDINATOR -> List.of("ISSUE", "REMINDER", "FOLLOW_UP", "GRADES", "COLLECTION", "NOTICE", "PLANNING", "SYSTEM", "SECURITY");
            case TEACHER -> List.of("ISSUE", "REMINDER", "GRADES", "COLLECTION", "NOTICE", "PLANNING", "SYSTEM", "SECURITY");
        };
    }

    private static final int MAX_PHOTO_BYTES = 512 * 1024;

    /** The person's own profile: display name and contact number. Logged. */
    @Transactional
    public Person updateProfile(CurrentUser user, String displayName, String phone) {
        Person person = me(user);
        String name = displayName == null ? "" : displayName.strip();
        if (name.length() < 2 || name.length() > 200) {
            throw new BadRequestException("Your name must be between 2 and 200 characters.");
        }
        String number = phone == null ? "" : phone.strip();
        if (number.length() > 30 || !number.matches("[0-9 +()-]*")) {
            throw new BadRequestException("Enter a phone number using digits, spaces, + ( ) and - only.");
        }
        String before = person.getDisplayName() + (person.getPhone() == null ? "" : ", " + person.getPhone());
        person.setDisplayName(name);
        person.setPhone(number.isEmpty() ? null : number);
        audit.log(person.getOrgId(), person.getSiteId(), ActivityActions.PROFILE_UPDATED).by(user).on("Person", person.getId())
                .change(before, name + (number.isEmpty() ? "" : ", " + number)).save();
        return person;
    }

    /** A profile photo: a small PNG or JPEG. The type is read from the file's own bytes, never from the client. */
    @Transactional
    public Person setPhoto(CurrentUser user, String base64) {
        byte[] bytes;
        try {
            bytes = java.util.Base64.getMimeDecoder().decode(base64 == null ? "" : base64);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("The photo could not be read.");
        }
        if (bytes.length == 0 || bytes.length > MAX_PHOTO_BYTES) {
            throw new BadRequestException("Choose a photo of at most 500 KB.");
        }
        String type;
        if (bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            type = "image/png";
        } else if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            type = "image/jpeg";
        } else {
            throw new BadRequestException("The photo must be a PNG or JPEG image.");
        }
        Person person = me(user);
        person.setPhoto(bytes);
        person.setPhotoType(type);
        audit.log(person.getOrgId(), person.getSiteId(), ActivityActions.PROFILE_UPDATED).by(user).on("Person", person.getId())
                .change(null, "photo changed").save();
        return person;
    }

    @Transactional
    public Person clearPhoto(CurrentUser user) {
        Person person = me(user);
        person.setPhoto(null);
        person.setPhotoType(null);
        return person;
    }

    // ================================================================== School IT actions

    /** Roles School IT may give a new person. Leadership and School IT accounts are not created from this screen. */
    private static final List<Role> ADDABLE = List.of(Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL, Role.ATTENDANCE_OFFICER, Role.STUDENT, Role.PARENT);

    /**
     * Adds a person to the user's school so they can sign in with their Google account. Sign-in checks that the
     * e-mail's domain belongs to a school of the network, so an address from any other domain is refused here
     * with a clear message instead of failing later at the sign-in page.
     */
    @Transactional
    public Person add(CurrentUser user, String email, String name, Role role, UUID subjectId) {
        return add(user, email, name, role, subjectId, null);
    }

    /** As above; a parent must be linked to the student they follow (studentId). */
    @Transactional
    public Person add(CurrentUser user, String email, String name, Role role, UUID subjectId, UUID studentId) {
        policy.requireRole(user, Role.SCHOOL_IT);
        String clean = email == null ? "" : email.trim().toLowerCase();
        if (!clean.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new BadRequestException("Please give a valid e-mail address.");
        }
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Please give the person's name.");
        }
        if (role == null || !ADDABLE.contains(role)) {
            throw new BadRequestException("Choose Teacher, Coordinator, Principal, Attendance Clerk, Student or Parent.");
        }
        String domain = clean.substring(clean.indexOf('@') + 1);
        boolean knownDomain = sites.findByOrgIdOrderByName(user.orgId()).stream()
                .anyMatch(s -> s.isActive() && s.getDomain().equalsIgnoreCase(domain));
        if (role != Role.STUDENT && role != Role.PARENT && !knownDomain) {
            throw new BadRequestException("Staff with @" + domain + " cannot sign in: that domain does not belong to any school of the network.");
        }
        Person child = null;
        if (role == Role.PARENT) {
            child = studentId == null ? null : people.findById(studentId)
                    .filter(c -> c.getRole() == Role.STUDENT && c.getSiteId().equals(user.siteId())).orElse(null);
            if (child == null) {
                throw new BadRequestException("Choose the student this parent follows.");
            }
        }
        if (people.findByEmailIgnoreCase(clean).isPresent()) {
            throw new ConflictException(clean + " already has an account.");
        }
        Person person = new Person();
        person.setOrgId(user.orgId());
        person.setSiteId(user.siteId());
        person.setEmail(clean);
        person.setDisplayName(name.trim());
        person.setRole(role);
        person.setStatus(PersonStatus.ACTIVE);
        if (subjectId != null) {
            subjects.findById(subjectId).filter(s -> s.getSiteId().equals(user.siteId()))
                    .orElseThrow(() -> new BadRequestException("That subject does not belong to this school."));
            person.setSubjectId(subjectId);
        }
        people.saveAndFlush(person); // flushed: the link below is plain SQL and needs the row
        if (child != null) {
            parentLinks.link(person.getId(), child.getId());
        }
        audit.log(person.getOrgId(), person.getSiteId(), ActivityActions.PERSON_ADDED).by(user)
                .on("Person", person.getId()).change(null, clean + " as " + role).save();
        return person;
    }

    /** Changes role, status, subject or the generic-account flag by hand (an override, audited). */
    @Transactional
    public Person update(CurrentUser user, UUID personId, UpdateCommand cmd) {
        policy.requireRole(user, Role.SCHOOL_IT);
        Person person = ownSchoolPerson(user, personId);
        String before = person.getRole() + "/" + person.getStatus();
        PersonStatus oldStatus = person.getStatus();
        if (person.getRole().isNetwork() && cmd.status() != null && cmd.status() != person.getStatus()) {
            requireNotNetworkRole(person.getRole(), null);
        }

        if (cmd.name() != null) {
            if (cmd.name().isBlank() || cmd.name().length() > 200) {
                throw new BadRequestException("Please give the person's name.");
            }
            person.setDisplayName(cmd.name().trim());
        }
        if (cmd.email() != null && !cmd.email().trim().equalsIgnoreCase(person.getEmail())) {
            String clean = cmd.email().trim().toLowerCase();
            if (!clean.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                throw new BadRequestException("Please give a valid e-mail address.");
            }
            people.findByEmailIgnoreCase(clean).filter(o -> !o.getId().equals(person.getId())).ifPresent(o -> {
                throw new ConflictException(clean + " already has an account.");
            });
            Role effective = cmd.role() != null ? cmd.role() : person.getRole();
            String domain = clean.substring(clean.indexOf('@') + 1);
            boolean knownDomain = sites.findByOrgIdOrderByName(user.orgId()).stream()
                    .anyMatch(x -> x.isActive() && x.getDomain().equalsIgnoreCase(domain));
            if (effective != Role.STUDENT && effective != Role.PARENT && !knownDomain) {
                throw new BadRequestException("Staff with @" + domain + " cannot sign in: that domain does not belong to any school of the network.");
            }
            person.setEmail(clean);
        }
        if (cmd.role() != null && cmd.role() != person.getRole()) {
            requireNotNetworkRole(person.getRole(), cmd.role());
            applyRole(user, person.getId(), cmd.role());   // takes effect at once; logged, and the person is told
        }
        if (cmd.subjectId() != null) {
            subjects.findById(cmd.subjectId()).filter(s -> s.getSiteId().equals(person.getSiteId()))
                    .orElseThrow(() -> new BadRequestException("That subject does not belong to this school."));
            person.setSubjectId(cmd.subjectId());
        }
        if (cmd.generic() != null) {
            person.setGeneric(cmd.generic());
        }
        if (cmd.status() != null) {
            person.setStatus(cmd.status());
        }
        person.setOverridden(true); // lasts until the directory reports a change
        people.save(person);

        audit.log(person.getOrgId(), person.getSiteId(), ActivityActions.PERSON_UPDATED)
                .by(user).on("Person", person.getId()).subject(person.getSubjectId())
                .change(before, person.getRole() + "/" + person.getStatus()).save();

        announceStatusChange(person, oldStatus);
        return person;
    }

    /** Applies an approved role change (called by the approval handler). */
    @Transactional
    public void applyRole(CurrentUser approver, UUID personId, Role role) {
        Person person = people.findById(personId).filter(p -> p.getOrgId().equals(approver.orgId()))
                .orElseThrow(() -> new NotFoundException("The person"));
        String before = person.getRole().name();
        person.setRole(role);
        person.setOverridden(true);
        people.save(person);
        audit.log(person.getOrgId(), person.getSiteId(), ActivityActions.PERSON_UPDATED).by(approver)
                .on("Person", person.getId()).subject(person.getSubjectId()).change(before, role.name()).save();
        publisher.publishEvent(new PersonNotice(person.getId(), NotificationKind.SECURITY, "تم تغيير دورك",
                "Your role was changed", "Your role is now " + role.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT) + "."));
    }

    /**
     * Permanently deletes a person of the user's school. Not allowed for yourself or for Leadership/School IT
     * accounts. A person who already has records (files, issues, marks, messages) cannot be deleted without
     * losing history, so the call is refused and School IT should set their status to Left instead.
     */
    @Transactional
    public void delete(CurrentUser user, UUID personId) {
        policy.requireRole(user, Role.SCHOOL_IT);
        Person person = ownSchoolPerson(user, personId);
        if (person.getId().equals(user.personId())) {
            throw new BadRequestException("You cannot delete your own account.");
        }
        if (person.getRole() == Role.LEADERSHIP || person.getRole() == Role.EAA_ADMIN || person.getRole() == Role.SCHOOL_IT) {
            throw new BadRequestException("Leadership and School IT accounts cannot be deleted here.");
        }
        String summary = person.getEmail() + " (" + person.getRole() + ")";
        try {
            people.delete(person);
            people.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ConflictException(person.getDisplayName()
                    + " already has records in Hafez, so they cannot be deleted. Set their status to Left instead.");
        }
        audit.log(person.getOrgId(), person.getSiteId(), ActivityActions.PERSON_DELETED).by(user)
                .on("Person", personId).change(summary, null).save();
    }

    /** "Send welcome" for people marked "New: welcome not sent" (FR-11.5). */
    @Transactional
    public Person sendWelcome(CurrentUser user, UUID personId) {
        policy.requireRole(user, Role.SCHOOL_IT);
        Person person = ownSchoolPerson(user, personId);
        if (person.getWelcomeSentAt() != null) {
            throw new ConflictException("The welcome message was already sent.");
        }
        person.setWelcomeSentAt(Instant.now(clock));
        if (person.getStatus() == PersonStatus.NEW) {
            person.setStatus(PersonStatus.ACTIVE);
        }
        publisher.publishEvent(new PersonNotice(person.getId(), NotificationKind.SYSTEM, "مرحبًا بك في " + qa.sijill.sijill.shared.config.Brand.ar(),
                "Welcome to Hafez", "Open the welcome page to see how Hafez works."));
        audit.log(person.getOrgId(), person.getSiteId(), ActivityActions.WELCOME_SENT)
                .by(user).on("Person", person.getId()).save();
        return person;
    }

    /**
     * Reads the school's directory (read-only) and brings the people table in line (FR-11.5).
     * Returns the number of people created or changed.
     */
    @Transactional
    public int syncDirectory(CurrentUser user) {
        policy.requireRole(user, Role.SCHOOL_IT);
        WorkspaceConnector workspace = connector.getIfAvailable();
        if (workspace == null) {
            throw new ConflictException("The school workspace is not connected yet.");
        }
        Site site = sites.findById(user.siteId()).orElseThrow(() -> new NotFoundException("The school"));
        Map<String, Role> groupToRole = roleGroups.findByOrgId(site.getOrgId()).stream()
                .collect(Collectors.toMap(g -> g.getDirectoryGroup().toLowerCase(), RoleGroup::getRole, (a, b) -> a));

        int changed = 0;
        for (WorkspacePerson entry : workspace.getPeople(site.getDomain())) {
            if (applyDirectoryEntry(site, entry, groupToRole)) {
                changed++;
            }
        }
        audit.log(site.getOrgId(), site.getId(), ActivityActions.DIRECTORY_SYNCED)
                .by(user).on("Site", site.getId()).change(null, changed + " people changed").save();
        return changed;
    }

    /** Applies one directory entry. Returns true if the person was created or changed. */
    private boolean applyDirectoryEntry(Site site, WorkspacePerson entry, Map<String, Role> groupToRole) {
        Role directoryRole = roleFor(entry, groupToRole);
        PersonStatus directoryStatus = entry.suspended() ? PersonStatus.LEFT : null;

        Optional<Person> existing = people.findByEmailIgnoreCase(entry.email());
        if (existing.isEmpty()) {
            Person p = new Person();
            p.setOrgId(site.getOrgId());
            p.setSiteId(site.getId());
            p.setEmail(entry.email());
            p.setDisplayName(entry.displayName());
            p.setRole(directoryRole);
            p.setStatus(directoryStatus == null ? PersonStatus.NEW : directoryStatus);
            p.setDirectoryRole(directoryRole);
            p.setDirectoryStatus(p.getStatus());
            people.save(p);
            return true;
        }

        Person p = existing.get();
        PersonStatus oldStatus = p.getStatus();
        boolean directoryChanged = p.getDirectoryRole() != directoryRole
                || (directoryStatus != null && p.getDirectoryStatus() != directoryStatus);
        if (!directoryChanged && (p.isOverridden() || p.getDirectoryRole() != null)) {
            return false; // nothing new in the directory; an override stays until the directory changes
        }
        p.setOverridden(false);
        p.setDisplayName(entry.displayName());
        p.setRole(directoryRole);
        p.setDirectoryRole(directoryRole);
        if (directoryStatus != null) {
            p.setStatus(directoryStatus);
            p.setDirectoryStatus(directoryStatus);
        }
        people.save(p);
        announceStatusChange(p, oldStatus);
        return true;
    }

    private Role roleFor(WorkspacePerson entry, Map<String, Role> groupToRole) {
        List<Role> matched = entry.groups() == null ? List.of() : entry.groups().stream()
                .map(g -> groupToRole.get(g.toLowerCase())).filter(r -> r != null).toList();
        return ROLE_PRECEDENCE.stream().filter(matched::contains).findFirst().orElse(Role.TEACHER);
    }

    // ================================================================== coordinator action

    /** A coordinator records that she followed a person up (FR-11.5). Only the audit log is written. */
    @Transactional
    public void recordFollowUp(CurrentUser user, UUID personId, String note) {
        policy.requireRole(user, Role.COORDINATOR);
        Person person = ownSchoolPerson(user, personId);
        audit.log(person.getOrgId(), person.getSiteId(), ActivityActions.PERSON_FOLLOWED_UP)
                .by(user).on("Person", person.getId()).subject(person.getSubjectId()).change(null, note).save();
    }

    // ================================================================== role groups (directory group → role)

    @Transactional(readOnly = true)
    public List<RoleGroup> listRoleGroups(CurrentUser user) {
        policy.requireRole(user, Role.SCHOOL_IT);
        return roleGroups.findByOrgId(user.orgId());
    }

    /** Maps a workspace directory group to a Hafez role (section 4: each role comes from a directory group). */
    @Transactional
    public RoleGroup saveRoleGroup(CurrentUser user, String directoryGroup, Role role) {
        policy.requireRole(user, Role.SCHOOL_IT);
        if (directoryGroup == null || directoryGroup.isBlank() || role == null) {
            throw new BadRequestException("Please give the directory group and the role.");
        }
        requireNotNetworkRole(null, role);
        RoleGroup group = roleGroups.findByOrgId(user.orgId()).stream()
                .filter(g -> g.getDirectoryGroup().equalsIgnoreCase(directoryGroup.trim())).findFirst()
                .orElseGet(() -> {
                    RoleGroup g = new RoleGroup();
                    g.setOrgId(user.orgId());
                    g.setDirectoryGroup(directoryGroup.trim());
                    return g;
                });
        String before = group.getRole() == null ? null : group.getRole().name();
        group.setRole(role);
        roleGroups.save(group);
        audit.log(user.orgId(), user.siteId(), ActivityActions.ROLE_GROUP_CHANGED).by(user)
                .on("RoleGroup", group.getId()).change(before, role.name()).save();
        return group;
    }

    // ================================================================== helpers

    /** Tells the rest of the system a person's status changed (a leaver raises rule R8; a left person's issues move). */
    private void announceStatusChange(Person person, PersonStatus oldStatus) {
        if (oldStatus != person.getStatus()) {
            publisher.publishEvent(new PersonStatusChanged(person.getId(), person.getEmail(), person.getStatus().name()));
        }
    }

    /** School IT works inside one school: the network roles (EAA IT, Leadership) are never given or taken away here. */
    private static void requireNotNetworkRole(Role from, Role to) {
        if ((from != null && from.isNetwork()) || (to != null && to.isNetwork())) {
            throw new qa.sijill.sijill.shared.exception.ForbiddenException(
                    "The network roles (EAA IT and Leadership) are not managed by School IT.");
        }
    }

    private Person ownSchoolPerson(CurrentUser user, UUID personId) {
        Person person = people.findById(personId).orElseThrow(() -> new NotFoundException("The person"));
        if (!policy.sameSite(user, person.getSiteId())) {
            throw new NotFoundException("The person"); // do not reveal people of other schools
        }
        return person;
    }
}
