package vn.swt301.labflowdemo.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.auth.AuthDtos.*;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.notification.MailService;
import vn.swt301.labflowdemo.security.AccessTokenService;
import vn.swt301.labflowdemo.security.JwtProperties;
import vn.swt301.labflowdemo.settings.SettingsService;
import vn.swt301.labflowdemo.user.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServicePolicyTest {
    static final Instant NOW = Instant.parse("2026-10-12T02:00:00Z");
    @Mock UserRepository users; @Mock RoleRepository roles; @Mock UserTokenRepository tokens;
    @Mock PasswordEncoder encoder; @Mock AccessTokenService access; @Mock SettingsService settings;
    @Mock MailService mail; @Mock AuditService audit;
    AuthService service;

    @BeforeEach void setUp() {
        service = new AuthService(users, roles, tokens, encoder, access,
                new JwtProperties("x".repeat(32), 15, 7), settings, mail, audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
    private User user(UserStatus status) { User u = new User(); u.setId(7L); u.setEmail("student@fpt.edu.vn");
        u.setFullName("Student"); u.setStatus(status); u.setPasswordHash("old-hash"); u.setRoles(Set.of()); return u; }
    private UserToken token(TokenPurpose p, String raw, Instant expires, Instant used) { UserToken t = new UserToken();
        t.setUserId(7L); t.setPurpose(p); t.setTokenHash(SecureTokens.hash(raw)); t.setExpiresAt(expires); t.setUsedAt(used); return t; }
    private void goodRegisterSettings() { when(settings.getList("auth.allowed-email-domains")).thenReturn(java.util.List.of("fpt.edu.vn"));
        lenient().when(settings.getInt("auth.verify-token-hours")).thenReturn(24); }

    @Test void utcid01_register_valid_createsPendingAndEmails() {
        // Arrange - Condition: allowed domain, new email and valid password
        goodRegisterSettings(); when(users.existsByEmailIgnoreCase("student@fpt.edu.vn")).thenReturn(false);
        Role role = new Role(); role.setCode(RoleCode.STUDENT); when(roles.findByCode(RoleCode.STUDENT)).thenReturn(Optional.of(role));
        when(encoder.encode("Password1")).thenReturn("hash");
        // Act - invoke the unit under test
        RegisterResponse result = service.register(new RegisterRequest(" Student@FPT.EDU.VN ", "Password1", " Student "));
        // Assert - Confirmation: pending account, normalized fields, verification mail
        assertThat(result.email()).isEqualTo("student@fpt.edu.vn"); assertThat(result.status()).isEqualTo("PENDING");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class); verify(users).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.PENDING); verify(mail).send(eq("student@fpt.edu.vn"), anyString(), contains("verify-email"));
    }

    @Test void utcid02_register_duplicateEmail_throwsTaken() {
        // Arrange - Condition: email already exists
        goodRegisterSettings(); when(users.existsByEmailIgnoreCase("student@fpt.edu.vn")).thenReturn(true);
        // Act + Assert - Confirmation: exact business error
        assertThatThrownBy(() -> service.register(new RegisterRequest("student@fpt.edu.vn", "Password1", "Student")))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo(ErrorCode.AUTH_EMAIL_TAKEN);
    }

    @Test void utcid03_register_disallowedDomain_throwsDomainError() {
        // Arrange - Condition: domain absent from settings
        when(settings.getList("auth.allowed-email-domains")).thenReturn(java.util.List.of("fpt.edu.vn"));
        // Act + Assert - Confirmation: exact business error
        assertThatThrownBy(() -> service.register(new RegisterRequest("student@gmail.com", "Password1", "Student")))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo(ErrorCode.AUTH_EMAIL_DOMAIN_NOT_ALLOWED);
    }

    @Test void utcid04_verify_validToken_activatesUser() {
        // Arrange - Condition: unused, unexpired verification token
        User u = user(UserStatus.PENDING); when(tokens.findByTokenHashAndPurpose(SecureTokens.hash("verify"), TokenPurpose.VERIFY)).thenReturn(Optional.of(token(TokenPurpose.VERIFY, "verify", NOW.plusSeconds(60), null)));
        when(users.findById(7L)).thenReturn(Optional.of(u));
        // Act
        MessageResponse result = service.verifyEmail("verify");
        // Assert - Confirmation: account active and confirmation message
        assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE); assertThat(result.message()).contains("verified");
    }

    @Test void utcid05_verify_expiredToken_throwsExpired() {
        // Arrange - Condition: token expires at current instant
        when(tokens.findByTokenHashAndPurpose(anyString(), eq(TokenPurpose.VERIFY))).thenReturn(Optional.of(token(TokenPurpose.VERIFY, "verify", NOW, null)));
        // Act + Assert - Confirmation
        assertThatThrownBy(() -> service.verifyEmail("verify")).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    @Test void utcid06_refresh_validRotatesTokenAndReturnsPair() {
        // Arrange - Condition: active user, valid unused refresh token
        User u = user(UserStatus.ACTIVE); when(tokens.findByTokenHashAndPurpose(anyString(), eq(TokenPurpose.REFRESH))).thenReturn(Optional.of(token(TokenPurpose.REFRESH, "refresh", NOW.plusSeconds(60), null))); when(users.findById(7L)).thenReturn(Optional.of(u));
        when(access.issue(eq(7L), anyString(), anyList())).thenReturn("access"); when(access.accessSeconds()).thenReturn(900);
        // Act
        TokenResponse result = service.refresh("refresh");
        // Assert - Confirmation: access token returned and old token consumed
        assertThat(result.accessToken()).isEqualTo("access"); verify(tokens, atLeastOnce()).save(any(UserToken.class));
    }

    @Test void utcid07_refresh_reusedToken_revokesAndThrowsUsed() {
        // Arrange - Condition: refresh token already consumed
        when(tokens.findByTokenHashAndPurpose(anyString(), eq(TokenPurpose.REFRESH))).thenReturn(Optional.of(token(TokenPurpose.REFRESH, "refresh", NOW.plusSeconds(60), NOW.minusSeconds(1))));
        // Act + Assert - Confirmation
        assertThatThrownBy(() -> service.refresh("refresh")).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo(ErrorCode.AUTH_TOKEN_USED);
        verify(tokens).invalidateAll(7L, TokenPurpose.REFRESH, NOW);
    }

    @Test void utcid08_logout_unusedToken_marksUsed() {
        // Arrange - Condition: known unused refresh token
        UserToken t = token(TokenPurpose.REFRESH, "logout", NOW.plusSeconds(60), null); when(tokens.findByTokenHashAndPurpose(anyString(), eq(TokenPurpose.REFRESH))).thenReturn(Optional.of(t));
        // Act
        MessageResponse result = service.logout("logout");
        // Assert - Confirmation
        assertThat(result.message()).isEqualTo("Logged out"); assertThat(t.getUsedAt()).isEqualTo(NOW);
    }

    @Test void utcid09_changePassword_wrongCurrent_throwsWrongPassword() {
        // Arrange - Condition: current password does not match
        when(users.findById(7L)).thenReturn(Optional.of(user(UserStatus.ACTIVE))); when(encoder.matches("bad", "old-hash")).thenReturn(false);
        // Act + Assert - Confirmation
        assertThatThrownBy(() -> service.changePassword(7L, new ChangePasswordRequest("bad", "Newpass1"))).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo(ErrorCode.AUTH_WRONG_PASSWORD);
    }

    @Test void utcid10_changePassword_valid_updatesAndRevokesRefreshTokens() {
        // Arrange - Condition: matching current and different strong new password
        User u = user(UserStatus.ACTIVE); when(users.findById(7L)).thenReturn(Optional.of(u)); when(encoder.matches("Oldpass1", "old-hash")).thenReturn(true); when(encoder.encode("Newpass1")).thenReturn("new-hash");
        // Act
        MessageResponse result = service.changePassword(7L, new ChangePasswordRequest("Oldpass1", "Newpass1"));
        // Assert - Confirmation
        assertThat(result.message()).isEqualTo("Password changed"); assertThat(u.getPasswordHash()).isEqualTo("new-hash"); verify(tokens).invalidateAll(eq(7L), eq(TokenPurpose.REFRESH), eq(NOW));
    }

    @Test void utcid11_forgotPassword_unknownEmail_returnsGenericMessageWithoutMail() {
        // Arrange - Condition: no matching account
        when(users.findByEmailIgnoreCase("ghost@fpt.edu.vn")).thenReturn(Optional.empty());
        // Act
        MessageResponse result = service.forgotPassword("ghost@fpt.edu.vn");
        // Assert - Confirmation: no account disclosure and no mail
        assertThat(result.message()).isEqualTo(AuthService.GENERIC_RESET_MESSAGE); verifyNoInteractions(mail);
    }

    @Test void utcid12_resetPassword_validToken_updatesPassword() {
        // Arrange - Condition: valid reset token and strong password
        User u = user(UserStatus.ACTIVE); when(tokens.findByTokenHashAndPurpose(anyString(), eq(TokenPurpose.RESET))).thenReturn(Optional.of(token(TokenPurpose.RESET, "reset", NOW.plusSeconds(60), null))); when(users.findById(7L)).thenReturn(Optional.of(u)); when(encoder.encode("Newpass1")).thenReturn("new-hash");
        // Act
        MessageResponse result = service.resetPassword(new ResetPasswordRequest("reset", "Newpass1"));
        // Assert - Confirmation
        assertThat(result.message()).contains("reset"); assertThat(u.getPasswordHash()).isEqualTo("new-hash"); verify(tokens).invalidateAll(eq(7L), eq(TokenPurpose.REFRESH), eq(NOW));
    }

    @Test void utcid13_resetPassword_weakPassword_doesNotConsumeToken() {
        // Arrange - Condition: weak password is rejected before token lookup
        // Act + Assert - Confirmation
        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("reset", "weak"))).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo(ErrorCode.AUTH_PASSWORD_WEAK);
        verifyNoInteractions(tokens);
    }

    @Test void register_weakPassword_doesNotCreateAccount() {
        // Given: email, domain and name are valid, but the password has fewer than eight characters.
        goodRegisterSettings();
        // When: registering fails at the password policy (before the duplicate-email lookup).
        assertThatThrownBy(() -> service.register(new RegisterRequest("student@fpt.edu.vn", "short1", "Student")))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_PASSWORD_WEAK);
        // Then: an invalid registration neither persists a user nor sends a verification email.
        verify(users, never()).save(any());
        verifyNoInteractions(mail);
    }

    @Test void register_invalidName_doesNotCreateAccount() {
        // Given: a permitted email and valid password, but a whitespace-only full name.
        goodRegisterSettings();
        // When/then: the name validation stops registration before any persistence or mail.
        assertThatThrownBy(() -> service.register(new RegisterRequest("student@fpt.edu.vn", "Password1", "  ")))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_FULL_NAME_INVALID);
        verify(users, never()).save(any());
        verifyNoInteractions(mail);
    }

    @Test void register_invalidEmail_doesNotConsultDomainSetting() {
        // Given: malformed email; domain policy must not be consulted for invalid syntax.
        assertThatThrownBy(() -> service.register(new RegisterRequest("not-an-email", "Password1", "Student")))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_EMAIL_INVALID);
        verifyNoInteractions(settings, users, mail);
    }

    @Test void verify_usedToken_doesNotActivateAccount() {
        // Given: a verification link was consumed one second ago.
        when(tokens.findByTokenHashAndPurpose(SecureTokens.hash("verify"), TokenPurpose.VERIFY))
                .thenReturn(Optional.of(token(TokenPurpose.VERIFY, "verify", NOW.plusSeconds(60), NOW.minusSeconds(1))));
        // When/then: replaying it is rejected before loading or changing the user.
        assertThatThrownBy(() -> service.verifyEmail("verify"))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_USED);
        verifyNoInteractions(users);
    }

    @Test void verify_activeAccount_keepsActiveButConsumesToken() {
        // Given: an active account and a fresh, valid verification token.
        User u = user(UserStatus.ACTIVE);
        UserToken t = token(TokenPurpose.VERIFY, "verify", NOW.plusSeconds(60), null);
        when(tokens.findByTokenHashAndPurpose(SecureTokens.hash("verify"), TokenPurpose.VERIFY)).thenReturn(Optional.of(t));
        when(users.findById(7L)).thenReturn(Optional.of(u));
        // When: the link is followed again for an already active user.
        service.verifyEmail("verify");
        // Then: verification is idempotent for status, but this new token is consumed.
        assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(t.getUsedAt()).isEqualTo(NOW);
    }

    @Test void changePassword_sameAsCurrent_doesNotRevokeSessions() {
        // Given: the current password matches; the requested new password is identical.
        when(users.findById(7L)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(encoder.matches("Oldpass1", "old-hash")).thenReturn(true);
        // When/then: a reused password is rejected without encoding or revoking tokens.
        assertThatThrownBy(() -> service.changePassword(7L, new ChangePasswordRequest("Oldpass1", "Oldpass1")))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_PASSWORD_SAME_AS_OLD);
        verify(encoder, never()).encode(anyString());
        verifyNoInteractions(tokens);
    }

    @Test void changePassword_missingUser_doesNotCheckPassword() {
        // Given: there is no account for the requested ID.
        when(users.findById(999L)).thenReturn(Optional.empty());
        // When/then: report USER_NOT_FOUND without trying a password hash.
        assertThatThrownBy(() -> service.changePassword(999L, new ChangePasswordRequest("Oldpass1", "Newpass1")))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(encoder, tokens);
    }

    @Test void logout_usedToken_doesNotOverwriteUsedAt() {
        // Given: a previously revoked refresh token with its original revocation timestamp.
        Instant used = NOW.minusSeconds(60);
        UserToken t = token(TokenPurpose.REFRESH, "logout", NOW.plusSeconds(60), used);
        when(tokens.findByTokenHashAndPurpose(SecureTokens.hash("logout"), TokenPurpose.REFRESH))
                .thenReturn(Optional.of(t));
        // When: logging out again with the same token.
        assertThat(service.logout("logout").message()).isEqualTo("Logged out");
        // Then: the previous usedAt remains intact; no extra token is created.
        assertThat(t.getUsedAt()).isEqualTo(used);
        verify(tokens, never()).save(any());
    }

    @Test void logout_blankToken_succeedsWithoutLookup() {
        // Given: blank input; logout is deliberately idempotent even without a known token.
        assertThat(service.logout("  ").message()).isEqualTo("Logged out");
        // Then: no database access is needed.
        verifyNoInteractions(tokens);
    }

    @Test void verify_nullToken_rejectsWithoutLookup() {
        // Condition: no verification token is supplied.
        // Act: attempt to verify the account.
        assertThatThrownBy(() -> service.verifyEmail(null))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
        // Confirmation: no token or account was changed.
        verifyNoInteractions(tokens, users);
    }

    @Test void verify_unknownToken_rejectsWithoutChangingUser() {
        // Condition: a nonblank token has no matching VERIFY record.
        when(tokens.findByTokenHashAndPurpose(SecureTokens.hash("unknown"), TokenPurpose.VERIFY))
                .thenReturn(Optional.empty());
        // Act and confirmation: invalid token, no account mutation.
        assertThatThrownBy(() -> service.verifyEmail("unknown"))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
        verifyNoInteractions(users);
    }

    @Test void changePassword_weakNewPassword_keepsOldHashAndSessions() {
        // Condition: correct current password, but the new password lacks the required length.
        User u = user(UserStatus.ACTIVE);
        when(users.findById(7L)).thenReturn(Optional.of(u));
        when(encoder.matches("Oldpass1", "old-hash")).thenReturn(true);
        // Act: reject the weak new password.
        assertThatThrownBy(() -> service.changePassword(7L, new ChangePasswordRequest("Oldpass1", "short1")))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_PASSWORD_WEAK);
        // Confirmation: do not change the hash or revoke sessions.
        assertThat(u.getPasswordHash()).isEqualTo("old-hash");
        verifyNoInteractions(tokens);
    }

    @Test void logout_unknownToken_isIdempotent() {
        // Condition: a syntactically valid refresh token is not stored.
        when(tokens.findByTokenHashAndPurpose(SecureTokens.hash("unknown"), TokenPurpose.REFRESH))
                .thenReturn(Optional.empty());
        // Act and confirmation: logging out still succeeds without creating a token.
        assertThat(service.logout("unknown").message()).isEqualTo("Logged out");
        verify(tokens, never()).save(any());
    }

    @Test void refresh_expiringNow_rejectsBeforeLoadingUser() {
        // Condition: expiration equals the fixed current instant (exclusive upper bound).
        UserToken t = token(TokenPurpose.REFRESH, "refresh", NOW, null);
        when(tokens.findByTokenHashAndPurpose(SecureTokens.hash("refresh"), TokenPurpose.REFRESH))
                .thenReturn(Optional.of(t));
        // Act: request another token pair.
        assertThatThrownBy(() -> service.refresh("refresh"))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
        // Confirmation: the expired token was not consumed and no user was loaded.
        assertThat(t.getUsedAt()).isNull();
        verifyNoInteractions(users);
    }

    @Test void refresh_pendingUser_rejectsWithoutIssuingNewPair() {
        // Condition: valid refresh token belongs to an account not yet ACTIVE.
        UserToken t = token(TokenPurpose.REFRESH, "refresh", NOW.plusSeconds(60), null);
        when(tokens.findByTokenHashAndPurpose(SecureTokens.hash("refresh"), TokenPurpose.REFRESH))
                .thenReturn(Optional.of(t));
        when(users.findById(7L)).thenReturn(Optional.of(user(UserStatus.PENDING)));
        // Act and confirmation: access is refused; no new JWT or refresh token is issued.
        assertThatThrownBy(() -> service.refresh("refresh"))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED);
        verifyNoInteractions(access);
        verify(tokens, never()).save(any());
        // The method consumes the old token before checking account status (transaction rolls back on error).
        assertThat(t.getUsedAt()).isEqualTo(NOW);
    }

    @Test void forgotPassword_activeUser_invalidatesOlderLinksAndSendsNewOne() {
        // Condition: the normalized email belongs to an ACTIVE account.
        when(users.findByEmailIgnoreCase("student@fpt.edu.vn"))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(settings.getInt("auth.reset-token-minutes")).thenReturn(30);
        // Act: request a new reset link.
        MessageResponse result = service.forgotPassword(" Student@FPT.EDU.VN ");
        // Confirmation: same public response as unknown email, but the old links are revoked and mail sent.
        assertThat(result.message()).isEqualTo(AuthService.GENERIC_RESET_MESSAGE);
        verify(tokens).invalidateAll(7L, TokenPurpose.RESET, NOW);
        verify(tokens).save(any(UserToken.class));
        verify(mail).send(eq("student@fpt.edu.vn"), anyString(), contains("reset-password?token="));
    }

    @Test void forgotPassword_pendingUser_doesNotSendResetEmail() {
        // Condition: email exists, but the account has not been verified.
        when(users.findByEmailIgnoreCase("student@fpt.edu.vn"))
                .thenReturn(Optional.of(user(UserStatus.PENDING)));
        // Act and confirmation: response does not reveal the account status and no reset link is created.
        assertThat(service.forgotPassword("student@fpt.edu.vn").message())
                .isEqualTo(AuthService.GENERIC_RESET_MESSAGE);
        verifyNoInteractions(tokens, mail);
    }

    @Test void forgotPassword_lockedUser_returnsGenericMessageWithoutMail() {
        // Condition: the matching account is LOCKED rather than ACTIVE.
        when(users.findByEmailIgnoreCase("student@fpt.edu.vn"))
                .thenReturn(Optional.of(user(UserStatus.LOCKED)));
        // Act: request a reset link.
        assertThat(service.forgotPassword("student@fpt.edu.vn").message())
                .isEqualTo(AuthService.GENERIC_RESET_MESSAGE);
        // Confirmation: no link is issued for a locked account.
        verifyNoInteractions(tokens, mail);
    }

    @Test void forgotPassword_nullEmail_doesNotLookUpUser() {
        // Condition: caller supplies no email.
        // Act: request a reset link.
        assertThat(service.forgotPassword(null).message())
                .isEqualTo(AuthService.GENERIC_RESET_MESSAGE);
        // Confirmation: neither user data nor mail is accessed.
        verifyNoInteractions(users, tokens, mail);
    }

    @Test void refresh_nullToken_rejectsWithoutLookup() {
        // Condition: no refresh token was supplied.
        // Act: request new tokens.
        assertThatThrownBy(() -> service.refresh(null))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
        // Confirmation: no user or token data is accessed.
        verifyNoInteractions(tokens, users);
    }

    @Test void resetPassword_expiredToken_doesNotUpdateUser() {
        // Condition: a strong password is supplied with a token expiring exactly now.
        when(tokens.findByTokenHashAndPurpose(SecureTokens.hash("reset"), TokenPurpose.RESET))
                .thenReturn(Optional.of(token(TokenPurpose.RESET, "reset", NOW, null)));
        // Act: try to reset the password.
        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("reset", "Newpass1")))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
        // Confirmation: no password is changed.
        verifyNoInteractions(users, encoder);
    }
}
