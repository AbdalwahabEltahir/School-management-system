package qa.sijill.sijill.organisation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.BaseEntity;

/**
 * A subject taught at a school (Mathematics, Arabic, …). There is one subject coordinator per subject
 * per school who manages the subject drive and reviews that subject's files (section 4).
 */
@Getter @Setter
@Entity
@Table(name = "subjects")
public class Subject extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    /** The {@code Person} who coordinates this subject; may be null until assigned. */
    @Column(name = "coordinator_id")
    private UUID coordinatorId;
}
