package qa.sijill.sijill.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for every school-scoped table: id, creation time, {@code org_id} and {@code site_id}.
 *
 * <p>The {@code site_id} column is what row-level security and the server-side
 * {@code AccessPolicy} use to keep one school's data away from another.
 */
@Getter @Setter
@MappedSuperclass
public abstract class BaseEntity extends OrgScopedEntity {

    @Column(name = "site_id", nullable = false)
    private UUID siteId;
}
