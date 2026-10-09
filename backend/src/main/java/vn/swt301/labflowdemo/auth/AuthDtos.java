package vn.swt301.labflowdemo.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request / response shapes of the auth API.
 * Bean Validation (@NotBlank, @Size, @Email) checks the SHAPE at the controller (400 VALIDATION_ERROR);
 * the service still checks the BUSINESS rules (domain, duplicate, password policy...) because a service
 * can be called from places other than the controller - và unit test gọi thẳng service.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(max = 64) String password,
            @NotBlank @Size(max = 100) String fullName) {
    }

    public record RegisterResponse(Long userId, String email, String status, String message) {
    }

    public record LoginRequest(@NotBlank @Size(max = 100) String email, @NotBlank @Size(max = 64) String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, int expiresIn, String refreshToken,
                                Long userId, String fullName, List<String> roles) {
    }

    public record TokenRequest(@NotBlank @Size(max = 100) String token) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(max = 64) String newPassword) {
    }

    public record ForgotPasswordRequest(@NotBlank @Size(max = 100) String email) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 100) String token, @NotBlank @Size(max = 64) String newPassword) {
    }

    public record MessageResponse(String message) {
    }
}
