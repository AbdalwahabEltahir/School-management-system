package qa.sijill.sijill.files.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import qa.sijill.sijill.files.model.SchoolFile;
import qa.sijill.sijill.workspace.DriveType;

public interface SchoolFileRepository extends JpaRepository<SchoolFile, UUID> {

    Optional<SchoolFile> findByWorkspaceFileId(String workspaceFileId);

    /** Live (not trashed) files of a school: the population a full check reads. */
    List<SchoolFile> findBySiteIdAndTrashedAtIsNull(UUID siteId);

    long countBySiteIdAndTrashedAtIsNull(UUID siteId);

    /** "School files in personal drives" tile on the leadership overview (FR-11.6). */
    long countBySiteIdAndDriveTypeAndTrashedAtIsNull(UUID siteId, DriveType driveType);

    /** A teacher's own files ("My files"). */
    List<SchoolFile> findByOwnerEmailIgnoreCaseAndTrashedAtIsNull(String ownerEmail);

    /** Files of a school's subject (the coordinator's "My subject"). */
    List<SchoolFile> findBySiteIdAndSubjectIdAndTrashedAtIsNull(UUID siteId, UUID subjectId);

    /** All of a person's files, including trashed ones; used when their status changes. */
    List<SchoolFile> findByOwnerEmailIgnoreCase(String ownerEmail);

    /** Bulk-deletion detection: files trashed since a point in time. */
    long countBySiteIdAndTrashedAtAfter(UUID siteId, Instant since);

    /**
     * True when another live file in the same school has the same checksum and was created earlier or at
     * the same time. The earliest copy is treated as the official one; later copies are flagged (rule R7).
     */
    @Query("""
            select count(f) > 0 from SchoolFile f
            where f.siteId = :siteId and f.checksum = :checksum and f.trashedAt is null
              and f.id <> :id and f.createdAt <= :createdAt
            """)
    boolean existsEarlierCopy(@Param("siteId") UUID siteId, @Param("checksum") String checksum,
                              @Param("id") UUID id, @Param("createdAt") Instant createdAt);
}
