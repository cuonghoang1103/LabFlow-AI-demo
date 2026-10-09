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
        // Arrange - dựng Condition
        User user = activeStudent(2);
        when(userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Labflow123", "hashed")).thenReturn(true);
        when(accessTokenService.issue(eq(5L), anyString(), anyList())).thenReturn("jwt-token");
        when(accessTokenService.accessSeconds()).thenReturn(900);

        // Act - gọi đúng một hàm
        TokenResponse res = authService.login(new LoginRequest("student1@fpt.edu.vn", "Labflow123"));

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
        when(userRepository.findByEmailIgnoreCase("ghost@fpt.edu.vn")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@fpt.edu.vn", "Labflow123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        verify(accessTokenService, never()).issue(any(), any(), any());
    }

    @Test
    @DisplayName("UTCID03 [A] wrong password (1st time) -> AUTH_INVALID_CREDENTIALS, failedLogins becomes 1")
    void utcid03_wrongPassword_countsFailure() {
        User user = activeStudent(0);
        when(userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong123", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("student1@fpt.edu.vn", "wrong123")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid email or password");          // = dòng Log message
        assertThat(user.getFailedLogins()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("UTCID04 [B] 4 failures so far + wrong password (5th = limit) -> temporarily locked 15 minutes")
    void utcid04_fifthFailure_locksFifteenMinutes() {
        User user = activeStudent(4);
        when(userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong123", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("student1@fpt.edu.vn", "wrong123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_TEMP_LOCKED);
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plusSeconds(15 * 60));
        assertThat(user.getFailedLogins()).isZero();
    }
}
