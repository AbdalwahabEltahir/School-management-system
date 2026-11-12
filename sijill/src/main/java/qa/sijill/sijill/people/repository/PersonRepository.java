package qa.sijill.sijill.people.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.shared.model.Role;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    /** Sign-in lookup: the school account's e-mail identifies the person. */
    Optional<Person> findByEmailIgnoreCase(String email);

    Optional<Person> findByStudentCode(String studentCode);

    List<Person> findBySiteIdOrderByDisplayName(UUID siteId);

    List<Person> findBySiteIdAndRole(UUID siteId, Role role);

    /** Used to find leadership recipients across the whole network. */
    List<Person> findByOrgIdAndRole(UUID orgId, Role role);

    List<Person> findBySiteIdAndRoleAndStatusNot(UUID siteId, Role role, PersonStatus status);
}
