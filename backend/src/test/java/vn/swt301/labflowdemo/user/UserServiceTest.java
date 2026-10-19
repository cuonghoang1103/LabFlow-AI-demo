package vn.swt301.labflowdemo.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.user.UserDtos.CreateUserRequest;
import vn.swt301.labflowdemo.user.UserDtos.ProfileRequest;
import vn.swt301.labflowdemo.user.UserDtos.UpdateUserRequest;
import vn.swt301.labflowdemo.user.UserDtos.UserView;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserTokenRepository tokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;
    private UserService service;
    private final Instant now = Instant.parse("2026-10-19T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, roleRepository, tokenRepository, passwordEncoder,
                auditService, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void utcid01_N_createUser_createsActiveUserAndAudits() {
        // Arrange: valid request, role and an unused email.
        Role role = role(RoleCode.STAFF);
        when(roleRepository.findByCodeIn(Set.of(RoleCode.STAFF))).thenReturn(List.of(role));
        when(userRepository.existsByEmailIgnoreCase("staff@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hash");
        doAnswer(inv -> { ((User) inv.getArgument(0)).setId(7L); return inv.getArgument(0); }).when(userRepository).save(any());
        // Condition: create an admin-managed account.
        UserView result = service.createUser(new CreateUserRequest(" Staff@Example.com ", " Staff ", "Password1", Set.of(RoleCode.STAFF)), 99L);
        // Assert/Confirmation: normalized active user is persisted and audited.
        assertAll(() -> assertEquals(7L, result.id()), () -> assertEquals("staff@example.com", result.email()),
                () -> assertEquals("ACTIVE", result.status()), () -> assertEquals(List.of("STAFF"), result.roles()));
        verify(userRepository).save(any(User.class));
        verify(auditService).record(eq(99L), eq("USER"), eq(7L), eq("CREATE"), isNull(), any(UserView.class));
    }

    @Test
    void utcid02_B_createUser_rejectsInvalidEmailWithErrorCode() {
        // Arrange: malformed email and otherwise valid input.
        // Condition: attempt creation.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createUser(new CreateUserRequest("bad", "Name", "Password1", Set.of(RoleCode.STAFF)), 1L));
        // Assert/Confirmation: AUTH_EMAIL_INVALID is returned before repository work.
        assertEquals(ErrorCode.AUTH_EMAIL_INVALID, ex.getCode());
        verifyNoInteractions(userRepository, roleRepository);
    }

    @Test
    void utcid03_B_createUser_rejectsTakenEmailWithErrorCode() {
        // Arrange: valid email already exists.
        when(roleRepository.findByCodeIn(Set.of(RoleCode.STAFF))).thenReturn(List.of(role(RoleCode.STAFF)));
        when(userRepository.existsByEmailIgnoreCase("a@example.com")).thenReturn(true);
        // Condition: attempt duplicate creation.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createUser(new CreateUserRequest("a@example.com", "Name", "Password1", Set.of(RoleCode.STAFF)), 1L));
        // Assert/Confirmation: AUTH_EMAIL_TAKEN is stable.
        assertEquals(ErrorCode.AUTH_EMAIL_TAKEN, ex.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void utcid04_N_updateUser_updatesNameAndRoles() {
        // Arrange: existing active user and replacement role.
        User user = user(4L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STAFF);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(roleRepository.findByCodeIn(Set.of(RoleCode.LECTURER))).thenReturn(List.of(role(RoleCode.LECTURER)));
        // Condition: update the account.
        UserView result = service.updateUser(4L, new UpdateUserRequest(" New ", Set.of(RoleCode.LECTURER)), 9L);
        // Assert/Confirmation: fields and audit are changed.
        assertEquals("New", result.fullName());
        assertEquals(List.of("LECTURER"), result.roles());
        verify(auditService).record(eq(9L), eq("USER"), eq(4L), eq("UPDATE"), any(UserView.class), any(UserView.class));
    }

    @Test
    void utcid05_B_updateUser_rejectsMissingUser() {
        // Arrange: repository cannot find target.
        when(userRepository.findById(8L)).thenReturn(Optional.empty());
        // Condition: update unknown id.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateUser(8L, new UpdateUserRequest("Name", Set.of(RoleCode.STAFF)), 1L));
        // Assert/Confirmation: USER_NOT_FOUND is returned.
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getCode());
    }

    @Test
    void utcid06_B_updateUser_rejectsSelfAdminRemoval() {
        // Arrange: actor is the only target admin and requests removal.
        User user = user(6L, "a@example.com", "Admin", UserStatus.ACTIVE, RoleCode.ADMIN);
        when(userRepository.findById(6L)).thenReturn(Optional.of(user));
        when(roleRepository.findByCodeIn(Set.of(RoleCode.STAFF))).thenReturn(List.of(role(RoleCode.STAFF)));
        // Condition: remove own ADMIN role.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateUser(6L, new UpdateUserRequest("Admin", Set.of(RoleCode.STAFF)), 6L));
        // Assert/Confirmation: self-protection error code is emitted.
        assertEquals(ErrorCode.USER_CANNOT_CHANGE_SELF, ex.getCode());
    }

    @Test
    void utcid07_N_changeStatus_locksAndInvalidatesRefreshTokens() {
        // Arrange: another active user.
        User user = user(10L, "u@example.com", "User", UserStatus.ACTIVE, RoleCode.STAFF);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        // Condition: lock the user.
        UserView result = service.changeStatus(10L, UserStatus.LOCKED, 20L);
        // Assert/Confirmation: status changes and refresh tokens are revoked.
        assertEquals("LOCKED", result.status());
        verify(tokenRepository).invalidateAll(10L, TokenPurpose.REFRESH, now);
        verify(auditService).record(20L, "USER", 10L, "STATUS", "ACTIVE", "LOCKED");
    }

    @Test
    void utcid08_B_changeStatus_rejectsPendingStatus() {
        // Arrange: status transition is not an allowed lock/unlock operation.
        // Condition: request PENDING.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.changeStatus(10L, UserStatus.PENDING, 20L));
        // Assert/Confirmation: VALIDATION_ERROR is returned before lookup.
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getCode());
        verifyNoInteractions(userRepository);
    }

    @Test
    void utcid09_B_changeStatus_rejectsUnchangedStatus() {
        // Arrange: target already has requested status.
        User user = user(10L, "u@example.com", "User", UserStatus.ACTIVE, RoleCode.STAFF);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        // Condition: request ACTIVE again.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.changeStatus(10L, UserStatus.ACTIVE, 20L));
        // Assert/Confirmation: unchanged status has its own error code.
        assertEquals(ErrorCode.USER_STATUS_UNCHANGED, ex.getCode());
    }

    @Test
    void utcid10_N_updateProfile_updatesOptionalFields() {
        // Arrange: existing user and valid phone/avatar.
        User user = user(11L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STUDENT);
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        // Condition: update profile.
        UserView result = service.updateProfile(11L, new ProfileRequest(" New ", "0912345678", "https://example.com/a.png"));
        // Assert/Confirmation: trimmed profile values are applied.
        assertEquals("New", result.fullName());
        assertEquals("0912345678", result.phone());
        assertEquals("https://example.com/a.png", result.avatarUrl());
    }

    @Test
    void utcid11_B_updateProfile_rejectsInvalidPhone() {
        // Arrange: existing user and invalid phone.
        when(userRepository.findById(11L)).thenReturn(Optional.of(user(11L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STUDENT)));
        // Condition: submit a non-Vietnamese phone.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateProfile(11L, new ProfileRequest("Name", "123", null)));
        // Assert/Confirmation: USER_PHONE_INVALID is returned.
        assertEquals(ErrorCode.USER_PHONE_INVALID, ex.getCode());
    }

    @Test
    void utcid12_B_updateProfile_rejectsInvalidAvatarUrl() {
        // Arrange: existing user and unsupported avatar scheme.
        when(userRepository.findById(11L)).thenReturn(Optional.of(user(11L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STUDENT)));
        // Condition: submit a non-http avatar URL.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateProfile(11L, new ProfileRequest("Name", "", "ftp://example.com/a")));
        // Assert/Confirmation: USER_AVATAR_URL_INVALID is returned.
        assertEquals(ErrorCode.USER_AVATAR_URL_INVALID, ex.getCode());
    }

    private static Role role(RoleCode code) { Role role = new Role(); role.setCode(code); return role; }
    private static User user(Long id, String email, String name, UserStatus status, RoleCode code) {
        User user = new User(); user.setId(id); user.setEmail(email); user.setFullName(name); user.setStatus(status); user.setCreatedAt(Instant.EPOCH); user.setRoles(new HashSet<>(Set.of(role(code)))); return user;
    }
}
