package vn.swt301.labflowdemo.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.auth.PasswordPolicy;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.common.PageResponse;
import vn.swt301.labflowdemo.common.Texts;
import vn.swt301.labflowdemo.user.UserDtos.CreateUserRequest;
import vn.swt301.labflowdemo.user.UserDtos.UpdateUserRequest;
import vn.swt301.labflowdemo.user.UserDtos.UserView;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * User administration (S10 list, S11 detail - owner C5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    /** S10: search by email/name, filter by role/status, server-side sort + paging (size capped at 100). */
    @Transactional(readOnly = true)
    public PageResponse<UserView> searchUsers(String q, RoleCode role, UserStatus status, Pageable pageable) {
        Pageable safe = pageable.getPageSize() > MAX_PAGE_SIZE
                ? PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort()) : pageable;
        return PageResponse.of(userRepository.search(Texts.clean(q), role, status, safe), UserView::of);
    }

    @Transactional(readOnly = true)
    public UserView getUser(Long id) {
        return UserView.of(find(id));
    }

    /**
     * S11 Admin creates an account (any email domain, e.g. staff) - ACTIVE immediately.
     *
     * @throws BusinessException AUTH_EMAIL_INVALID, AUTH_FULL_NAME_INVALID, AUTH_PASSWORD_WEAK,
     *                           USER_ROLES_REQUIRED, AUTH_EMAIL_TAKEN
     */
    @Transactional
    public UserView createUser(CreateUserRequest request, Long actorId) {
        String email = Texts.clean(request.email());
        if (email == null || email.length() > 100 || !EMAIL.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_INVALID);
        }
        email = email.toLowerCase(Locale.ROOT);
        if (!Texts.lengthBetween(request.fullName(), 1, 100)) {
            throw new BusinessException(ErrorCode.AUTH_FULL_NAME_INVALID);
        }
        PasswordPolicy.check(request.password());
        Set<Role> roles = loadRoles(request.roles());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_TAKEN);
        }
        Instant now = Instant.now(clock);
        User user = new User();
        user.setEmail(email);
        user.setFullName(Texts.clean(request.fullName()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(roles);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        auditService.record(actorId, "USER", user.getId(), "CREATE", null, UserView.of(user));
        log.info("User created by admin: id={}, roles={}, by={}", user.getId(), user.roleCodes(), actorId);
        return UserView.of(user);
    }

    /**
     * S11 Admin edits name and roles. An admin cannot remove their own ADMIN role, and the last
     * active admin cannot lose it (nobody could manage the system any more).
     *
     * @throws BusinessException USER_NOT_FOUND, AUTH_FULL_NAME_INVALID, USER_ROLES_REQUIRED,
     *                           USER_CANNOT_CHANGE_SELF, USER_LAST_ADMIN
     */
    @Transactional
    public UserView updateUser(Long id, UpdateUserRequest request, Long actorId) {
        User user = find(id);
        if (!Texts.lengthBetween(request.fullName(), 1, 100)) {
            throw new BusinessException(ErrorCode.AUTH_FULL_NAME_INVALID);
        }
        Set<Role> roles = loadRoles(request.roles());
        boolean losesAdmin = user.hasRole(RoleCode.ADMIN) && !request.roles().contains(RoleCode.ADMIN);
        if (losesAdmin && user.getId().equals(actorId)) {
            throw new BusinessException(ErrorCode.USER_CANNOT_CHANGE_SELF);
        }
        if (losesAdmin && user.getStatus() == UserStatus.ACTIVE && userRepository.countActiveAdmins() <= 1) {
            throw new BusinessException(ErrorCode.USER_LAST_ADMIN);
        }
        UserView before = UserView.of(user);
        user.setFullName(Texts.clean(request.fullName()));
        user.setRoles(roles);
        user.setUpdatedAt(Instant.now(clock));
        auditService.record(actorId, "USER", id, "UPDATE", before, UserView.of(user));
        log.info("User updated: id={}, roles={}, by={}", id, user.roleCodes(), actorId);
        return UserView.of(user);
    }

    /**
     * S10 Lock / unlock an account. Locking also logs the user out everywhere (refresh tokens revoked);
     * unlocking clears the BR-02 failed-login counter.
     *
     * @throws BusinessException USER_NOT_FOUND, VALIDATION_ERROR (status PENDING), USER_CANNOT_CHANGE_SELF,
     *                           USER_STATUS_UNCHANGED, USER_LAST_ADMIN
     */
    @Transactional
    public UserView changeStatus(Long id, UserStatus newStatus, Long actorId) {
        if (newStatus != UserStatus.ACTIVE && newStatus != UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Status must be ACTIVE or LOCKED");
        }
        User user = find(id);
        if (user.getId().equals(actorId)) {
            throw new BusinessException(ErrorCode.USER_CANNOT_CHANGE_SELF);
        }
        if (user.getStatus() == newStatus) {
            throw new BusinessException(ErrorCode.USER_STATUS_UNCHANGED);
        }
        Instant now = Instant.now(clock);
        if (newStatus == UserStatus.LOCKED) {
            if (user.hasRole(RoleCode.ADMIN) && user.getStatus() == UserStatus.ACTIVE
                    && userRepository.countActiveAdmins() <= 1) {
                throw new BusinessException(ErrorCode.USER_LAST_ADMIN);
            }
            tokenRepository.invalidateAll(id, TokenPurpose.REFRESH, now);
        } else {
            user.setFailedLogins(0);
            user.setLockedUntil(null);
        }
        String before = user.getStatus().name();
        user.setStatus(newStatus);
        user.setUpdatedAt(now);
        auditService.record(actorId, "USER", id, "STATUS", before, newStatus.name());
        log.info("User status changed: id={}, {} -> {}, by={}", id, before, newStatus, actorId);
        return UserView.of(user);
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Set<Role> loadRoles(Set<RoleCode> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new BusinessException(ErrorCode.USER_ROLES_REQUIRED);
        }
        return new HashSet<>(roleRepository.findByCodeIn(codes));
    }
}
