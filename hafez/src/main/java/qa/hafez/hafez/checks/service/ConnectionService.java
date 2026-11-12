package qa.hafez.hafez.checks.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.organisation.model.Site;
import qa.hafez.hafez.organisation.repository.SiteRepository;
import qa.hafez.hafez.shared.events.AuditPublisher;
import qa.hafez.hafez.shared.exception.ConflictException;
import qa.hafez.hafez.shared.exception.NotFoundException;
import qa.hafez.hafez.shared.model.JobRun;
import qa.hafez.hafez.shared.model.JobType;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.repository.JobRunRepository;
import qa.hafez.hafez.shared.security.AccessPolicy;
import qa.hafez.hafez.shared.security.CurrentUser;
import qa.hafez.hafez.workspace.WorkspaceConnector;

/**
 * The connection screen's logic (FR-8.2): is a workspace connector installed and switched on, test it,
 * disconnect and reconnect. Kept apart from {@link CheckRunService} (which runs checks) because these are
 * the <b>risky actions</b> and have their own permissions: School IT of that school, or Leadership.
 */
@Service
@RequiredArgsConstructor
public class ConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);

    private final SiteRepository sites;
    private final JobRunRepository runs;
    private final AuditPublisher audit;
    private final AccessPolicy policy;
    private final ObjectProvider<WorkspaceConnector> connector;
    private final Clock clock;

    /** The connection screen's facts: is a connector installed, is it switched on, when did the first check run. */
    public record ConnectionStatus(boolean connectorInstalled, boolean checksEnabled, Instant firstCheckAt,
                                   JobRun lastRun) {}

    public ConnectionStatus status(CurrentUser user, UUID siteId) {
        Site site = connectionSite(user, siteId);
        JobRun last = runs.findTop20BySiteIdAndTypeInOrderByStartedAtDesc(siteId, JobType.CHECKS).stream()
                .findFirst().orElse(null);
        return new ConnectionStatus(connector.getIfAvailable() != null, site.isChecksEnabled(), site.getFirstCheckAt(), last);
    }

    /** "Disconnect" (checks stop; the UI shows a red banner) and "Reconnect". Audited. */
    @Transactional
    public void setEnabled(CurrentUser user, UUID siteId, boolean enabled) {
        Site site = connectionSite(user, siteId);
        site.setChecksEnabled(enabled);
        audit.log(site.getOrgId(), siteId, "CONNECTION_" + (enabled ? "ENABLED" : "DISABLED")).by(user)
                .on("Site", siteId).save();
    }

    /**
     * "Test connection": performs a harmless <i>read</i> against the workspace. The connector interface has
     * no write operations at all, so a write cannot be attempted by Hafez (design principle 1); proving
     * that the workspace itself also refuses writes needs the real connector and is part of its test suite.
     */
    public String testConnection(CurrentUser user, UUID siteId) {
        Site site = connectionSite(user, siteId);
        WorkspaceConnector workspace = connector.getIfAvailable();
        if (workspace == null) {
            throw new ConflictException("The school workspace is not connected yet.");
        }
        try {
            workspace.listChanges(site.getDomain(), Instant.now(clock));
            return "Connected. Hafez can read the workspace and has no way to change it.";
        } catch (RuntimeException e) {
            log.warn("Connection test failed for {}: {}", site.getName(), e.getMessage());
            throw new ConflictException("The connection test failed. Please check the connection settings.");
        }
    }

    private Site connectionSite(CurrentUser user, UUID siteId) {
        Site site = sites.findById(siteId).orElseThrow(() -> new NotFoundException("The school"));
        policy.requireRole(user, Role.SCHOOL_IT, Role.LEADERSHIP);
        policy.requireSiteAccess(user, siteId, site.getOrgId());
        return site;
    }

}
