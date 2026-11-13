package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import qa.sijill.sijill.academic.model.Section;
import qa.sijill.sijill.academic.model.TeachingAssignment;
import qa.sijill.sijill.academic.repository.SectionRepository;
import qa.sijill.sijill.academic.repository.TeachingAssignmentRepository;
import qa.sijill.sijill.academic.service.MarksService;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.service.PeopleService;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.DigestMode;
import qa.sijill.sijill.shared.model.Language;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.support.dto.SupportDtos;
import qa.sijill.sijill.support.service.SupportService;

/** Support tickets (staff to School IT, principals also to Leadership), personal settings and alerts. */
class SupportAndSettingsTest extends WorldTestSupport {

    @Autowired SupportService support;
    @Autowired PeopleService peopleService;
    @Autowired MarksService marksService;
    @Autowired SectionRepository sections;
    @Autowired TeachingAssignmentRepository assignments;

    private Person headOfficeAdmin(World w) {
        return person(w.org(), sites.findById(w.leader().getSiteId()).orElseThrow(),
                "eaa-" + UUID.randomUUID() + "@ho.edu.qa", "Head Office Admin", Role.EAA_ADMIN, null);
    }

    private Person leadership(World w) {
        return person(w.org(), sites.findById(w.leader().getSiteId()).orElseThrow(),
                "lead-" + UUID.randomUUID() + "@ho.edu.qa", "Network Leader", Role.LEADERSHIP, null);
    }

    private SupportDtos.CreateRequest request(String title, String audience) {
        return new SupportDtos.CreateRequest(title, "Something needs help.", "TECHNICAL", "HIGH", audience, null, null);
    }

    // ------------------------------------------------------------------ tickets to School IT

    @Test
    void aTeachersTicketGoesToTheirSchoolItAndBack() {
        World w = world();
        Person otherIt = person(w.org(), newSchool(w.org(), "Other " + UUID.randomUUID(), "o-" + UUID.randomUUID() + ".edu.qa"),
                "it2-" + UUID.randomUUID() + "@o.edu.qa", "Other IT", Role.SCHOOL_IT, null);

        var ticket = support.create(user(w.teacher()), request("Projector will not connect", "SCHOOL_IT"));
        assertThat(ticket.audience()).isEqualTo("SCHOOL_IT");
        assertThat(count(w.it(), NotificationKind.TICKET)).isEqualTo(1);               // their School IT is told
        assertThat(count(otherIt, NotificationKind.TICKET)).isZero();                  // another school's is not
        assertThat(count(w.principal(), NotificationKind.TICKET)).isZero();
        assertThat(count(w.leader(), NotificationKind.TICKET)).isZero();               // head office is not involved

        // who sees it: the teacher, their School IT, and (read only) the principal. Nobody else.
        assertThat(support.list(user(w.teacher()))).extracting(SupportDtos.TicketView::id).containsExactly(ticket.id());
        assertThat(support.list(user(w.it()))).extracting(SupportDtos.TicketView::id).contains(ticket.id());
        assertThat(support.get(user(w.principal()), ticket.id()).canReply()).isFalse();
        assertThatThrownBy(() -> support.reply(user(w.principal()), ticket.id(), "fyi")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> support.get(user(w.coordinator()), ticket.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> support.get(user(otherIt), ticket.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> support.get(user(w.leader()), ticket.id())).isInstanceOf(NotFoundException.class);

        support.reply(user(w.it()), ticket.id(), "On it.");
        assertThat(support.get(user(w.teacher()), ticket.id()).ticket().status()).isEqualTo("IN_PROGRESS");
        assertThat(count(w.teacher(), NotificationKind.TICKET)).isEqualTo(1);
        assertThat(support.assignees(user(w.it()))).extracting(SupportDtos.Assignee::id).containsExactly(w.it().getId());
        support.assign(user(w.it()), ticket.id(), w.it().getId());

        assertThatThrownBy(() -> support.setStatus(user(w.teacher()), ticket.id(), "CLOSED")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> support.setStatus(user(otherIt), ticket.id(), "CLOSED")).isInstanceOf(NotFoundException.class);
        support.setStatus(user(w.it()), ticket.id(), "CLOSED");
        assertThat(support.get(user(w.teacher()), ticket.id()).canReply()).isFalse();
        assertThatThrownBy(() -> support.reply(user(w.teacher()), ticket.id(), "one more thing")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void whoMayOpenWhichKindOfTicket() {
        World w = world();
        assertThatThrownBy(() -> support.create(user(w.teacher()), request("x", "LEADERSHIP"))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> support.create(user(w.coordinator()), request("x", "LEADERSHIP"))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> support.create(user(w.it()), request("x", "SCHOOL_IT"))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> support.list(user(person(w.org(), w.school(), "s-" + UUID.randomUUID() + "@f.example", "Some Student", Role.STUDENT, null))))
                .isInstanceOf(ForbiddenException.class);
        assertThat(support.create(user(w.coordinator()), request("Account", "SCHOOL_IT")).audience()).isEqualTo("SCHOOL_IT");
        assertThat(support.create(user(w.principal()), request("Our IT", "SCHOOL_IT")).audience()).isEqualTo("SCHOOL_IT");
    }

    // ------------------------------------------------------------------ principals to Leadership

    @Test
    void aPrincipalCanAlsoWriteToLeadership() {
        World w = world();
        Person admin = headOfficeAdmin(w);
        Person lead = leadership(w);

        var ticket = support.create(user(w.principal()), request("Drive will not connect", "LEADERSHIP"));
        assertThat(count(admin, NotificationKind.TICKET)).isEqualTo(1);
        assertThat(count(lead, NotificationKind.TICKET)).isEqualTo(1);
        assertThat(count(w.it(), NotificationKind.TICKET)).isZero();                   // School IT is not involved

        // Leadership and EAA administrators see it across schools and handle it
        assertThat(support.list(user(lead))).extracting(SupportDtos.TicketView::id).contains(ticket.id());
        assertThat(support.get(user(lead), ticket.id()).canManage()).isTrue();
        support.reply(user(lead), ticket.id(), "We are looking into it.");
        assertThat(count(w.principal(), NotificationKind.TICKET)).isEqualTo(1);
        assertThat(support.assignees(user(lead))).extracting(SupportDtos.Assignee::id).contains(lead.getId(), admin.getId());
        support.assign(user(admin), ticket.id(), lead.getId());
        support.setStatus(user(lead), ticket.id(), "CLOSED");
        assertThat(support.get(user(w.principal()), ticket.id()).ticket().status()).isEqualTo("CLOSED");

        // School IT of the same school cannot read or handle a ticket to Leadership
        assertThatThrownBy(() -> support.get(user(w.it()), ticket.id())).isInstanceOf(NotFoundException.class);
        assertThat(support.list(user(w.it()))).extracting(SupportDtos.TicketView::id).doesNotContain(ticket.id());

        // and Leadership does not see tickets that were only written to a School IT
        var internal = support.create(user(w.teacher()), request("Local issue", "SCHOOL_IT"));
        assertThat(support.list(user(lead))).extracting(SupportDtos.TicketView::id).doesNotContain(internal.id());
        assertThatThrownBy(() -> support.get(user(lead), internal.id())).isInstanceOf(NotFoundException.class);

        // a principal of another school of the network does not see it either
        Person other = person(w.org(), newSchool(w.org(), "Other " + UUID.randomUUID(), "o-" + UUID.randomUUID() + ".edu.qa"),
                "p2-" + UUID.randomUUID() + "@o.edu.qa", "Other Principal", Role.PRINCIPAL, null);
        assertThatThrownBy(() -> support.get(user(other), ticket.id())).isInstanceOf(NotFoundException.class);
        assertThat(support.list(user(other))).isEmpty();
    }

    @Test
    void attachmentsAreCheckedByTypeAndSize() {
        World w = world();
        String text = Base64.getEncoder().encodeToString("hello".getBytes());
        assertThatThrownBy(() -> support.create(user(w.teacher()),
                new SupportDtos.CreateRequest("t", "d", "OTHER", "LOW", "SCHOOL_IT", "run.exe", text))).isInstanceOf(BadRequestException.class);
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> support.create(user(w.teacher()),
                new SupportDtos.CreateRequest("t", "d", "OTHER", "LOW", "SCHOOL_IT", "big.pdf", Base64.getEncoder().encodeToString(big)))).isInstanceOf(BadRequestException.class);

        var ok = support.create(user(w.teacher()), new SupportDtos.CreateRequest("t", "d", "OTHER", "LOW", "SCHOOL_IT", "../../notes.txt", text));
        assertThat(ok.attachmentName()).isEqualTo("notes.txt");                          // any path is stripped
        assertThat(support.attachment(user(w.it()), ok.id()).getData()).isEqualTo("hello".getBytes());
        assertThat(support.attachment(user(w.teacher()), ok.id()).getContentType()).isEqualTo("text/plain");
    }

    // ------------------------------------------------------------------ settings and alerts

    @Test
    void aSwitchedOffAlertIsNotDeliveredButSecurityAlertsAlwaysAre() {
        World w = world();
        peopleService.updatePreferences(user(w.it()), Language.EN, DigestMode.PER_ISSUE, List.of("TICKET", "SECURITY"));
        assertThat(people.findById(w.it().getId()).orElseThrow().mutedSet()).containsExactly(NotificationKind.TICKET); // security cannot be muted

        support.create(user(w.teacher()), request("Muted", "SCHOOL_IT"));
        assertThat(count(w.it(), NotificationKind.TICKET)).isZero();

        peopleService.updatePreferences(user(w.it()), null, null, List.of());              // everything back on
        support.create(user(w.teacher()), request("Heard", "SCHOOL_IT"));
        assertThat(count(w.it(), NotificationKind.TICKET)).isEqualTo(1);

        assertThatThrownBy(() -> peopleService.updatePreferences(user(w.it()), null, null, List.of("NOT_A_KIND"))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void profileDetailsAndPhotoAreValidated() {
        World w = world();
        var me = peopleService.updateProfile(user(w.teacher()), "  Tariq Al-Marri ", "+974 5555 1234");
        assertThat(me.getDisplayName()).isEqualTo("Tariq Al-Marri");
        assertThat(me.getPhone()).isEqualTo("+974 5555 1234");
        assertThatThrownBy(() -> peopleService.updateProfile(user(w.teacher()), "T", null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> peopleService.updateProfile(user(w.teacher()), "Tariq", "call me <b>")).isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> peopleService.setPhoto(user(w.teacher()), Base64.getEncoder().encodeToString("<html>".getBytes())))
                .isInstanceOf(BadRequestException.class);                                   // not an image, whatever the name says
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        var withPhoto = peopleService.setPhoto(user(w.teacher()), Base64.getEncoder().encodeToString(png));
        assertThat(withPhoto.getPhotoType()).isEqualTo("image/png");
        assertThat(peopleService.clearPhoto(user(w.teacher())).getPhoto()).isNull();
    }

    @Test
    void teachersWhoHaveNotSubmittedWeeklyGradesAreReminded() {
        World w = world();
        Section section = new Section();
        section.setOrgId(w.org().getId());
        section.setSiteId(w.school().getId());
        section.setGrade(8);
        section.setName("A");
        sections.save(section);
        TeachingAssignment ta = new TeachingAssignment();
        ta.setOrgId(w.org().getId());
        ta.setSiteId(w.school().getId());
        ta.setSectionId(section.getId());
        ta.setSubjectId(w.maths().getId());
        ta.setTeacherId(w.teacher().getId());
        assignments.save(ta);

        marksService.remindWeekly();
        assertThat(count(w.teacher(), NotificationKind.GRADES)).isEqualTo(1);
    }
}
