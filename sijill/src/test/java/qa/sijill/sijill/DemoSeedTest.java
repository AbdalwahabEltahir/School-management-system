package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import qa.sijill.sijill.academic.repository.AssessmentRepository;
import qa.sijill.sijill.academic.repository.AttendanceRecordRepository;
import qa.sijill.sijill.academic.repository.PointEntryRepository;
import qa.sijill.sijill.dashboard.repository.HealthScoreRepository;
import qa.sijill.sijill.organisation.model.Org;
import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.setup.dev.DemoNetworkSeeder;
import qa.sijill.sijill.setup.dev.DemoSchools;
import qa.sijill.sijill.support.repository.SupportTicketRepository;

/** The demo seed must give exactly five realistic schools (A to E), free of placeholders, with months of history. */
@TestPropertySource(properties = "sijill.demo-network=true")
class DemoSeedTest extends WorldTestSupport {

    @Autowired DemoNetworkSeeder network;
    @Autowired HealthScoreRepository snapshots;
    @Autowired AssessmentRepository assessments;
    @Autowired AttendanceRecordRepository attendance;
    @Autowired PointEntryRepository points;
    @Autowired SupportTicketRepository tickets;

    @Test
    void fiveRealisticSchoolsAreSeededWithMonthsOfHistory() {
        String uid = UUID.randomUUID().toString().substring(0, 6);
        DemoSchools.Profile first = DemoSchools.ALL.get(0);   // the first workspace of a network is school A itself
        Org org = setup.createOrganisation("Demo " + uid, first.name(), first.domain(), "lead@" + first.domain(), "Lead");
        network.seed(org);
        network.seed(org);   // running twice adds nothing

        List<Site> schools = sites.findByOrgIdOrderByName(org.getId()).stream().filter(s -> !s.isHeadOffice()).toList();
        assertThat(schools).extracting(Site::getName).containsExactlyInAnyOrderElementsOf(
                DemoSchools.ALL.stream().map(DemoSchools.Profile::name).toList());

        LocalDate today = LocalDate.now();
        for (Site s : schools) {
            if (s.getName().equals(first.name())) {
                continue;   // school A is built by the dev seeder, which has its own test of accounts
            }
            assertThat(s.getDomain()).as("domain of " + s.getName()).endsWith(".edu.qa");
            assertThat(files.countBySiteIdAndTrashedAtIsNull(s.getId())).as(s.getName() + " files").isPositive();
            var history = snapshots.findBySiteIdAndScoreDateAfterOrderByScoreDateAsc(s.getId(), today.minusDays(125));
            assertThat(history).as(s.getName() + " score history").hasSizeGreaterThan(100);
            assertThat(history.get(history.size() - 1).getScore()).as(s.getName() + " improves over the months")
                    .isGreaterThan(history.get(0).getScore());
            assertThat(assessments.findBySiteIdOrderByTermAscNameAsc(s.getId())).as(s.getName() + " weekly grades").hasSizeGreaterThan(90);
            assertThat(points.findBySiteId(s.getId())).as(s.getName() + " points").isNotEmpty();
            assertThat(tickets.findTop200BySiteIdOrderByUpdatedAtDesc(s.getId())).as(s.getName() + " tickets").isNotEmpty();

            // no placeholder people
            for (Person p : people.findBySiteIdOrderByDisplayName(s.getId())) {
                assertThat(p.getEmail()).doesNotContain("gmail.com").doesNotContain(".test");
                assertThat(p.getDisplayName()).doesNotMatch("(?i).*(teacher\\d|student|parent ·|check emalis).*");
            }
        }
        // several months of attendance across the network
        assertThat(attendance.count()).isGreaterThan(1000);
    }
}
