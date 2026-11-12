package qa.sijill.sijill.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import qa.sijill.sijill.academic.model.TimetableEntry;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, UUID> {

    List<TimetableEntry> findBySectionIdOrderByDayOfWeekAscPeriodAsc(UUID sectionId);
    List<TimetableEntry> findByTeacherIdOrderByDayOfWeekAscPeriodAsc(UUID teacherId);
    Optional<TimetableEntry> findBySectionIdAndDayOfWeekAndPeriod(UUID sectionId, int dayOfWeek, int period);
    Optional<TimetableEntry> findByTeacherIdAndDayOfWeekAndPeriod(UUID teacherId, int dayOfWeek, int period);
    Optional<TimetableEntry> findBySiteIdAndRoomIgnoreCaseAndDayOfWeekAndPeriod(UUID siteId, String room, int dayOfWeek, int period);
}
