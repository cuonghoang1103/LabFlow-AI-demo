package vn.swt301.labflowdemo.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Turns exceptions into the JSON envelope and writes ONE consistent log line per error.
 * Log format (dùng cho dòng "Log message" của 5.1): {@code WARN [ERROR_CODE] message}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException ex) {
        log.warn("[{}] {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getCode().status())
                .body(ApiResponse.fail(ex.getCode().name(), ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException ex) {
        List<ApiResponse.FieldProblem> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiResponse.FieldProblem(f.getField(), f.getDefaultMessage()))
                .toList();
        log.warn("[{}] {}", ErrorCode.VALIDATION_ERROR, fields);
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR.name(), ErrorCode.VALIDATION_ERROR.defaultMessage(), fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<ApiResponse<Void>> unreadable(Exception ex) {
        log.warn("[{}] {}", ErrorCode.VALIDATION_ERROR, ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR.name(), "Request body or parameter is malformed", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException ex) {
        log.warn("[{}] {}", ErrorCode.FORBIDDEN, ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ErrorCode.FORBIDDEN.name(), ErrorCode.FORBIDDEN.defaultMessage(), null));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> optimistic(ObjectOptimisticLockingFailureException ex) {
        log.warn("[{}] {}", ErrorCode.CONCURRENT_UPDATE, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ErrorCode.CONCURRENT_UPDATE.name(), ErrorCode.CONCURRENT_UPDATE.defaultMessage(), null));
    }

    /** A UNIQUE / CHECK / EXCLUDE constraint said no - the database is the last line of defence. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> integrity(DataIntegrityViolationException ex) {
        log.warn("[DATA_CONFLICT] {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("DATA_CONFLICT", "The data conflicts with existing data", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        log.error("[INTERNAL_ERROR] {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("INTERNAL_ERROR", "Unexpected error", null));
    }
}
