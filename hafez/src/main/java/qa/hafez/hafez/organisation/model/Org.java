package qa.hafez.hafez.organisation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import qa.hafez.hafez.shared.model.IdentifiedEntity;

/**
 * The organisation that owns everything: today the school network (five schools and a head office),
 * but every table carries {@code org_id} so a second organisation can be added later (section 7).
 *
 * <p>The activity-log service locks this row while it appends to the hash chain, which serialises
 * writers per organisation (see {@code ActivityLogService}).
 */
@Getter @Setter
@Entity
@Table(name = "orgs")
public class Org extends IdentifiedEntity {

    @Column(nullable = false, length = 200)
    private String name;
}
