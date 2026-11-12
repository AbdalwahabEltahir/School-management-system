package qa.sijill.sijill.checks.dto;

import java.time.Instant;

import qa.sijill.sijill.checks.service.ConnectionService;
import qa.sijill.sijill.shared.dto.JobRunView;

/** Response shapes of the connection API. */
public final class CheckDtos {

    private CheckDtos() {}

    /** The connection screen (FR-8.2): is a connector installed, is it switched on, the last run. */
    public record ConnectionView(boolean connectorInstalled, boolean checksEnabled, Instant firstCheckAt,
                                 JobRunView lastRun) {
        public static ConnectionView from(ConnectionService.ConnectionStatus s) {
            return new ConnectionView(s.connectorInstalled(), s.checksEnabled(), s.firstCheckAt(),
                    s.lastRun() == null ? null : JobRunView.from(s.lastRun()));
        }
    }

    public record MessageView(String message) {}
}
