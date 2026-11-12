package qa.sijill.sijill.checks.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.JobRun;
import qa.sijill.sijill.shared.model.JobType;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.repository.JobRunRepository;
import qa.sijill.sijill.shared.security.AccessPolicy;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.workspace.WorkspaceConnector;

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
    private final qa.sijill.sijill.approvals.service.ApprovalService approvals;

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
        if (enabled) {
            policy.requireRole(user, Role.SCHOOL_IT, Role.EAA_ADMIN);
            applyEnabled(user, siteId, true);
            return;
        }
        // Disconnecting Google Drive is an EAA administrator's decision and needs a second person's approval.
        policy.requireRole(user, Role.EAA_ADMIN);
        approvals.request(user, siteId, qa.sijill.sijill.approvals.ApprovalActions.DRIVE_DISCONNECT, siteId, null,
                "Disconnect Google Drive for " + site.getName());
    }

    /** Switches checks on or off without further permission checks (callers have already authorised it). */
    @Transactional
    public void applyEnabled(CurrentUser user, UUID siteId, boolean enabled) {
        Site site = sites.findById(siteId).orElseThrow(() -> new NotFoundException("The school"));
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
        policy.requireRole(user, Role.SCHOOL_IT, Role.LEADERSHIP, Role.EAA_ADMIN);
        policy.requireSiteAccess(user, siteId, site.getOrgId());
        return site;
    }

}
