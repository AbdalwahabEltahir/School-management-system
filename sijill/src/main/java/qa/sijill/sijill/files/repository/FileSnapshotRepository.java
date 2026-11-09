package qa.sijill.sijill.files.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.files.model.FileSnapshot;

public interface FileSnapshotRepository extends JpaRepository<FileSnapshot, UUID> {

    Optional<FileSnapshot> findFirstByFileIdOrderByCreatedAtDesc(UUID fileId);

    /** Full history of a file, newest first (the file-details history, FR-11.3). */
    List<FileSnapshot> findByFileIdOrderByCreatedAtDesc(UUID fileId);
}
