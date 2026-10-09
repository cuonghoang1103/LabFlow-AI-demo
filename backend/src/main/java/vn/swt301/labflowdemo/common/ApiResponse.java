package vn.swt301.labflowdemo.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One envelope for every response (plan: "API chính - hợp đồng v1"):
 * {@code {"success": true, "data": ...}} or {@code {"success": false, "error": {"code", "message", "fields"}}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(String code, String message, List<FieldProblem> fields) {
        return new ApiResponse<>(false, null, new ApiError(code, message, fields));
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ApiError(String code, String message, List<FieldProblem> fields) {
    }

    public record FieldProblem(String field, String message) {
    }
}
