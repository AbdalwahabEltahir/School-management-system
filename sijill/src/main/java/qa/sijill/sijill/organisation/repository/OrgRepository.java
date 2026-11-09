package qa.sijill.sijill.organisation.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import qa.sijill.sijill.organisation.model.Org;

public interface OrgRepository extends JpaRepository<Org, UUID> {

    /**
     * Loads the organisation with a pessimistic write lock. {@code ActivityLogService} uses this to
     * serialise appends to the hash chain: two concurrent writers cannot both take the same sequence number.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Org o where o.id = :id")
    Optional<Org> lockById(@Param("id") UUID id);
}
