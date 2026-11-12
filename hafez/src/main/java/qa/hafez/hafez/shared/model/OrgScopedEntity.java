package qa.hafez.hafez.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * An entity that belongs to an organisation (the school network) but not necessarily to one school.
 * Section 7 of the proposal: "every table carries org_id", leaving room for a second organisation.
 */
@Getter @Setter
@MappedSuperclass
public abstract class OrgScopedEntity extends IdentifiedEntity {

    @Column(name = "org_id", nullable = false)
    private UUID orgId;
}
