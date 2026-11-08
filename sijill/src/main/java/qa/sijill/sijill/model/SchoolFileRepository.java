package qa.sijill.sijill.model;

import java.util.UUID;
import qa.sijill.sijill.model.SchoolFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolFileRepository extends JpaRepository<SchoolFile, UUID> {}