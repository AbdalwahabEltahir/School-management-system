package qa.hafez.hafez.audit.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.hafez.hafez.audit.model.ExportRecord;

public interface ExportRecordRepository extends JpaRepository<ExportRecord, UUID> {

    List<ExportRecord> findBySiteIdOrderByCreatedAtDesc(UUID siteId);
}
