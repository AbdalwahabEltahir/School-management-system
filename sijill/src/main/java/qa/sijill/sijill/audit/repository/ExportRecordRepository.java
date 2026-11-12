package qa.sijill.sijill.audit.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.audit.model.ExportRecord;

public interface ExportRecordRepository extends JpaRepository<ExportRecord, UUID> {

    List<ExportRecord> findBySiteIdOrderByCreatedAtDesc(UUID siteId);
}
