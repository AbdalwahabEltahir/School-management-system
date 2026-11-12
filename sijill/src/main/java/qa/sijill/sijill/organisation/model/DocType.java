package qa.sijill.sijill.organisation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.OrgScopedEntity;
import qa.sijill.sijill.shared.model.Sensitivity;

/**
 * A document type (FR-8.4), e.g. "Mark book" or "Exam", with a default sensitivity that School IT can
 * change in one click. A new type is not trusted by the checker until it has labelled examples.
 */
@Getter @Setter
@Entity
@Table(name = "doc_types")
public class DocType extends OrgScopedEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_sensitivity", nullable = false, length = 20)
    private Sensitivity defaultSensitivity = Sensitivity.INTERNAL;

    /** False for a newly added type until it has labelled examples (the checker must not trust it yet). */
    @Column(nullable = false)
    private boolean trusted = true;
}
