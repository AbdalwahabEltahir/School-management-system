package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import qa.sijill.sijill.academic.model.Enrolment;
import qa.sijill.sijill.academic.model.Section;
import qa.sijill.sijill.academic.model.TeachingAssignment;
import qa.sijill.sijill.academic.repository.EnrolmentRepository;
import qa.sijill.sijill.academic.repository.SectionRepository;
import qa.sijill.sijill.academic.repository.TeachingAssignmentRepository;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.service.ParentLinks;
import qa.sijill.sijill.pickup.dto.PickupDtos;
import qa.sijill.sijill.pickup.service.PickupService;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;

/** Student pick-up: approved collectors, "I have arrived", staff verification and release, and the chain-of-custody log. */
class PickupTest extends WorldTestSupport {

    @Autowired PickupService pickup;
    @Autowired SectionRepository sections;
    @Autowired EnrolmentRepository enrolments;
    @Autowired TeachingAssignmentRepository assignments;
    @Autowired ParentLinks links;
    @Autowired JdbcTemplate jdbc;

    private record Scene(World w, Person parent, Person child, Section section) {}

    private Scene scene() {
        World w = world();
        Person parent = person(w.org(), w.school(), "mum-" + UUID.randomUUID() + "@family.example", "Maha Al-Sulaiti", Role.PARENT, null);
        Person child = person(w.org(), w.school(), "amal-" + UUID.randomUUID() + "@" + w.school().getDomain(), "Amal Al-Sulaiti", Role.STUDENT, null);
        links.link(parent.getId(), child.getId());
        Section section = new Section();
        section.setOrgId(w.org().getId());
        section.setSiteId(w.school().getId());
        section.setGrade(7);
        section.setName("B");
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

    private PickupDtos.CollectorView driver(Scene s) {
        return pickup.addCollector(user(s.parent()), s.child().getId(),
                new PickupDtos.CollectorRequest("Yusuf Driver", "DRIVER", "+974 5555 0000", "QID-1234567890"));
    }

    @Test
    void aChildIsReleasedOnlyAfterStaffVerifyAnApprovedCollector() {
        Scene s = scene();
        var collector = driver(s);
        assertThat(collector.idLast4()).isEqualTo("7890");                                   // only the last four are kept

        var arrival = pickup.arrive(user(s.parent()), s.child().getId(), collector.id());
        assertThat(arrival.status()).isEqualTo("ARRIVED");
        assertThat(count(s.w().teacher(), NotificationKind.COLLECTION)).isEqualTo(1);        // the class teacher is alerted
        assertThat(count(s.w().coordinator(), NotificationKind.COLLECTION)).isEqualTo(1);    // and the coordinator
        assertThat(count(s.w().principal(), NotificationKind.COLLECTION)).isZero();

        var board = pickup.board(user(s.w().teacher()));
        assertThat(board.arrivals()).extracting(PickupDtos.RequestView::id).containsExactly(arrival.id());
        assertThat(board.canAct()).isTrue();

        // a child cannot be released before the ID was verified
        assertThatThrownBy(() -> pickup.release(user(s.w().teacher()), arrival.id())).isInstanceOf(ConflictException.class);

        pickup.verify(user(s.w().teacher()), arrival.id());
        assertThat(pickup.board(user(s.w().coordinator())).pending()).hasSize(1);
        var released = pickup.release(user(s.w().coordinator()), arrival.id());
        assertThat(released.status()).isEqualTo("RELEASED");

        // the chain-of-custody record: student, collector, exact time and the staff member
        var log = pickup.log(user(s.w().principal()), null, null);
        assertThat(log).hasSize(1);
        assertThat(log.get(0).student()).isEqualTo("Amal Al-Sulaiti");
        assertThat(log.get(0).collector()).isEqualTo("Yusuf Driver");
        assertThat(log.get(0).releasedBy()).isEqualTo(s.w().coordinator().getDisplayName());
        assertThat(log.get(0).releasedAt()).isNotNull();
        assertThat(pickup.board(user(s.w().teacher())).completed()).hasSize(1);
        assertThat(count(s.parent(), NotificationKind.COLLECTION)).isEqualTo(1);             // the parent is told
        assertThat(pickup.log(user(s.parent()), null, null)).hasSize(1);                    // and can see the record

        // the same child can be collected again another day, or once finished, again now
        assertThat(pickup.arrive(user(s.parent()), s.child().getId(), null).collector()).isEqualTo("Maha Al-Sulaiti");
    }

    @Test
    void onlyApprovedCollectorsCanBeReleased() {
        Scene s = scene();
        Scene other = scene();
        var strangers = driver(other);                                                       // approved for someone else's child
        assertThatThrownBy(() -> pickup.arrive(user(s.parent()), s.child().getId(), strangers.id())).isInstanceOf(BadRequestException.class);

        var mine = driver(s);
        var arrival = pickup.arrive(user(s.parent()), s.child().getId(), mine.id());
        pickup.removeCollector(user(s.parent()), mine.id());                                 // approval withdrawn before release
        assertThatThrownBy(() -> pickup.verify(user(s.w().teacher()), arrival.id())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> pickup.release(user(s.w().teacher()), arrival.id())).isInstanceOf(ConflictException.class);
        assertThat(pickup.decline(user(s.w().teacher()), arrival.id(), "Not on the approved list any more").status()).isEqualTo("DECLINED");
        assertThat(pickup.log(user(s.w().principal()), null, null)).isEmpty();
    }

    @Test
    void whoMayDoWhat() {
        Scene s = scene();
        Scene other = scene();
        assertThatThrownBy(() -> pickup.addCollector(user(other.parent()), s.child().getId(),
                new PickupDtos.CollectorRequest("X Person", "DRIVER", null, "12345678"))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> pickup.arrive(user(s.w().teacher()), s.child().getId(), null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> pickup.addCollector(user(s.parent()), s.child().getId(),
                new PickupDtos.CollectorRequest("A", "DRIVER", null, "12345678"))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> pickup.addCollector(user(s.parent()), s.child().getId(),
                new PickupDtos.CollectorRequest("Short Id", "DRIVER", null, "12"))).isInstanceOf(BadRequestException.class);

        var arrival = pickup.arrive(user(s.parent()), s.child().getId(), null);
        assertThatThrownBy(() -> pickup.arrive(user(s.parent()), s.child().getId(), null)).isInstanceOf(ConflictException.class); // one at a time

        // the principal can read the board but cannot verify, release or decline
        assertThat(pickup.board(user(s.w().principal())).canAct()).isFalse();
        assertThatThrownBy(() -> pickup.verify(user(s.w().principal()), arrival.id())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> pickup.board(user(s.parent()))).isInstanceOf(ForbiddenException.class);
        // a teacher of another class, and another school's staff, do not even see it
        Person otherTeacher = person(s.w().org(), s.w().school(), "t2-" + UUID.randomUUID() + "@" + s.w().school().getDomain(), "Other Teacher", Role.TEACHER, s.w().maths());
        assertThat(pickup.board(user(otherTeacher)).arrivals()).isEmpty();
        assertThatThrownBy(() -> pickup.verify(user(otherTeacher), arrival.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> pickup.verify(user(other.w().teacher()), arrival.id())).isInstanceOf(NotFoundException.class);
        // declining needs a reason; the parent can withdraw their own request
        assertThatThrownBy(() -> pickup.decline(user(s.w().teacher()), arrival.id(), " ")).isInstanceOf(BadRequestException.class);
        assertThat(pickup.cancel(user(s.parent()), arrival.id()).status()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> pickup.cancel(user(other.parent()), arrival.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void theReleaseLogCannotBeChangedOrDeleted() {
        Scene s = scene();
        var arrival = pickup.arrive(user(s.parent()), s.child().getId(), null);
        pickup.verify(user(s.w().teacher()), arrival.id());
        pickup.release(user(s.w().teacher()), arrival.id());

        assertThatThrownBy(() -> jdbc.update("UPDATE pickup_release_log SET collector_name = 'Someone Else' WHERE request_id = ?", arrival.id()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("cannot be changed or deleted");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM pickup_release_log WHERE request_id = ?", arrival.id()))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pickup_release_log WHERE request_id = ?", Integer.class, arrival.id())).isEqualTo(1);
    }

    @Test
    void aSwitchedOffAlertIsRespectedForPickup() {
        Scene s = scene();
        people.findById(s.w().teacher().getId()).ifPresent(t -> {
            t.setMuted(java.util.Set.of(NotificationKind.COLLECTION));
            people.save(t);
        });
        pickup.arrive(user(s.parent()), s.child().getId(), null);
        assertThat(count(s.w().teacher(), NotificationKind.COLLECTION)).isZero();
        assertThat(count(s.w().coordinator(), NotificationKind.COLLECTION)).isEqualTo(1);
    }
}
