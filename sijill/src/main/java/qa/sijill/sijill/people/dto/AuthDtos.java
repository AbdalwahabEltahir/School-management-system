package qa.sijill.sijill.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Public sign-in and registration for students and parents. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record StudentRegistration(@NotNull UUID siteId, @NotBlank String name, @NotBlank String email, @NotBlank String password) {}

    public record ParentRegistration(@NotBlank String name, @NotBlank String email, @NotBlank String password, @NotBlank String studentCode) {}

    public record AddChildRequest(@NotBlank String studentCode) {}

    public record School(UUID id, String name) {}
}
