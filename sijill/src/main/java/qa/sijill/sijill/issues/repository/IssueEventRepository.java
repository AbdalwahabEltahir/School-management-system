package qa.sijill.sijill.issues.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.issues.model.IssueEvent;

public interface IssueEventRepository extends JpaRepository<IssueEvent, UUID> {

    /** An issue's history, oldest first. */
    List<IssueEvent> findByIssueIdOrderByCreatedAtAsc(UUID issueId);
}
