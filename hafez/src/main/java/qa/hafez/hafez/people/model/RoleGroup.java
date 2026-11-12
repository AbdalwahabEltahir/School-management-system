package qa.hafez.hafez.people.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import qa.hafez.hafez.shared.model.OrgScopedEntity;
import qa.hafez.hafez.shared.model.Role;

/**
 * Maps a directory group in the school workspace to a Hafez {@link Role} (section 4: "each role comes
 * from a group in the school's own workspace directory"). Used by the directory sync.
 */
@Getter @Setter
@Entity
@Table(name = "role_groups")
public class RoleGroup extends OrgScopedEntity {

    /** The group's name/email in the workspace directory. */
    @Column(name = "directory_group", nullable = false, length = 255)
    private String directoryGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;
}
