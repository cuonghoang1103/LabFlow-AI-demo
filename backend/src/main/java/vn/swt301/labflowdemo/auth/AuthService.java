package vn.swt301.labflowdemo.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.auth.AuthDtos.ChangePasswordRequest;
import vn.swt301.labflowdemo.auth.AuthDtos.LoginRequest;
import vn.swt301.labflowdemo.auth.AuthDtos.MessageResponse;
import vn.swt301.labflowdemo.auth.AuthDtos.RegisterRequest;
import vn.swt301.labflowdemo.auth.AuthDtos.RegisterResponse;
import vn.swt301.labflowdemo.auth.AuthDtos.ResetPasswordRequest;
import vn.swt301.labflowdemo.auth.AuthDtos.TokenResponse;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.common.Texts;
import vn.swt301.labflowdemo.notification.MailService;
import vn.swt301.labflowdemo.security.AccessTokenService;
import vn.swt301.labflowdemo.security.JwtProperties;
import vn.swt301.labflowdemo.settings.SettingsService;
import vn.swt301.labflowdemo.user.RoleCode;
import vn.swt301.labflowdemo.user.RoleRepository;
import vn.swt301.labflowdemo.user.TokenPurpose;
import vn.swt301.labflowdemo.user.User;
import vn.swt301.labflowdemo.user.UserRepository;
import vn.swt301.labflowdemo.user.UserStatus;
import vn.swt301.labflowdemo.user.UserToken;
import vn.swt301.labflowdemo.user.UserTokenRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Register, verify email, login, refresh, logout, change / forgot / reset password.
 * Screens S01 (C3), S03 (C1), S04 (C3), S05 (C1). Business rules BR-01, BR-02, BR-03.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Simple, readable email check: something@domain.tld, no spaces. */
    static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    static final String GENERIC_RESET_MESSAGE = "If the email exists, a reset link has been sent";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final JwtProperties jwtProperties;
    private final SettingsService settings;
    private final MailService mailService;
    private final AuditService auditService;
    private final Clock clock;

    @Value("${app.public-url}")
    private String publicUrl = "http://localhost:8080";

    /**
     * S01 Register: creates a PENDING student account and emails a verification link (BR-01).
     *
     * @return the new user id and status PENDING
     * @throws BusinessException AUTH_EMAIL_INVALID, AUTH_EMAIL_DOMAIN_NOT_ALLOWED, AUTH_FULL_NAME_INVALID,
     *                           AUTH_PASSWORD_WEAK, AUTH_EMAIL_TAKEN
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (email == null || email.length() > 100 || !EMAIL.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_INVALID);
        }
        // BR-01: chỉ email trường (danh sách tên miền cấu hình ở Settings)
        String domain = email.substring(email.indexOf('@') + 1);
        if (!settings.getList("auth.allowed-email-domains").contains(domain)) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_DOMAIN_NOT_ALLOWED);
        }
        if (!Texts.lengthBetween(request.fullName(), 1, 100)) {
            throw new BusinessException(ErrorCode.AUTH_FULL_NAME_INVALID);
        }
        PasswordPolicy.check(request.password());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_TAKEN);
        }
        Instant now = Instant.now(clock);
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(Texts.clean(request.fullName()));
        user.setStatus(UserStatus.PENDING);
        user.setRoles(Set.of(roleRepository.findByCode(RoleCode.STUDENT).orElseThrow()));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        String token = issueOneTimeToken(user.getId(), TokenPurpose.VERIFY,
                Duration.ofHours(settings.getInt("auth.verify-token-hours")));
        mailService.send(email, "Verify your LabFlow account",
                "Open this link to activate your account: " + publicUrl + "/verify-email?token=" + token);
        auditService.record(user.getId(), "USER", user.getId(), "REGISTER", null, Map.of("email", email));
        log.info("User registered: id={}, email={}", user.getId(), email);
        return new RegisterResponse(user.getId(), email, UserStatus.PENDING.name(),
                "Check your email to verify the account");
    }

    /**
     * S01 Verify email: a valid, unused, unexpired VERIFY token turns the account ACTIVE.
     *
     * @throws BusinessException AUTH_TOKEN_INVALID, AUTH_TOKEN_USED, AUTH_TOKEN_EXPIRED
     */
    @Transactional
    public MessageResponse verifyEmail(String rawToken) {
        UserToken token = consumeToken(rawToken, TokenPurpose.VERIFY);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
        if (user.getStatus() == UserStatus.PENDING) {
            user.setStatus(UserStatus.ACTIVE);
            user.setUpdatedAt(Instant.now(clock));
        }
        auditService.record(user.getId(), "USER", user.getId(), "VERIFY_EMAIL", null, null);
        log.info("Email verified: userId={}", user.getId());
        return new MessageResponse("Email verified, you can log in now");
    }

    /**
     * S03 Login. BR-02: after N wrong passwords in a row (setting auth.max-failed-logins, default 5)
     * the account is locked for auth.lock-minutes (default 15).
     * <p>
     * noRollbackFor: bộ đếm sai mật khẩu phải được LƯU dù hàm ném lỗi - nếu rollback thì không bao giờ khoá được.
     *
     * @throws BusinessException AUTH_INVALID_CREDENTIALS, AUTH_ACCOUNT_LOCKED, AUTH_ACCOUNT_TEMP_LOCKED,
     *                           AUTH_EMAIL_NOT_VERIFIED
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        if (email == null || request.password() == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        // Không nói "email không tồn tại" - cùng một lỗi với sai mật khẩu để không lộ ai có tài khoản
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        Instant now = Instant.now(clock);
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }
        if (user.getLockedUntil() != null && now.isBefore(user.getLockedUntil())) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_TEMP_LOCKED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int failed = user.getFailedLogins() + 1;
            if (failed >= settings.getInt("auth.max-failed-logins")) {
                user.setFailedLogins(0);
                user.setLockedUntil(now.plus(Duration.ofMinutes(settings.getInt("auth.lock-minutes"))));
                auditService.record(user.getId(), "USER", user.getId(), "LOGIN_LOCKED", null, null);
                log.warn("Login locked after {} failures: userId={}", failed, user.getId());
                throw new BusinessException(ErrorCode.AUTH_ACCOUNT_TEMP_LOCKED);
            }
            user.setFailedLogins(failed);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (user.getStatus() == UserStatus.PENDING) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        auditService.record(user.getId(), "USER", user.getId(), "LOGIN", null, null);
        log.info("Login success: userId={}", user.getId());
        return issueTokens(user);
    }

    /**
     * Refresh with rotation: the old refresh token is used up and a new pair is returned.
     * Re-using an already used refresh token = possible theft, so ALL refresh tokens of that user are revoked.
     *
     * @throws BusinessException AUTH_TOKEN_INVALID, AUTH_TOKEN_USED, AUTH_TOKEN_EXPIRED, AUTH_ACCOUNT_LOCKED
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse refresh(String rawToken) {
        UserToken token = findToken(rawToken, TokenPurpose.REFRESH);
        Instant now = Instant.now(clock);
        if (token.getUsedAt() != null) {
            tokenRepository.invalidateAll(token.getUserId(), TokenPurpose.REFRESH, now);
            log.warn("Refresh token reused, all sessions revoked: userId={}", token.getUserId());
            throw new BusinessException(ErrorCode.AUTH_TOKEN_USED);
        }
        if (!now.isBefore(token.getExpiresAt())) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
        token.setUsedAt(now);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }
        log.info("Token refreshed: userId={}", user.getId());
        return issueTokens(user);
    }

    /** S05 Logout: revokes the given refresh token. Unknown or used tokens are ignored (idempotent). */
    @Transactional
    public MessageResponse logout(String rawToken) {
        String clean = Texts.clean(rawToken);
        if (clean != null) {
            tokenRepository.findByTokenHashAndPurpose(SecureTokens.hash(clean), TokenPurpose.REFRESH)
                    .filter(t -> t.getUsedAt() == null)
                    .ifPresent(t -> {
                        t.setUsedAt(Instant.now(clock));
                        log.info("Logout: userId={}", t.getUserId());
                    });
        }
        return new MessageResponse("Logged out");
    }

    /**
     * S05 Change password: current password must match; the new one follows the password policy
     * and differs from the current one. All refresh tokens are revoked (other devices log out).
     *
     * @throws BusinessException USER_NOT_FOUND, AUTH_WRONG_PASSWORD, AUTH_PASSWORD_WEAK, AUTH_PASSWORD_SAME_AS_OLD
     */
    @Transactional
    public MessageResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (request.currentPassword() == null
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_WRONG_PASSWORD);
        }
        PasswordPolicy.check(request.newPassword());
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_SAME_AS_OLD);
        }
        Instant now = Instant.now(clock);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(now);
        tokenRepository.invalidateAll(userId, TokenPurpose.REFRESH, now);
        auditService.record(userId, "USER", userId, "CHANGE_PASSWORD", null, null);
        log.info("Password changed: userId={}", userId);
        return new MessageResponse("Password changed");
    }

    /**
     * S04 Forgot password (BR-03). Always returns the same message, whether the email exists or not,
     * so nobody can probe which emails have an account. Older reset links of the user stop working.
     */
    @Transactional
    public MessageResponse forgotPassword(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        User user = email == null ? null : userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            log.info("Password reset requested for unknown or inactive email");
            return new MessageResponse(GENERIC_RESET_MESSAGE);
        }
        tokenRepository.invalidateAll(user.getId(), TokenPurpose.RESET, Instant.now(clock));
        String token = issueOneTimeToken(user.getId(), TokenPurpose.RESET,
                Duration.ofMinutes(settings.getInt("auth.reset-token-minutes")));
        mailService.send(user.getEmail(), "Reset your LabFlow password",
                "Open this link within " + settings.getInt("auth.reset-token-minutes") + " minutes: "
                        + publicUrl + "/reset-password?token=" + token);
        log.info("Password reset link sent: userId={}", user.getId());
        return new MessageResponse(GENERIC_RESET_MESSAGE);
    }

    /**
     * S04 Reset password with a one-time token (BR-03): valid, unused, not expired.
     * Kiểm mật khẩu mới TRƯỚC khi dùng token, để mật khẩu yếu không làm "cháy" link.
     *
     * @throws BusinessException AUTH_PASSWORD_WEAK, AUTH_TOKEN_INVALID, AUTH_TOKEN_USED, AUTH_TOKEN_EXPIRED
     */
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        PasswordPolicy.check(request.newPassword());
        UserToken token = consumeToken(request.token(), TokenPurpose.RESET);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
        Instant now = Instant.now(clock);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(now);
        tokenRepository.invalidateAll(user.getId(), TokenPurpose.REFRESH, now);
        auditService.record(user.getId(), "USER", user.getId(), "RESET_PASSWORD", null, null);
        log.info("Password reset: userId={}", user.getId());
        return new MessageResponse("Password has been reset, please log in");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    static String normalizeEmail(String raw) {
        String t = Texts.clean(raw);
        return t == null ? null : t.toLowerCase(Locale.ROOT);
    }

    private TokenResponse issueTokens(User user) {
        String access = accessTokenService.issue(user.getId(), user.getEmail(), user.roleCodes());
        String refresh = issueOneTimeToken(user.getId(), TokenPurpose.REFRESH,
                Duration.ofDays(jwtProperties.refreshDays()));
        return new TokenResponse(access, "Bearer", accessTokenService.accessSeconds(), refresh,
                user.getId(), user.getFullName(), user.roleCodes());
    }

    private String issueOneTimeToken(Long userId, TokenPurpose purpose, Duration lifetime) {
        String raw = SecureTokens.newToken();
        Instant now = Instant.now(clock);
        UserToken token = new UserToken();
        token.setUserId(userId);
        token.setPurpose(purpose);
        token.setTokenHash(SecureTokens.hash(raw));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(lifetime));
        tokenRepository.save(token);
        return raw;
    }

    private UserToken findToken(String rawToken, TokenPurpose purpose) {
        String clean = Texts.clean(rawToken);
        if (clean == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return tokenRepository.findByTokenHashAndPurpose(SecureTokens.hash(clean), purpose)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
    }

    /** Finds a one-time token, checks it is unused and unexpired, then marks it used. */
    private UserToken consumeToken(String rawToken, TokenPurpose purpose) {
        UserToken token = findToken(rawToken, purpose);
        Instant now = Instant.now(clock);
        if (token.getUsedAt() != null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_USED);
        }
        if (!now.isBefore(token.getExpiresAt())) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
        token.setUsedAt(now);
        return token;
    }
}
