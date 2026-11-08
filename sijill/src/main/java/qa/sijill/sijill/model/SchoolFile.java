package qa.sijill.sijill.model;



import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity
@Table(name = "files")
public class SchoolFile extends BaseEntity {
    @Column(name = "workspace_file_id", nullable = false, unique = true)
    private String workspaceFileId;   // the file's ID in the school workspace

    @Column(nullable = false)
    private String name;

    @Column(name = "owner_email")
    private String ownerEmail;

    @Enumerated(EnumType.STRING)
    private Sensitivity sensitivity;
}