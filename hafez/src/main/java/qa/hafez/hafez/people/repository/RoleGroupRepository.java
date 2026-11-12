package qa.hafez.hafez.people.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.hafez.hafez.people.model.RoleGroup;

public interface RoleGroupRepository extends JpaRepository<RoleGroup, UUID> {

    List<RoleGroup> findByOrgId(UUID orgId);
}
