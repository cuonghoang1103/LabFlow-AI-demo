package vn.swt301.labflowdemo.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.auth.AuthDtos.ChangePasswordRequest;
import vn.swt301.labflowdemo.auth.AuthDtos.LoginRequest;
import vn.swt301.labflowdemo.auth.AuthDtos.MessageResponse;
import vn.swt301.labflowdemo.auth.AuthDtos.TokenResponse;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.common.Texts;
import vn.swt301.labflowdemo.notification.MailService;
import vn.swt301.labflowdemo.security.AccessTokenService;
import vn.swt301.labflowdemo.security.JwtProperties;
import vn.swt301.labflowdemo.settings.SettingsService;
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

/**
 * Register, verify email, login, refresh, logout, change / forgot / reset password.
 * Screens S01 (C3), S03 (C1), S04 (C3), S05 (C1). Business rules BR-01, BR-02, BR-03.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {


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

}
