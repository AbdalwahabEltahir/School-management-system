package qa.sijill.sijill.files.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.files.model.FileProfile;

public interface FileProfileRepository extends JpaRepository<FileProfile, UUID> {

    Optional<FileProfile> findByFileId(UUID fileId);

    /** Files waiting for a person to classify them (FR-2.3). */
    List<FileProfile> findBySiteIdAndNeedsReviewTrue(UUID siteId);

    List<FileProfile> findBySiteIdAndSubjectIdAndNeedsReviewTrue(UUID siteId, UUID subjectId);

    /** Files to review whose subject is not known yet: every coordinator of the school can pick these up. */
    List<FileProfile> findBySiteIdAndSubjectIdIsNullAndNeedsReviewTrue(UUID siteId);
}
