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

    @Test
    void utcid16_B_createUser_rejectsWeakPassword() {
        // Condition: valid identity but password has no digit.
        // Act: submit the admin creation request.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createUser(
                new CreateUserRequest("staff@external.org", "Staff", "onlyletters", Set.of(RoleCode.STAFF)), 9L));
        // Confirmation: password policy fails before role lookup or persistence.
        assertEquals(ErrorCode.AUTH_PASSWORD_WEAK, ex.getCode());
        verifyNoInteractions(roleRepository, userRepository, auditService);
    }

    @Test
    void utcid17_B_createUser_requiresRoles() {
        // Condition: valid identity but no roles are supplied.
        // Act: request account creation.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createUser(
                new CreateUserRequest("staff@external.org", "Staff", "Password1", Set.of()), 9L));
        // Confirmation: empty roles fail without saving or auditing.
        assertEquals(ErrorCode.USER_ROLES_REQUIRED, ex.getCode());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void utcid18_B_createUser_rejectsBlankName() {
        // Condition: full name contains only spaces.
        // Act: submit an otherwise valid request.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createUser(
                new CreateUserRequest("staff@external.org", "  ", "Password1", Set.of(RoleCode.STAFF)), 9L));
        // Confirmation: invalid name is rejected before any repository call.
        assertEquals(ErrorCode.AUTH_FULL_NAME_INVALID, ex.getCode());
        verifyNoInteractions(roleRepository, userRepository);
    }

    @Test
    void utcid19_B_updateUser_rejectsLastActiveAdminRemoval() {
        // Condition: another active admin is the last active admin.
        User admin = user(6L, "a@example.com", "Admin", UserStatus.ACTIVE, RoleCode.ADMIN);
        when(userRepository.findById(6L)).thenReturn(Optional.of(admin));
        when(roleRepository.findByCodeIn(Set.of(RoleCode.STAFF))).thenReturn(List.of(role(RoleCode.STAFF)));
        when(userRepository.countActiveAdmins()).thenReturn(1L);
        // Act: remove ADMIN as a different actor.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateUser(
                6L, new UpdateUserRequest("Changed", Set.of(RoleCode.STAFF)), 9L));
        // Confirmation: the admin retains their name and role and no audit is recorded.
        assertEquals(ErrorCode.USER_LAST_ADMIN, ex.getCode());
        assertEquals("Admin", admin.getFullName());
        assertTrue(admin.hasRole(RoleCode.ADMIN));
        verifyNoInteractions(auditService);
    }

    @Test
    void utcid20_N_updateUser_allowsRemovalWithTwoActiveAdmins() {
        // Condition: two active admins exist and the actor is not the target.
        User admin = user(6L, "a@example.com", "Admin", UserStatus.ACTIVE, RoleCode.ADMIN);
        when(userRepository.findById(6L)).thenReturn(Optional.of(admin));
        when(roleRepository.findByCodeIn(Set.of(RoleCode.STAFF))).thenReturn(List.of(role(RoleCode.STAFF)));
        when(userRepository.countActiveAdmins()).thenReturn(2L);
        // Act: remove the target's ADMIN role.
        UserView result = service.updateUser(6L, new UpdateUserRequest("Staff", Set.of(RoleCode.STAFF)), 9L);
        // Confirmation: replacement role is applied and change is audited.
        assertEquals(List.of("STAFF"), result.roles());
        verify(auditService).record(eq(9L), eq("USER"), eq(6L), eq("UPDATE"), any(UserView.class), any(UserView.class));
    }

    @Test
    void utcid21_B_updateUser_requiresRoles() {
        // Condition: an existing user receives an empty role set.
        User user = user(4L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STAFF);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        // Act: update their account.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateUser(
                4L, new UpdateUserRequest("New", Set.of()), 9L));
        // Confirmation: no fields change or audit is written.
        assertEquals(ErrorCode.USER_ROLES_REQUIRED, ex.getCode());
        assertEquals("Old", user.getFullName());
        verifyNoInteractions(auditService);
    }

    @Test
    void utcid22_B_changeStatus_rejectsSelfLock() {
        // Condition: the target and the actor are the same active admin.
        when(userRepository.findById(6L)).thenReturn(Optional.of(user(6L, "a@example.com", "Admin", UserStatus.ACTIVE, RoleCode.ADMIN)));
        // Act: lock the actor's own account.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.changeStatus(6L, UserStatus.LOCKED, 6L));
        // Confirmation: self-protection runs before token revocation.
        assertEquals(ErrorCode.USER_CANNOT_CHANGE_SELF, ex.getCode());
        verifyNoInteractions(tokenRepository, auditService);
    }

    @Test
    void utcid23_B_changeStatus_rejectsLockingLastActiveAdmin() {
        // Condition: target is the only active admin.
        User admin = user(6L, "a@example.com", "Admin", UserStatus.ACTIVE, RoleCode.ADMIN);
        when(userRepository.findById(6L)).thenReturn(Optional.of(admin));
        when(userRepository.countActiveAdmins()).thenReturn(1L);
        // Act: another actor tries to lock the admin.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.changeStatus(6L, UserStatus.LOCKED, 9L));
        // Confirmation: admin stays active and no sessions are revoked.
        assertEquals(ErrorCode.USER_LAST_ADMIN, ex.getCode());
        assertEquals(UserStatus.ACTIVE, admin.getStatus());
        verifyNoInteractions(tokenRepository, auditService);
    }

    @Test
    void utcid24_N_changeStatus_unlocksAndClearsFailedLogins() {
        // Condition: locked user still has a temporary lock and failed attempts.
        User user = user(10L, "u@example.com", "User", UserStatus.LOCKED, RoleCode.STAFF);
        user.setFailedLogins(5);
        user.setLockedUntil(now.plusSeconds(900));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        // Act: unlock the account.
        UserView result = service.changeStatus(10L, UserStatus.ACTIVE, 20L);
        // Confirmation: counters clear, status changes, and no refresh tokens are invalidated.
        assertEquals("ACTIVE", result.status());
        assertEquals(0, user.getFailedLogins());
        assertNull(user.getLockedUntil());
        verifyNoInteractions(tokenRepository);
        verify(auditService).record(20L, "USER", 10L, "STATUS", "LOCKED", "ACTIVE");
    }

    @Test
    void utcid25_B_updateProfile_rejectsMissingUser() {
        // Condition: profile user is absent.
        when(userRepository.findById(11L)).thenReturn(Optional.empty());
        // Act: attempt a valid profile update.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateProfile(
                11L, new ProfileRequest("Name", null, null)));
        // Confirmation: missing user is reported without other repository work.
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getCode());
    }

    @Test
    void utcid26_N_updateProfile_blankOptionalFieldsClearExistingValues() {
        // Condition: user has phone and avatar already set.
        User user = user(11L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STUDENT);
        user.setPhone("0912345678");
        user.setAvatarUrl("https://example.com/old.png");
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        // Act: submit blank optional fields.
        UserView result = service.updateProfile(11L, new ProfileRequest("New", "  ", "  "));
        // Confirmation: both optional values are cleared, and name changes.
        assertEquals("New", result.fullName());
        assertNull(result.phone());
        assertNull(result.avatarUrl());
    }

    @Test
    void utcid27_B_updateProfile_rejectsElevenDigitPhone() {
        // Condition: profile exists but phone has eleven digits.
        User user = user(11L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STUDENT);
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        // Act: submit a number longer than the ten-digit rule.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateProfile(
                11L, new ProfileRequest("New", "09123456789", null)));
        // Confirmation: invalid phone does not change the name.
        assertEquals(ErrorCode.USER_PHONE_INVALID, ex.getCode());
        assertEquals("Old", user.getFullName());
    }

    @Test
    void utcid28_B_updateProfile_rejectsAvatarOver500Characters() {
        // Condition: the http URL exceeds the 500-character limit.
        User user = user(11L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STUDENT);
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        // Act: submit the oversized avatar URL.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateProfile(
                11L, new ProfileRequest("New", null, "https://example.com/" + "a".repeat(481))));
        // Confirmation: URL validation fails before mutating the profile.
        assertEquals(ErrorCode.USER_AVATAR_URL_INVALID, ex.getCode());
        assertEquals("Old", user.getFullName());
    }

    @Test
    void utcidTv3_34_B_createUser_rejectsEmailOver100Characters() {
        // Condition: syntactically valid email exceeds the maximum length.
        // Act: create an account with the oversized email.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createUser(
                new CreateUserRequest("a".repeat(89) + "@example.com", "Name", "Password1", Set.of(RoleCode.STAFF)), 9L));
        // Confirmation: email validation stops before any repository access.
        assertEquals(ErrorCode.AUTH_EMAIL_INVALID, ex.getCode());
        verifyNoInteractions(userRepository, roleRepository);
    }

    @Test
    void utcidTv3_35_B_createUser_rejectsNameOver100Characters() {
        // Condition: valid email but full name is 101 characters.
        // Act: attempt account creation.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createUser(
                new CreateUserRequest("u@example.com", "N".repeat(101), "Password1", Set.of(RoleCode.STAFF)), 9L));
        // Confirmation: name error occurs without loading roles.
        assertEquals(ErrorCode.AUTH_FULL_NAME_INVALID, ex.getCode());
        verifyNoInteractions(roleRepository, userRepository);
    }

    @Test
    void utcidTv3_36_B_createUser_rejectsPasswordOver64Characters() {
        // Condition: password contains a letter and digit but has 65 characters.
        // Act: attempt account creation.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createUser(
                new CreateUserRequest("u@example.com", "Name", "A1" + "a".repeat(63), Set.of(RoleCode.STAFF)), 9L));
        // Confirmation: password length is enforced before role lookup.
        assertEquals(ErrorCode.AUTH_PASSWORD_WEAK, ex.getCode());
        verifyNoInteractions(roleRepository, userRepository);
    }

    @Test
    void utcidTv3_37_B_createUser_rejectsNullRoles() {
        // Condition: account request has no role set at all.
        // Act: attempt account creation.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createUser(
                new CreateUserRequest("u@example.com", "Name", "Password1", null), 9L));
        // Confirmation: a null set has the same explicit error as an empty set.
        assertEquals(ErrorCode.USER_ROLES_REQUIRED, ex.getCode());
        verifyNoInteractions(roleRepository, userRepository);
    }

    @Test
    void utcidTv3_38_B_updateUser_rejectsNameOver100Characters() {
        // Condition: target exists, but replacement name exceeds 100 characters.
        User user = user(4L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STAFF);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        // Act: update the account.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateUser(
                4L, new UpdateUserRequest("N".repeat(101), Set.of(RoleCode.STAFF)), 9L));
        // Confirmation: the account remains unchanged and no audit is written.
        assertEquals(ErrorCode.AUTH_FULL_NAME_INVALID, ex.getCode());
        assertEquals("Old", user.getFullName());
        verifyNoInteractions(roleRepository, auditService);
    }

    @Test
    void utcidTv3_39_B_updateUser_rejectsNullRoles() {
        // Condition: target exists and role set is null.
        User user = user(4L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STAFF);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        // Act: update the account.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateUser(
                4L, new UpdateUserRequest("New", null), 9L));
        // Confirmation: missing roles do not mutate the target.
        assertEquals(ErrorCode.USER_ROLES_REQUIRED, ex.getCode());
        assertEquals("Old", user.getFullName());
        verifyNoInteractions(auditService);
    }

    @Test
    void utcidTv3_40_B_changeStatus_rejectsMissingUser() {
        // Condition: target id does not exist.
        when(userRepository.findById(10L)).thenReturn(Optional.empty());
        // Act: attempt to lock that account.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.changeStatus(10L, UserStatus.LOCKED, 20L));
        // Confirmation: no tokens or audit events are touched.
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getCode());
        verifyNoInteractions(tokenRepository, auditService);
    }

    @Test
    void utcidTv3_41_N_changeStatus_allowsLockingAdminWhenTwoAreActive() {
        // Condition: a different actor locks one of two active admins.
        User admin = user(6L, "a@example.com", "Admin", UserStatus.ACTIVE, RoleCode.ADMIN);
        when(userRepository.findById(6L)).thenReturn(Optional.of(admin));
        when(userRepository.countActiveAdmins()).thenReturn(2L);
        // Act: lock the target admin.
        UserView result = service.changeStatus(6L, UserStatus.LOCKED, 9L);
        // Confirmation: the boundary of two permits lock and revokes refresh tokens.
        assertEquals("LOCKED", result.status());
        verify(tokenRepository).invalidateAll(6L, TokenPurpose.REFRESH, now);
        verify(auditService).record(9L, "USER", 6L, "STATUS", "ACTIVE", "LOCKED");
    }

    @Test
    void utcidTv3_42_B_updateProfile_rejectsNineDigitPhone() {
        // Condition: phone starts with zero but is one digit short.
        when(userRepository.findById(11L)).thenReturn(Optional.of(user(11L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STUDENT)));
        // Act: update the profile with nine digits.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateProfile(
                11L, new ProfileRequest("New", "091234567", null)));
        // Confirmation: the ten-digit rule rejects the short number.
        assertEquals(ErrorCode.USER_PHONE_INVALID, ex.getCode());
    }

    @Test
    void utcidTv3_43_B_updateProfile_rejectsLetterInTenCharacterPhone() {
        // Condition: phone has ten characters, including a letter.
        when(userRepository.findById(11L)).thenReturn(Optional.of(user(11L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STUDENT)));
        // Act: update the profile with an alphabetic character.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateProfile(
                11L, new ProfileRequest("New", "091234567a", null)));
        // Confirmation: phone must consist solely of digits.
        assertEquals(ErrorCode.USER_PHONE_INVALID, ex.getCode());
    }

    @Test
    void utcidTv3_44_N_updateProfile_acceptsAvatarAt500Characters() {
        // Condition: an HTTP avatar URL is exactly 500 characters long.
        when(userRepository.findById(11L)).thenReturn(Optional.of(user(11L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STUDENT)));
        String avatar = "https://example.com/" + "a".repeat(480);
        // Act: update the profile at the upper URL boundary.
        UserView result = service.updateProfile(11L, new ProfileRequest("New", null, avatar));
        // Confirmation: 500 characters are allowed and retained.
        assertEquals(500, result.avatarUrl().length());
        assertEquals(avatar, result.avatarUrl());
    }

    @Test
    void utcidTv3_45_B_updateProfile_rejectsBlankName() {
        // Condition: profile owner exists, but the new name contains only spaces.
        User user = user(11L, "u@example.com", "Old", UserStatus.ACTIVE, RoleCode.STUDENT);
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        // Act: attempt to clear the mandatory name.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateProfile(
                11L, new ProfileRequest("  ", null, null)));
        // Confirmation: name validation leaves the profile intact.
        assertEquals(ErrorCode.AUTH_FULL_NAME_INVALID, ex.getCode());
        assertEquals("Old", user.getFullName());
    }

    private static Role role(RoleCode code) { Role role = new Role(); role.setCode(code); return role; }
    private static User user(Long id, String email, String name, UserStatus status, RoleCode code) {
        User user = new User(); user.setId(id); user.setEmail(email); user.setFullName(name); user.setStatus(status); user.setCreatedAt(Instant.EPOCH); user.setRoles(new HashSet<>(Set.of(role(code)))); return user;
    }
}
