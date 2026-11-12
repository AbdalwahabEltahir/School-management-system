package qa.hafez.hafez.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.people.model.PersonStatus;
import qa.hafez.hafez.people.model.RoleGroup;
import qa.hafez.hafez.people.service.PeopleService;
import qa.hafez.hafez.shared.model.DigestMode;
import qa.hafez.hafez.shared.model.Language;
import qa.hafez.hafez.shared.model.Role;

/** Request and response shapes of the people API. */
public final class PeopleDtos {

    private PeopleDtos() {}

    public record PersonView(UUID id, String email, String displayName, String role, String status, UUID siteId,
                             UUID subjectId, boolean generic, boolean overridden, Instant welcomeSentAt,
                             String studentCode) {
        public static PersonView from(Person p) {
            return new PersonView(p.getId(), p.getEmail(), p.getDisplayName(), p.getRole().name(), p.getStatus().name(),
                    p.getSiteId(), p.getSubjectId(), p.isGeneric(), p.isOverridden(), p.getWelcomeSentAt(),
                    p.getStudentCode());
        }
    }

    /** The signed-in person with their preferences ("My preferences", FR-11.2). */
    public record MeView(UUID id, String email, String displayName, String role, UUID siteId, UUID subjectId,
                         String language, String digestMode, boolean canDownloadWeeklyReport) {
        public static MeView from(Person p) {
            return new MeView(p.getId(), p.getEmail(), p.getDisplayName(), p.getRole().name(), p.getSiteId(),
                    p.getSubjectId(), p.getLanguage().name(), p.getDigestMode().name(),
                    p.getRole().canDownloadWeeklyReport());
        }
    }

    public record RoleGroupView(UUID id, String directoryGroup, String role) {
        public static RoleGroupView from(RoleGroup g) {
            return new RoleGroupView(g.getId(), g.getDirectoryGroup(), g.getRole().name());
        }
    }

    public record PreferencesRequest(Language language, DigestMode digestMode) {}

    /** School IT's override. Null fields are left as they are. */
    public record UpdatePersonRequest(Role role, PersonStatus status, UUID subjectId, Boolean generic, String name, String email) {
        public PeopleService.UpdateCommand toCommand() {
            return new PeopleService.UpdateCommand(role, status, subjectId, generic, name, email);
        }
    }

    public record AddPersonRequest(@NotBlank String email, @NotBlank String name, @NotNull Role role, UUID subjectId, UUID studentId) {}

    public record FollowUpRequest(String note) {}

    public record RoleGroupRequest(@NotBlank String directoryGroup, @NotNull Role role) {}

    public record SyncResult(int changed) {}
}
