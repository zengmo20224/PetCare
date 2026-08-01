package com.petcare.common.api;

import java.util.List;

/**
 * Structured error details returned in error responses.
 */
public class ApiError {

    private final String code;
    private final String message;
    private final List<FieldError> details;

    public ApiError(String code, String message, List<FieldError> details) {
        this.code = code;
        this.message = message;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public List<FieldError> getDetails() {
        return details;
    }

    /**
     * Field-level validation error detail.
     */
    public static class FieldError {

        private final String field;
        private final String message;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}
