package com.petcare.common.api;

import java.util.List;

/**
 * Unified API response envelope for all endpoints.
 *
 * @param <T> type of the data payload
 */
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ApiError error;
    private final Object meta;

    private ApiResponse(boolean success, T data, ApiError error, Object meta) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.meta = meta;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, Object meta) {
        return new ApiResponse<>(true, data, null, meta);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message, null), null);
    }

    public static <T> ApiResponse<T> error(String code, String message, List<ApiError.FieldError> details) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details), null);
    }

    // Getters (no setters — immutable)

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public ApiError getError() {
        return error;
    }

    public Object getMeta() {
        return meta;
    }
}
