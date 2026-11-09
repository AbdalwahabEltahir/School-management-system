package qa.sijill.sijill.shared.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.shared.model.JobRun;
import qa.sijill.sijill.shared.model.JobStatus;
import qa.sijill.sijill.shared.model.JobType;

public interface JobRunRepository extends JpaRepository<JobRun, UUID> {

    /** Recent runs of the given kinds (checks or backups), newest first. */
    List<JobRun> findTop20BySiteIdAndTypeInOrderByStartedAtDesc(UUID siteId, Collection<JobType> types);

    /** The last run of the given kinds with the given status, e.g. the last successful check. */
    Optional<JobRun> findFirstBySiteIdAndTypeInAndStatusOrderByStartedAtDesc(UUID siteId, Collection<JobType> types,
                                                                             JobStatus status);
}
