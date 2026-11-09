package qa.sijill.sijill.shared.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Only principals and Leadership are offered the weekly report download. */
class RoleTest {

    @Test
    void onlyPrincipalAndLeadershipCanDownloadTheWeeklyReport() {
        assertThat(Role.PRINCIPAL.canDownloadWeeklyReport()).isTrue();
        assertThat(Role.LEADERSHIP.canDownloadWeeklyReport()).isTrue();
        assertThat(Role.TEACHER.canDownloadWeeklyReport()).isFalse();
        assertThat(Role.COORDINATOR.canDownloadWeeklyReport()).isFalse();
        assertThat(Role.SCHOOL_IT.canDownloadWeeklyReport()).isFalse();
    }
}
