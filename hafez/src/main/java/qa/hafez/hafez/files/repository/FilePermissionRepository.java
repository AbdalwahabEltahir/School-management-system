package qa.hafez.hafez.files.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.hafez.hafez.files.model.FilePermission;

public interface FilePermissionRepository extends JpaRepository<FilePermission, UUID> {

    List<FilePermission> findByFileId(UUID fileId);

    /** Removes all grants of a file. Transactional on its own: a derived delete fails without a transaction. */
    @org.springframework.transaction.annotation.Transactional
    void deleteByFileId(UUID fileId);
}
