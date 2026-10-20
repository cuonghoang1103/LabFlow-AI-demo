package vn.swt301.labflowdemo.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.auth.AuthDtos.LoginRequest;
import vn.swt301.labflowdemo.auth.AuthDtos.TokenResponse;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.notification.MailService;
import vn.swt301.labflowdemo.security.AccessTokenService;
import vn.swt301.labflowdemo.security.JwtProperties;
import vn.swt301.labflowdemo.settings.SettingsService;
import vn.swt301.labflowdemo.user.Role;
import vn.swt301.labflowdemo.user.RoleCode;
import vn.swt301.labflowdemo.user.RoleRepository;
import vn.swt301.labflowdemo.user.User;
import vn.swt301.labflowdemo.user.UserRepository;
import vn.swt301.labflowdemo.user.UserStatus;
import vn.swt301.labflowdemo.user.UserTokenRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SAMPLE unit test (the only one written in advance) for {@link AuthService#login(LoginRequest)}.
 * Copy this shape for your own functions: one @Test = one UTCID, name utcidNN_..., AAA pattern,
 * every dependency mocked, "now" fixed with Clock.fixed.
 * <p>
 * Các UTCID còn lại của ma trận login (sai lần 5 bị khoá, đang bị khoá tạm, tài khoản LOCKED,
 * email rỗng/null, email có khoảng trắng/hoa thường...) là việc của thành viên được giao hàm login.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    static final Instant NOW = Instant.parse("2026-10-12T02:00:00Z");   // 09:00 Monday, Vietnam time

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserTokenRepository tokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AccessTokenService accessTokenService;
    @Mock SettingsService settings;
    @Mock MailService mailService;
    @Mock AuditService auditService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        // Dựng service bằng tay để truyền Clock cố định và JwtProperties (record, không mock được)
        authService = new AuthService(userRepository, roleRepository, tokenRepository, passwordEncoder,
                accessTokenService, new JwtProperties("x".repeat(32), 15, 7), settings, mailService, auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(settings.getInt("auth.max-failed-logins")).thenReturn(5);
        lenient().when(settings.getInt("auth.lock-minutes")).thenReturn(15);
    }

    private static User activeStudent(int failedLogins) {
        Role role = new Role();
        role.setId(1);
        role.setCode(RoleCode.STUDENT);
        User u = new User();
        u.setId(5L);
        u.setEmail("student1@fpt.edu.vn");
        u.setPasswordHash("hashed");
        u.setFullName("Demo Student One");
        u.setStatus(UserStatus.ACTIVE);
        u.setFailedLogins(failedLogins);
        u.setRoles(Set.of(role));
        return u;
    }

    @Test
    @DisplayName("UTCID01 [N] active student + correct password -> Bearer token, role STUDENT, counter reset")
    void utcid01_activeStudent_returnsTokens() {
        // Condition: active student, correct password, and two previous failures.
        User user = activeStudent(2);
        when(userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Labflow123", "hashed")).thenReturn(true);
        when(accessTokenService.issue(eq(5L), anyString(), anyList())).thenReturn("jwt-token");
        when(accessTokenService.accessSeconds()).thenReturn(900);

        // Act - gọi đúng một hàm
        TokenResponse res = authService.login(new LoginRequest("student1@fpt.edu.vn", "Labflow123"));

        // Confirmation: Bearer token, STUDENT role, refresh token, and counter reset.
        // Assert - so với Confirmation (Return)
        assertThat(res.accessToken()).isEqualTo("jwt-token");
        assertThat(res.tokenType()).isEqualTo("Bearer");
        assertThat(res.roles()).containsExactly("STUDENT");
        assertThat(res.refreshToken()).isNotBlank();
        assertThat(user.getFailedLogins()).isZero();
    }

    @Test
    @DisplayName("UTCID02 [A] unknown email -> AUTH_INVALID_CREDENTIALS, no token")
    void utcid02_unknownEmail_throwsInvalidCredentials() {
        // Condition: an email not present in the repository.
        when(userRepository.findByEmailIgnoreCase("ghost@fpt.edu.vn")).thenReturn(Optional.empty());

        // Confirmation: invalid-credentials error and no access token.
        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@fpt.edu.vn", "Labflow123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        verify(accessTokenService, never()).issue(any(), any(), any());
    }

    @Test
    @DisplayName("UTCID03 [A] wrong password (1st time) -> AUTH_INVALID_CREDENTIALS, failedLogins becomes 1")
    void utcid03_wrongPassword_countsFailure() {
        // Condition: active student enters a wrong password for the first time.
        User user = activeStudent(0);
        when(userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong123", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("student1@fpt.edu.vn", "wrong123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        // Confirmation: invalid-credentials error, one failure counted, and no temporary lock yet.
        assertThat(user.getFailedLogins()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("UTCID04 [B] 4 failures so far + wrong password (5th = limit) -> temporarily locked 15 minutes")
    void utcid04_fifthFailure_locksFifteenMinutes() {
        // Condition: active account has four prior failures and now supplies a wrong password.
        User user = activeStudent(4);
        when(userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong123", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("student1@fpt.edu.vn", "wrong123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_TEMP_LOCKED);
        // Confirmation: temporary-lock error, lock expiry at exactly 15 minutes, and counter reset.
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plusSeconds(15 * 60));
        assertThat(user.getFailedLogins()).isZero();
    }

    @Test
    @DisplayName("UTCID05 [B] fourth failed login does not lock before the fifth attempt")
    void utcid05_fourthFailure_doesNotLockYet() {
        // Given: three failed attempts already recorded, with a configured limit of five.
        User user = activeStudent(3);
        when(userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong123", "hashed")).thenReturn(false);
        // When: the fourth wrong password is submitted.
        assertThatThrownBy(() -> authService.login(new LoginRequest("student1@fpt.edu.vn", "wrong123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        // Then: the counter rises to four without starting the temporary lock.
        assertThat(user.getFailedLogins()).isEqualTo(4);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("UTCID06 [A] active account still temporarily locked -> AUTH_ACCOUNT_TEMP_LOCKED")
    void utcid06_currentTemporaryLock_rejectsLogin() {
        // Condition: active account has a temporary lock that expires after the fixed current time.
        User user = activeStudent(0);
        user.setLockedUntil(NOW.plusSeconds(1));
        when(userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn")).thenReturn(Optional.of(user));

        // Confirmation: temporary-lock error and password validation is skipped.
        assertThatThrownBy(() -> authService.login(new LoginRequest("student1@fpt.edu.vn", "Labflow123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_TEMP_LOCKED);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("UTCID08 [A] administratively LOCKED account -> AUTH_ACCOUNT_LOCKED")
    void utcid08_lockedAccount_rejectsLogin() {
        // Condition: account status is LOCKED, regardless of a correct password.
        User user = activeStudent(0);
        user.setStatus(UserStatus.LOCKED);
        when(userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn")).thenReturn(Optional.of(user));

        // Confirmation: permanent-lock error and no password validation.
        assertThatThrownBy(() -> authService.login(new LoginRequest("student1@fpt.edu.vn", "Labflow123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("UTCID09 [A] PENDING account with correct password -> AUTH_EMAIL_NOT_VERIFIED")
    void utcid09_pendingAccount_rejectsLogin() {
        // Condition: email is verified neither, account is PENDING, and password is correct.
        User user = activeStudent(0);
        user.setStatus(UserStatus.PENDING);
        when(userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Labflow123", "hashed")).thenReturn(true);

        // Confirmation: email-not-verified error and no token issued.
        assertThatThrownBy(() -> authService.login(new LoginRequest("student1@fpt.edu.vn", "Labflow123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        verify(accessTokenService, never()).issue(any(), any(), any());
    }

    @Test
    @DisplayName("Extra: null email -> AUTH_INVALID_CREDENTIALS")
    void extra_nullEmail_rejectsLogin() {
        // Condition: login request has a null email.
        // Confirmation: invalid-credentials error before repository lookup.
        assertThatThrownBy(() -> authService.login(new LoginRequest(null, "Labflow123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    @DisplayName("Extra: empty email -> AUTH_INVALID_CREDENTIALS")
    void extra_emptyEmail_rejectsLogin() {
        // Condition: login request has an empty email.
        // Confirmation: invalid-credentials error before repository lookup.
        assertThatThrownBy(() -> authService.login(new LoginRequest("", "Labflow123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    @DisplayName("UTCID12 [A] whitespace-only email -> AUTH_INVALID_CREDENTIALS")
    void utcid12_whitespaceEmail_rejectsLogin() {
        // Condition: login request has only whitespace as email.
        // Confirmation: invalid-credentials error before repository lookup.
        assertThatThrownBy(() -> authService.login(new LoginRequest("   ", "Labflow123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    @DisplayName("UTCID11 [N] mixed-case email with surrounding spaces -> login succeeds")
    void utcid11_emailIsTrimmedAndCaseFolded() {
        // Condition: active account is submitted with surrounding spaces and mixed-case email.
        User user = activeStudent(0);
        when(userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Labflow123", "hashed")).thenReturn(true);
        when(accessTokenService.issue(eq(5L), anyString(), anyList())).thenReturn("jwt-token");
        when(accessTokenService.accessSeconds()).thenReturn(900);

        // Confirmation: normalized repository key is used and a Bearer response is returned.
        TokenResponse response = authService.login(new LoginRequest("  Student1@FPT.EDU.VN  ", "Labflow123"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(userRepository).findByEmailIgnoreCase("student1@fpt.edu.vn");
    }

    @Test
    @DisplayName("UTCID13 [A] null password -> AUTH_INVALID_CREDENTIALS")
    void utcid13_nullPassword_rejectsLogin() {
        // Condition: known-looking login request has a null password.
        // Confirmation: invalid-credentials error before repository lookup.
        assertThatThrownBy(() -> authService.login(new LoginRequest("student1@fpt.edu.vn", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    @DisplayName("UTCID07 [B] temporary lock expires exactly now -> password is evaluated")
    void utcid07_lockExpiryBoundary_allowsLogin() {
        // Condition: temporary lock expires exactly at the fixed current instant.
        User user = activeStudent(0);
        user.setLockedUntil(NOW);
        when(userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Labflow123", "hashed")).thenReturn(true);
        when(accessTokenService.issue(eq(5L), anyString(), anyList())).thenReturn("jwt-token");
        when(accessTokenService.accessSeconds()).thenReturn(900);

        // Confirmation: expiry boundary is allowed and login returns a token.
        TokenResponse response = authService.login(new LoginRequest("student1@fpt.edu.vn", "Labflow123"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("UTCID10 [A] pending account with wrong password returns invalid credentials")
    void utcid10_pendingWrongPassword_doesNotRevealAccountStatus() {
        // Condition: unverified account supplies the wrong password.
        User user = activeStudent(0);
        user.setStatus(UserStatus.PENDING);
        when(userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong123", "hashed")).thenReturn(false);
        // Act: attempt to log in before verification.
        assertThatThrownBy(() -> authService.login(new LoginRequest("student1@fpt.edu.vn", "wrong123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        // Confirmation: do not disclose PENDING status or issue a token.
        assertThat(user.getFailedLogins()).isEqualTo(1);
        verify(accessTokenService, never()).issue(any(), any(), any());
    }
}
