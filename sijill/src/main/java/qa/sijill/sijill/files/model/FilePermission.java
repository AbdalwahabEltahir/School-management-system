package qa.sijill.sijill.files.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.BaseEntity;
import qa.sijill.sijill.workspace.AccessLevel;
import qa.sijill.sijill.workspace.ShareTargetType;

/**
 * One sharing grant on a file, as last read from the workspace (section 7 "permissions").
 * The set of grants for a file is replaced wholesale on every re-read.
 */
@Getter @Setter
@Entity
@Table(name = "file_permissions")
public class FilePermission extends BaseEntity {

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    /** E-mail or group of the grantee; null for a public link. */
    @Column(name = "grantee", length = 255)
    private String grantee;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private ShareTargetType targetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 20)
    private AccessLevel accessLevel = AccessLevel.VIEWER;

    /** True when the grantee is outside the network (rule R3). */
    @Column(nullable = false)
    private boolean external;
}
