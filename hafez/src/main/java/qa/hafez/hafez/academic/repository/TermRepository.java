package qa.hafez.hafez.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import qa.hafez.hafez.academic.model.Term;

public interface TermRepository extends JpaRepository<Term, UUID> {

    List<Term> findBySiteIdOrderByNumberAsc(UUID siteId);
    Optional<Term> findBySiteIdAndNumber(UUID siteId, int number);
}
