package qa.sijill.sijill.setup.dev;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.pickup.model.PickupCollector;
import qa.sijill.sijill.pickup.model.PickupRelease;
import qa.sijill.sijill.pickup.model.PickupRequest;
import qa.sijill.sijill.pickup.model.PickupStatus;
import qa.sijill.sijill.pickup.model.Relationship;
import qa.sijill.sijill.pickup.repository.PickupCollectorRepository;
import qa.sijill.sijill.pickup.repository.PickupReleaseRepository;
import qa.sijill.sijill.pickup.repository.PickupRequestRepository;
import qa.sijill.sijill.shared.util.SchoolCalendar;

/**
 * <b>Demo only.</b> Gives the pick-up screens something to show: approved collectors for a few children, one parent
 * waiting at the gate, one collector being checked, two children already released today, and a month of past releases
 * for the log. Written straight to the tables, like the rest of the demo history.
 */
@Component
@RequiredArgsConstructor
public class DemoPickup {

    private final PickupCollectorRepository collectors;
    private final PickupRequestRepository requests;
    private final PickupReleaseRepository releases;
    private final Clock clock;

    private static final String[][] PEOPLE = {
            {"Yusuf Al-Naimi", "DRIVER", "+974 5550 1101", "4821"}, {"Mariam Al-Kuwari", "RELATIVE", "+974 5550 1102", "7306"},
            {"Hassan Al-Marri", "RELATIVE", "+974 5550 2202", "1937"}, {"Ibrahim Al-Sada", "DRIVER", "+974 5550 3303", "5560"}};

    /**
     * @param students    the school's students
     * @param parentOf    the parent of each student, same order (null for a child without a linked parent)
     * @param classLabels the class label of each student, same order as {@code students}
     * @param sectionIds  the class id of each student, same order as {@code students}
     * @param staff       the coordinator who verifies and releases
     */
    public void seed(Site site, List<Person> students, List<Person> parentOf, List<String> classLabels, List<UUID> sectionIds, Person staff) {
        List<Integer> linked = new java.util.ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            if (parentOf.get(i) != null) {
                linked.add(i);
            }
        }
        if (linked.isEmpty()) {
            return;
        }
        Instant now = Instant.now(clock);
        PickupCollector[] approved = new PickupCollector[linked.size()];
        for (int k = 0; k < linked.size(); k++) {
            String[] c = PEOPLE[k % PEOPLE.length];
            approved[k] = collector(site, students.get(linked.get(k)), parentOf.get(linked.get(k)), c[0], Relationship.valueOf(c[1]), c[2], c[3]);
        }
        // today: one parent has just arrived, one child was released to a driver, one collector is being checked, one child is home
        PickupStatus[] today = {PickupStatus.ARRIVED, PickupStatus.RELEASED, PickupStatus.VERIFYING, PickupStatus.RELEASED};
        int[] minutesAgo = {4, 48, 11, 55};
        for (int k = 0; k < Math.min(linked.size(), today.length); k++) {
            int i = linked.get(k);
            PickupCollector by = k == 3 ? null : approved[k];
            request(site, students, classLabels, sectionIds, i, parentOf.get(i), by, today[k], now.minus(Duration.ofMinutes(minutesAgo[k])), staff);
        }

        // the last weeks: a couple of released children per school day, for the release log
        LocalDate day = LocalDate.now(clock.withZone(SchoolCalendar.ZONE)).minusDays(1);
        int made = 0;
        for (int back = 0; back < 20 && made < 14; back++, day = day.minusDays(1)) {
            if (!SchoolCalendar.isSchoolDay(day)) {
                continue;
            }
            Instant at = day.atTime(13, 20 + (made % 5) * 4).atZone(SchoolCalendar.ZONE).toInstant();
            for (int k = 0; k < Math.min(2, linked.size()); k++) {
                int i = linked.get((made + k) % linked.size());
                request(site, students, classLabels, sectionIds, i, parentOf.get(i), null, PickupStatus.RELEASED, at.plus(Duration.ofMinutes(k * 3)), staff);
            }
            made++;
        }
    }

    private PickupCollector collector(Site site, Person student, Person by, String name, Relationship rel, String phone, String last4) {
        PickupCollector c = new PickupCollector();
        c.setOrgId(site.getOrgId());
        c.setSiteId(site.getId());
        c.setStudentId(student.getId());
        c.setName(name);
        c.setRelationship(rel);
        c.setPhone(phone);
        c.setIdLast4(last4);
        c.setCreatedBy(by.getId());
        return collectors.save(c);
    }

    private void request(Site site, List<Person> students, List<String> labels, List<UUID> sectionIds, int index, Person parent,
                         PickupCollector collector, PickupStatus status, Instant arrived, Person staff) {
        Person student = students.get(index);
        PickupRequest r = new PickupRequest();
        r.setOrgId(site.getOrgId());
        r.setSiteId(site.getId());
        r.setStudentId(student.getId());
        r.setStudentName(student.getDisplayName());
        r.setSectionId(sectionIds.get(index));
        r.setSectionLabel(labels.get(index));
        r.setParentId(parent.getId());
        if (collector == null) {
            r.setCollectorName(parent.getDisplayName());
            r.setCollectorRelationship(Relationship.PARENT);
            r.setCollectorPhone(parent.getPhone());
        } else {
            r.setCollectorId(collector.getId());
            r.setCollectorName(collector.getName());
            r.setCollectorRelationship(collector.getRelationship());
            r.setCollectorIdLast4(collector.getIdLast4());
            r.setCollectorPhone(collector.getPhone());
        }
        r.setStatus(status);
        r.setArrivedAt(arrived);
        r.setUpdatedAt(arrived);
        if (status != PickupStatus.ARRIVED) {
            r.setVerifiedBy(staff.getId());
            r.setVerifiedByName(staff.getDisplayName());
            r.setVerifiedAt(arrived.plus(Duration.ofMinutes(2)));
            r.setUpdatedAt(r.getVerifiedAt());
        }
        if (status == PickupStatus.RELEASED) {
            Instant at = arrived.plus(Duration.ofMinutes(4));
            r.setDecidedBy(staff.getId());
            r.setDecidedByName(staff.getDisplayName());
            r.setDecidedAt(at);
            r.setUpdatedAt(at);
        }
        requests.save(r);
        if (status == PickupStatus.RELEASED) {
            PickupRelease log = new PickupRelease();
            log.setOrgId(site.getOrgId());
            log.setSiteId(site.getId());
            log.setRequestId(r.getId());
            log.setStudentId(student.getId());
            log.setStudentName(student.getDisplayName());
            log.setSectionLabel(labels.get(index));
            log.setCollectorName(r.getCollectorName());
            log.setCollectorRelationship(r.getCollectorRelationship());
            log.setReleasedBy(staff.getId());
            log.setReleasedByName(staff.getDisplayName());
            log.setReleasedAt(r.getDecidedAt());
            releases.save(log);
        }
    }
}
