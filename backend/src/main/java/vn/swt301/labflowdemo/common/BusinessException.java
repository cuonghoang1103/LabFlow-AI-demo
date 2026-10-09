package vn.swt301.labflowdemo.common;

import lombok.Getter;

/**
 * The only exception a service throws for a broken business rule.
 * Unit tests assert on {@link #getCode()} - stable, unlike the message text.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
