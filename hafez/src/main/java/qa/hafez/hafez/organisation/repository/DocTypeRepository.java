package qa.hafez.hafez.organisation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.hafez.hafez.organisation.model.DocType;

public interface DocTypeRepository extends JpaRepository<DocType, UUID> {

    List<DocType> findByOrgIdOrderByName(UUID orgId);

    Optional<DocType> findByOrgIdAndNameIgnoreCase(UUID orgId, String name);
}
