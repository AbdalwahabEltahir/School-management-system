package qa.sijill.sijill.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import qa.sijill.sijill.academic.model.Notice;

public interface NoticeRepository extends JpaRepository<Notice, UUID> {

    List<Notice> findBySiteIdOrderByStartsAtDesc(UUID siteId);
}
