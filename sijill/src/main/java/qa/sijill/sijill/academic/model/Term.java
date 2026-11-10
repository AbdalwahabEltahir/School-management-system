package qa.sijill.sijill.academic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import qa.sijill.sijill.shared.model.BaseEntity;

/** A term of the school year (1 to 3) with its dates, created by the coordinator (FR-016). */
@Getter @Setter
@Entity
@Table(name = "terms")
public class Term extends BaseEntity {

    @Column(name = "number", nullable = false)
    private int number;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;
}
