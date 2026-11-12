package qa.hafez.hafez.academic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import qa.hafez.hafez.shared.model.BaseEntity;

/** A class group of one school, e.g. grade 7, section B (FR-017). */
@Getter @Setter
@Entity
@Table(name = "sections")
public class Section extends BaseEntity {

    @Column(name = "grade", nullable = false)
    private int grade;

    @Column(name = "name", nullable = false, length = 40)
    private String name;
}
