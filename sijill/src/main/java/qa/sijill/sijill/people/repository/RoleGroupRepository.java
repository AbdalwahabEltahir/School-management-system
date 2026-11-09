package qa.sijill.sijill.people.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.people.model.RoleGroup;

public interface RoleGroupRepository extends JpaRepository<RoleGroup, UUID> {

    List<RoleGroup> findByOrgId(UUID orgId);
}
