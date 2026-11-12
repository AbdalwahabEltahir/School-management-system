package qa.hafez.hafez.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Root of the entity hierarchy: a UUID primary key and a creation timestamp.
 *
 * <p>Hierarchy: {@code IdentifiedEntity} → {@link OrgScopedEntity} (adds {@code org_id})
 * → {@link BaseEntity} (adds {@code site_id}). Pick the lowest class that fits:
 * <ul>
 *   <li>{@code IdentifiedEntity} – the organisation itself;</li>
 *   <li>{@code OrgScopedEntity} – sites, document types, rules (network-wide rules have no site);</li>
 *   <li>{@code BaseEntity} – everything that belongs to one school (the vast majority).</li>
 * </ul>
 */
@Getter @Setter
@MappedSuperclass
public abstract class IdentifiedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
