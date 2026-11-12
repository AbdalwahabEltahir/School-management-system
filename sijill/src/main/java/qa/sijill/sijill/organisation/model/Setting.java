package qa.sijill.sijill.organisation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.BaseEntity;

/**
 * A per-school configuration value (FR-8.5): message defaults, quiet hours, log retention,
 * classifier confidence threshold, … Configuration over code (design principle 7).
 *
 * <p>Well-known keys are declared in {@code SettingKeys}.
 */
@Getter @Setter
@Entity
@Table(name = "settings")
public class Setting extends BaseEntity {

    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 1000)
    private String settingValue;
}
