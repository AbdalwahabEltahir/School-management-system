package qa.hafez.hafez.organisation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.hafez.hafez.shared.model.OrgScopedEntity;

/**
 * A school or the head office (FR-8.3). The label "School"/"Campus" is a display setting, not code.
 *
 * <p>Each site has its own workspace {@link #domain} and its own read-only connection.
 * Its own {@code id} is the {@code site_id} used on all school-scoped tables.
 */
@Getter @Setter
@Entity
@Table(name = "sites")
public class Site extends OrgScopedEntity {

    @Column(nullable = false, length = 200)
    private String name;

    /** Workspace domain, e.g. {@code school-a.example.edu}. Only these domains may sign in. */
    @Column(nullable = false, unique = true, length = 255)
    private String domain;

    /** The head office cannot be removed and is not scored. */
    @Column(name = "head_office", nullable = false)
    private boolean headOffice;

    /** Counted in the health score and the network overview. */
    @Column(nullable = false)
    private boolean scored = true;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Whether the workspace connection is switched on. "Disconnect" turns it off: checks stop and the UI
     * shows a red banner on every page (FR-8.2); "Reconnect" turns it back on.
     */
    @Column(name = "checks_enabled", nullable = false)
    private boolean checksEnabled = true;

    /** The principal of this school (a {@code Person}); drives the "principal sees all notifications" routing. */
    @Column(name = "principal_id")
    private UUID principalId;

    /** The school-level coordinator who receives issues that have no subject coordinator. */
    @Column(name = "coordinator_id")
    private UUID coordinatorId;

    /** Null until the first check has run: the UI shows "Waiting for first check". */
    @Column(name = "first_check_at")
    private Instant firstCheckAt;
}
