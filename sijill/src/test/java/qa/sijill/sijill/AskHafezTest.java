package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import qa.sijill.sijill.academic.model.Enrolment;
import qa.sijill.sijill.academic.model.Section;
import qa.sijill.sijill.academic.model.TeachingAssignment;
import qa.sijill.sijill.academic.repository.EnrolmentRepository;
import qa.sijill.sijill.academic.repository.SectionRepository;
import qa.sijill.sijill.academic.repository.TeachingAssignmentRepository;
import qa.sijill.sijill.academic.service.AcademicAccess;
import qa.sijill.sijill.askhafez.AskHafezDtos.Reply;
import qa.sijill.sijill.askhafez.AskHafezService;
import qa.sijill.sijill.classroom.ClassroomDtos.HomeworkRequest;
import qa.sijill.sijill.classroom.HomeworkService;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.service.ParentLinks;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;

/** Ask Hafez: answers only what the asker may already see, a parent can switch it off per child, and every exchange is logged. */
class AskHafezTest extends WorldTestSupport {

    @Autowired AskHafezService ask;
    @Autowired HomeworkService homework;
    @Autowired SectionRepository sections;
    @Autowired EnrolmentRepository enrolments;
    @Autowired TeachingAssignmentRepository assignments;
    @Autowired ParentLinks links;
    @Autowired AcademicAccess access;
    @Autowired JdbcTemplate jdbc;

    private record Scene(World w, Person parent, Person child, Section section) {}

    private Scene scene() {
        World w = world();
        Person parent = person(w.org(), w.school(), "mum-" + UUID.randomUUID() + "@family.example", "Maha Al-Sulaiti", Role.PARENT, null);
        Person child = person(w.org(), w.school(), "bader-" + UUID.randomUUID() + "@" + w.school().getDomain(), "Bader Al-Sulaiti", Role.STUDENT, null);
        links.link(parent.getId(), child.getId());
        Section section = new Section();
        section.setOrgId(w.org().getId());
        section.setSiteId(w.school().getId());
        section.setGrade(5);
        section.setName("A");
        sections.save(section);
        Enrolment e = new Enrolment();
        e.setOrgId(w.org().getId());
        e.setSiteId(w.school().getId());
        e.setSectionId(section.getId());
        e.setStudentId(child.getId());
        enrolments.save(e);
        TeachingAssignment ta = new TeachingAssignment();
        ta.setOrgId(w.org().getId());
        ta.setSiteId(w.school().getId());
        ta.setSectionId(section.getId());
        ta.setSubjectId(w.maths().getId());
        ta.setTeacherId(w.teacher().getId());
        assignments.save(ta);
        return new Scene(w, parent, child, section);
    }

    private void setHomework(Scene s, String title, int daysFromToday) {
        homework.add(user(s.w().teacher()), new HomeworkRequest(s.section().getId(), s.w().maths().getId(), title, "", access.today().plusDays(daysFromToday)));
    }

    @Test
    void aParentSeesTheirChildsHomeworkAndAStudentTheirOwn() {
        Scene s = scene();
        setHomework(s, "Fractions worksheet", 1);

        Reply parent = ask.ask(user(s.parent()), "Is there homework due tomorrow?");
        assertThat(parent.answer()).contains("Bader Al-Sulaiti").contains("Fractions worksheet");
        assertThat(parent.links()).extracting(l -> l.href()).contains("#/homework");
        assertThat(parent.language()).isEqualTo("en");

        assertThat(ask.ask(user(s.child()), "what homework do i have tomorrow").answer()).contains("Fractions worksheet");

        Reply arabic = ask.ask(user(s.parent()), "هل يوجد واجب غدا؟");
        assertThat(arabic.language()).isEqualTo("ar");
        assertThat(arabic.answer()).contains("Fractions worksheet");
    }

    @Test
    void aParentWithoutThatChildSeesNothingOfIt() {
        Scene s = scene();
        setHomework(s, "Secret worksheet", 1);
        Person stranger = person(s.w().org(), s.w().school(), "other-" + UUID.randomUUID() + "@family.example", "Omar Stranger", Role.PARENT, null);
        assertThat(ask.ask(user(stranger), "Is there homework due tomorrow?").answer()).doesNotContain("Secret worksheet").doesNotContain("Bader");
    }

    @Test
    void aTeacherSeesWhoHasNotHandedInOnlyFromTheirOwnHomework() {
        Scene s = scene();
        setHomework(s, "Chapter 2 review", 0);
        Reply r = ask.ask(user(s.w().teacher()), "Who has not handed in homework this week?");
        assertThat(r.answer()).contains("Bader Al-Sulaiti").contains("Chapter 2 review");

        jdbc.update("INSERT INTO homework_done (homework_id, student_id) SELECT id, ? FROM homework WHERE section_id = ?", s.child().getId(), s.section().getId());
        assertThat(ask.ask(user(s.w().teacher()), "Who has not handed in homework this week?").answer()).doesNotContain("Bader");
    }

    @Test
    void pickUpStepsSensitiveTopicsAndUnknownQuestions() {
        Scene s = scene();
        assertThat(ask.ask(user(s.parent()), "How do I pick up my child early?").answer()).contains("Press Pick up");
        Reply sensitive = ask.ask(user(s.parent()), "My son is being bullied");
        assertThat(sensitive.answer()).contains("person").doesNotContain("Bader");
        assertThat(ask.ask(user(s.parent()), "What is the meaning of life?").answer()).startsWith("I don't know");
    }

    @Test
    void aParentSwitchesItOffForOneChildAndTheButtonAndAnswersStop() {
        Scene s = scene();
        assertThat(ask.status(user(s.child())).available()).isTrue();
        assertThat(ask.status(user(s.parent())).children()).extracting(c -> c.enabled()).containsExactly(true);

        ask.setChild(user(s.parent()), s.child().getId(), false);
        assertThat(ask.status(user(s.child())).available()).isFalse();
        assertThat(ask.status(user(s.child())).offByParent()).isTrue();
        assertThatThrownBy(() -> ask.ask(user(s.child()), "hello")).isInstanceOf(ForbiddenException.class);
        assertThat(ask.status(user(s.parent())).children()).extracting(c -> c.enabled()).containsExactly(false);
        assertThat(ask.status(user(s.parent())).available()).isTrue();                     // the parent's own helper stays

        ask.setChild(user(s.parent()), s.child().getId(), true);
        assertThat(ask.status(user(s.child())).available()).isTrue();
        assertThat(ask.ask(user(s.child()), "hello").answer()).contains("Ask Hafez");
    }

    @Test
    void onlyTheLinkedParentCanSwitchAndOtherRolesDoNotGetTheButton() {
        Scene s = scene();
        Person stranger = person(s.w().org(), s.w().school(), "other-" + UUID.randomUUID() + "@family.example", "Omar Stranger", Role.PARENT, null);
        assertThatThrownBy(() -> ask.setChild(user(stranger), s.child().getId(), false)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> ask.setChild(user(s.child()), s.child().getId(), false)).isInstanceOf(NotFoundException.class);
        assertThat(ask.status(user(s.w().principal())).available()).isFalse();
        assertThat(ask.status(user(s.w().teacher())).available()).isTrue();
        assertThatThrownBy(() -> ask.ask(user(s.w().principal()), "hello")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void everyExchangeIsLoggedAndOnlyTheAskerCanRateIt() {
        Scene s = scene();
        Reply r = ask.ask(user(s.parent()), "How do I pick up my child early?");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ask_hafez_log WHERE person_id = ? AND intent = 'PICKUP'", Integer.class, s.parent().getId())).isEqualTo(1);
        ask.rate(user(s.parent()), r.id(), true);
        assertThat(jdbc.queryForObject("SELECT rating FROM ask_hafez_log WHERE id = ?", Integer.class, r.id())).isEqualTo(1);
        assertThatThrownBy(() -> ask.rate(user(s.child()), r.id(), false)).isInstanceOf(NotFoundException.class);
    }
}
