package qa.hafez.hafez.academic.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import qa.hafez.hafez.academic.model.AttendanceRecord;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    List<AttendanceRecord> findBySectionIdAndAttendanceDate(UUID sectionId, LocalDate date);
    List<AttendanceRecord> findByStudentIdOrderByAttendanceDateDesc(UUID studentId);
    List<AttendanceRecord> findBySectionIdAndAttendanceDateBetween(UUID sectionId, LocalDate from, LocalDate to);
}
