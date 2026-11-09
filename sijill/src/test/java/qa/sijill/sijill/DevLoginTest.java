package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * The development sign-in: {@code /dev/login/<role>} signs in as that role without a password, and the
 * session then reaches the real API. Runs in the {@code dev} profile, which also seeds the demo school.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
@SpringBootTest(properties = "sijill.jobs.enabled=false")
class DevLoginTest {

    @Autowired WebApplicationContext context;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Signs in as the role and returns the session, as the browser would hold it. */
    private MockHttpSession signInAs(String role) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc().perform(get("/dev/login/" + role).session(session))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/api/me"));
        return session;
    }

    @Test
    void everyRoleCanSignInByNameAndReachTheApi() throws Exception {
        String[][] expected = {{"leadership", "LEADERSHIP"}, {"it", "SCHOOL_IT"}, {"principal", "PRINCIPAL"},
                {"coordinator", "COORDINATOR"}, {"teacher", "TEACHER"}};
        for (String[] e : expected) {
            mvc().perform(get("/api/me").session(signInAs(e[0])))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(e[1]));
        }
    }

    @Test
    void theSeededTeacherSeesTheirOwnProblemFilesAndNobodyElsesView() throws Exception {
        // the teacher owns three files that break rules; the seed ran the real rule engine
        String body = mvc().perform(get("/api/issues").session(signInAs("teacher")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("OWNER_NOTIFIED");

        // School IT sees counts only, never individual issues
        mvc().perform(get("/api/issues").session(signInAs("it"))).andExpect(status().isForbidden());
    }

    @Test
    void withoutSigningInTheApiIsClosed() throws Exception {
        mvc().perform(get("/api/me")).andExpect(status().is3xxRedirection()); // sent to the login page
    }

    @Test
    void anUnknownRoleIsRefused() throws Exception {
        mvc().perform(get("/dev/login/wizard")).andExpect(status().isNotFound());
    }
}
