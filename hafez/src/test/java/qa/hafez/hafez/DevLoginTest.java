package qa.hafez.hafez;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
@SpringBootTest(properties = "hafez.jobs.enabled=false")
class DevLoginTest {

    @Autowired WebApplicationContext context;
    @Autowired qa.hafez.hafez.organisation.repository.SiteRepository sites;

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

    @Autowired qa.hafez.hafez.people.repository.PersonRepository people;

    @Test
    void aStudentActivatesWithTheSchoolsCodeAndASecondDeviceSignsInWithTheSamePassword() throws Exception {
        var student = people.findAll().stream().filter(p -> p.getRole() == qa.hafez.hafez.shared.model.Role.STUDENT && p.canSignIn()).findFirst().orElseThrow();
        MockHttpSession it = signInAs("it");
        String issued = mvc().perform(post("/api/invitations").with(csrf()).session(it).contentType("application/json")
                        .content("{\"kind\":\"STUDENT\",\"studentIds\":[\"" + student.getId() + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").exists()).andReturn().getResponse().getContentAsString();
        var m = java.util.regex.Pattern.compile("\"code\":\"([^\"]+)\"").matcher(issued); m.find(); String code = m.group(1);

        MockHttpSession first = new MockHttpSession();
        mvc().perform(post("/api/auth/redeem/student").with(csrf()).session(first).contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"password\":\"correct horse\"}"))
                .andExpect(status().isOk());
        mvc().perform(get("/api/me").session(first)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));

        MockHttpSession second = new MockHttpSession();
        String login = "{\"email\":\"" + student.getEmail() + "\",\"password\":\"correct horse\"}";
        mvc().perform(post("/api/auth/login").with(csrf()).session(second).contentType("application/json").content(login)).andExpect(status().isOk());
        mvc().perform(get("/api/enrol/status").session(second)).andExpect(status().isOk()).andExpect(jsonPath("$.studentCode").exists());
        mvc().perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(login.replace("correct horse", "wrong horse")))
                .andExpect(status().isBadRequest());
        mvc().perform(post("/api/auth/login").contentType("application/json").content(login)).andExpect(status().isForbidden()); // no CSRF token
        // the old open registration is gone
        mvc().perform(post("/api/auth/register/student").with(csrf()).contentType("application/json").content("{}")).andExpect(status().is4xxClientError());
        mvc().perform(post("/api/auth/redeem/student").with(csrf()).contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"password\":\"another one\"}")).andExpect(status().isBadRequest()); // used once
    }

    @Test
    void withoutSigningInTheApiIsClosed() throws Exception {
        mvc().perform(get("/api/me")).andExpect(status().isUnauthorized()); // the page then shows the welcome screen
        mvc().perform(get("/api/issues")).andExpect(status().isUnauthorized());
        mvc().perform(get("/api/auth/options")).andExpect(status().isOk()); // public: the welcome page needs it
        mvc().perform(get("/")).andExpect(status().isOk());                  // the welcome page itself
    }

    @Test
    void anUnknownRoleIsRefused() throws Exception {
        mvc().perform(get("/dev/login/wizard")).andExpect(status().isNotFound());
    }
}
