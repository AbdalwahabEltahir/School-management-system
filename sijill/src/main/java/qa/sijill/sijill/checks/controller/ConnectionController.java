package qa.sijill.sijill.checks.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.checks.dto.CheckDtos;
import qa.sijill.sijill.checks.service.CheckRunService;
import qa.sijill.sijill.checks.service.ConnectionService;
import qa.sijill.sijill.shared.dto.JobRunView;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * The connection screen (FR-8.2) and its <b>risky actions</b>: test the connection, disconnect and
 * reconnect, run a check now. Kept apart from the ordinary settings so the permissions of these actions
 * (School IT of that school, or Leadership) are easy to review.
 */
@RestController
@RequestMapping("/api/connection/{siteId}")
@RequiredArgsConstructor
public class ConnectionController {

    private final CheckRunService checks;
    private final ConnectionService connection;

    @GetMapping
    public CheckDtos.ConnectionView status(CurrentUser user, @PathVariable UUID siteId) {
        return CheckDtos.ConnectionView.from(connection.status(user, siteId));
    }

    /** "Test connection": a harmless read; Hafez has no way to write to the workspace. */
    @PostMapping("/test")
    public CheckDtos.MessageView test(CurrentUser user, @PathVariable UUID siteId) {
        return new CheckDtos.MessageView(connection.testConnection(user, siteId));
    }

    /** "Disconnect": checks stop until it is reconnected. */
    @PostMapping("/disconnect")
    public void disconnect(CurrentUser user, @PathVariable UUID siteId) {
        connection.setEnabled(user, siteId, false);
    }

    @PostMapping("/reconnect")
    public void reconnect(CurrentUser user, @PathVariable UUID siteId) {
        connection.setEnabled(user, siteId, true);
    }

    /** "Run a full check now" / "Run the first check": returns at once; the run continues in the background. */
    @PostMapping("/check")
    public JobRunView runCheck(CurrentUser user, @PathVariable UUID siteId) {
        return JobRunView.from(checks.start(user, siteId));
    }

    /** "Recent checks with skipped files". */
    @GetMapping("/checks")
    public List<JobRunView> recent(CurrentUser user, @PathVariable UUID siteId) {
        return checks.recent(user, siteId).stream().map(JobRunView::from).toList();
    }
}
