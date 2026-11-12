package qa.hafez.hafez.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Public sign-in and registration for students and parents. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record StudentInvitation(@NotBlank String code, @NotBlank String password) {}

    public record ParentInvitation(@NotBlank String code, @NotBlank String name, @NotBlank String email, @NotBlank String password) {}

    public record RedeemRequest(@NotBlank String code) {}

    public record School(UUID id, String name) {}
}
