package vn.swt301.labflowdemo.auth;

import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;

/**
 * Password rule used by register, change password, reset password and admin-created users:
 * 8-64 characters, at least one letter and at least one digit.
 * (64 giữ an toàn cho BCrypt - BCrypt chỉ đọc 72 byte đầu.)
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    private PasswordPolicy() {
    }

    /** @throws BusinessException AUTH_PASSWORD_WEAK when the password breaks the rule */
    public static void check(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_WEAK);
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_WEAK);
        }
    }
}
