package qa.sijill.sijill.organisation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

import qa.sijill.sijill.organisation.model.DocType;
import qa.sijill.sijill.organisation.model.Setting;
import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.model.Subject;
import qa.sijill.sijill.organisation.service.SiteService;
import qa.sijill.sijill.shared.model.Sensitivity;

/** Request and response shapes of the sites, subjects, document types and settings API. */
public final class OrganisationDtos {

    private OrganisationDtos() {}

    public record SiteView(UUID id, String name, String domain, boolean headOffice, boolean scored, boolean active,
                           boolean checksEnabled, UUID principalId, UUID coordinatorId, Instant firstCheckAt) {
        public static SiteView from(Site s) {
            return new SiteView(s.getId(), s.getName(), s.getDomain(), s.isHeadOffice(), s.isScored(), s.isActive(),
                    s.isChecksEnabled(), s.getPrincipalId(), s.getCoordinatorId(), s.getFirstCheckAt());
        }
    }

    public record SubjectView(UUID id, String name, UUID coordinatorId) {
        public static SubjectView from(Subject s) {
            return new SubjectView(s.getId(), s.getName(), s.getCoordinatorId());
        }
    }

    public record DocTypeView(UUID id, String name, String defaultSensitivity, boolean trusted) {
        public static DocTypeView from(DocType d) {
            return new DocTypeView(d.getId(), d.getName(), d.getDefaultSensitivity().name(), d.isTrusted());
        }
    }

    public record SettingView(String key, String value) {
        public static SettingView from(Setting s) {
            return new SettingView(s.getSettingKey(), s.getSettingValue());
        }
    }

    public record SiteRequest(String name, String domain, Boolean headOffice, Boolean scored, UUID principalId,
                              UUID coordinatorId) {
        public SiteService.SiteCommand toCommand() {
            return new SiteService.SiteCommand(name, domain, headOffice, scored, principalId, coordinatorId);
        }
    }

    public record SubjectRequest(@NotBlank String name, UUID coordinatorId) {}

    public record DocTypeRequest(@NotBlank String name, @NotNull Sensitivity sensitivity) {}

    public record SettingRequest(@NotBlank String key, @NotNull String value) {}
}
