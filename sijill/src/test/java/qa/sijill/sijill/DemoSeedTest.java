package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import qa.sijill.sijill.dashboard.repository.HealthScoreRepository;
import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.setup.dev.DemoAcademySeeder;
import qa.sijill.sijill.setup.dev.DemoNetworkSeeder;

/** The demo seeders must run cleanly on a real database and leave schools with files, scores and history. */
@TestPropertySource(properties = "sijill.demo-network=true")
class DemoSeedTest extends WorldTestSupport {

    @Autowired DemoNetworkSeeder network;
    @Autowired DemoAcademySeeder academy;
    @Autowired HealthScoreRepository snapshots;

    @Test
    void demoSchoolsAreSeededWithFilesAndHistory() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 6);
        setup.createOrganisation("Demo " + uid, "HQ", "hq-" + uid + ".test", "lead@hq-" + uid + ".test", "Lead");
        network.run(null);
        academy.run(null);

        for (String name : new String[] {"School C", "School D", "School E", "School F", "Demo Academy"}) {
            Site s = sites.findAll().stream().filter(x -> x.getName().equals(name)).findFirst().orElseThrow();
            assertThat(files.countBySiteIdAndTrashedAtIsNull(s.getId())).as(name + " files").isPositive();
            assertThat(snapshots.findBySiteIdAndScoreDateAfterOrderByScoreDateAsc(s.getId(), java.time.LocalDate.now().minusDays(30)))
                    .as(name + " history").hasSizeGreaterThan(3);
        }
    }
}
