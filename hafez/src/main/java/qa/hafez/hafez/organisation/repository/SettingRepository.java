package qa.hafez.hafez.organisation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import qa.hafez.hafez.organisation.model.Setting;

public interface SettingRepository extends JpaRepository<Setting, UUID> {

    List<Setting> findBySiteId(UUID siteId);

    Optional<Setting> findBySiteIdAndSettingKey(UUID siteId, String settingKey);
}
