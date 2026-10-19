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
}
