package qa.sijill.sijill.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.model.PersonStatus;
import qa.sijill.sijill.people.model.RoleGroup;
import qa.sijill.sijill.people.service.PeopleService;
import qa.sijill.sijill.shared.model.DigestMode;
import qa.sijill.sijill.shared.model.Language;
import qa.sijill.sijill.shared.model.Role;

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
                         String language, String digestMode, boolean canDownloadWeeklyReport,
                         String phone, boolean hasPhoto, List<String> muted, List<String> alertKinds) {
        public static MeView from(Person p) {
            return new MeView(p.getId(), p.getEmail(), p.getDisplayName(), p.getRole().name(), p.getSiteId(),
                    p.getSubjectId(), p.getLanguage().name(), p.getDigestMode().name(),
                    p.getRole().canDownloadWeeklyReport(), p.getPhone(), p.getPhoto() != null,
                    p.mutedSet().stream().map(Enum::name).sorted().toList(), PeopleService.alertKindsFor(p.getRole()));
        }
    }

    public record RoleGroupView(UUID id, String directoryGroup, String role) {
        public static RoleGroupView from(RoleGroup g) {
            return new RoleGroupView(g.getId(), g.getDirectoryGroup(), g.getRole().name());
        }
    }

    /** Language, summary mode and (when sent) the alert kinds the person switched off. Null leaves a field as it is. */
    public record PreferencesRequest(Language language, DigestMode digestMode, List<String> muted) {}

    /** Personal details a person edits on their own profile. */
    public record ProfileRequest(String displayName, String phone) {}

    /** A profile photo as base64 text. */
    public record PhotoRequest(String data) {}

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
