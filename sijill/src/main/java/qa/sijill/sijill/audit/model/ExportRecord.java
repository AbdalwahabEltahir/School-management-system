package qa.sijill.sijill.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.BaseEntity;

/**
 * Who exported what, masked or not, and why (section 7 "exports", FR-10.2). Exports are name-free by
 * default; School IT may unmask one export, which needs a logged reason.
 */
@Getter @Setter
@Entity
@Table(name = "exports")
public class ExportRecord extends BaseEntity {

    @Column(name = "exported_by", nullable = false)
    private UUID exportedBy;

    /** What was exported, e.g. "issues" or "activity-log". */
    @Column(name = "export_kind", nullable = false, length = 60)
    private String exportKind;

    /** True when names were replaced by initials/IDs (the default). */
    @Column(nullable = false)
    private boolean masked = true;

    /** Mandatory when {@link #masked} is false. */
    @Column(name = "unmask_reason", length = 500)
    private String unmaskReason;

    @Column(name = "row_count", nullable = false)
    private int rowCount;
}
