package vn.swt301.labflowdemo.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class UserDtos {

    private UserDtos() {
    }

    /** What the API shows about a user - never the password hash. */
    public record UserView(Long id, String email, String fullName, String phone, String avatarUrl,
                           String status, List<String> roles, Instant createdAt) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getAvatarUrl(),
                    u.getStatus().name(), u.roleCodes(), u.getCreatedAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Size(max = 64) String password,
            @NotEmpty Set<RoleCode> roles) {
    }

    public record UpdateUserRequest(@NotBlank @Size(max = 100) String fullName, @NotEmpty Set<RoleCode> roles) {
    }

    public record ChangeStatusRequest(@NotNull UserStatus status) {
    }

    public record ProfileRequest(
            @NotBlank @Size(max = 100) String fullName,
            @Size(max = 15) String phone,
            @Size(max = 500) String avatarUrl) {
    }
}
