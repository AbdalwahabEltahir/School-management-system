package qa.sijill.sijill.rules.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import qa.sijill.sijill.shared.model.OrgScopedEntity;

/**
 * A rule's stable identity and state (FR-8.1). The <i>content</i> of the rule (check, scope, severity,
 * reminder days, fix steps) lives in versioned {@link RuleVersion} rows, so each issue can point to the
 * exact wording and settings that were in force when it was raised (FR-5.3).
 *
 * <p>A rule with {@code siteId == null} is <b>network-wide</b> and changes only with two-person
 * approval (section 4). A rule with a site is a school-level rule owned by that school's IT admin.
 */
@Getter @Setter
@Entity
@Table(name = "rules")
public class Rule extends OrgScopedEntity {

    /** Human code such as R1…R10 for the starter rules; free text for rules added later. */
    @Column(nullable = false, length = 20)
    private String code;

    /** Null = network-wide rule; otherwise the school this rule belongs to. */
    @Column(name = "site_id")
    private UUID siteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleStatus status = RuleStatus.DRAFT;

    public boolean isNetworkWide() {
        return siteId == null;
    }
}
