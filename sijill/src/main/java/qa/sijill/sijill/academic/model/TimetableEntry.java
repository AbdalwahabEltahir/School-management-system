package qa.sijill.sijill.academic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import qa.sijill.sijill.shared.model.BaseEntity;

/** One lesson in a section's weekly grid. Day 0 = Sunday to 4 = Thursday (FR-020). */
@Getter @Setter
@Entity
@Table(name = "timetable_entries")
public class TimetableEntry extends BaseEntity {

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "period", nullable = false)
    private int period;

    @Column(name = "room", nullable = true, length = 40)
    private String room;
}
