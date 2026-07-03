package com.petcare.common.exception;

import com.petcare.ai.provider.AiProviderException;
import com.petcare.ai.provider.AiProviderUnavailableException;
import com.petcare.common.api.ApiError;
import com.petcare.common.api.ApiResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler that converts exceptions into unified ApiResponse format.
 * Ensures error responses never leak SQL, stack traces, or secrets.
 *
 * Spring Security exceptions are primarily handled by RestAuthenticationEntryPoint and
 * RestAccessDeniedHandler. This class provides fallback coverage for edge cases.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles bean validation errors (e.g. @NotBlank, @Size).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR,
                "请求参数不合法",
                fieldErrors
        );
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles business rule violations thrown by service layer.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        HttpStatus status = resolveHttpStatus(ex.getCode());
        ApiResponse<Void> body = ApiResponse.error(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Handles Spring Security access denied (403).
     * This is a fallback; primary handling is in RestAccessDeniedHandler.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.FORBIDDEN, "权限不足，无法访问");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Handles Spring Security authentication failures (401).
     * This is a fallback; primary handling is in RestAuthenticationEntryPoint.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.UNAUTHORIZED, "认证失败，请先登录");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    /**
     * Handles AI Provider unavailability (503).
     * Returns a safe error without leaking provider details.
     */
    @ExceptionHandler(AiProviderUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleProviderUnavailable(AiProviderUnavailableException ex) {
        log.warn("AI Provider unavailable: {}", ex.getMessage());
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.AI_PROVIDER_NOT_ENABLED, "AI 服务暂未启用");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * Handles general AI Provider errors (503).
     * Logs internally but returns a safe, generic message.
     */
    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiResponse<Void>> handleProviderError(AiProviderException ex) {
        log.error("AI Provider error [{}]: {}", ex.getInternalCode(), ex.getMessage());
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.AI_PROVIDER_UNAVAILABLE, "AI 服务暂时不可用，请稍后重试");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * Handles 404 Not Found for unknown API paths.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.RESOURCE_NOT_FOUND,
                "请求的资源不存在"
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handles 405 Method Not Allowed when a request uses an HTTP verb not supported
     * by the matched handler. Without this, Spring's default falls through to the
     * generic 500 handler, which is incorrect and misleading.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.debug("Method not supported: {} for {}", ex.getMethod(), ex.getMessage());
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.METHOD_NOT_ALLOWED,
                "请求方法不被支持"
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    /**
     * Handles path/query parameter type conversion failures (e.g. "abc" for Long).
     * Returns 400 with a safe message, never leaking internal details.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String paramName = ex.getName();
        String rejectedValue = ex.getValue() != null ? ex.getValue().toString() : "null";
        log.debug("Parameter type mismatch: {}={}", paramName, rejectedValue);
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR,
                "请求参数不合法"
        );
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles unreadable request body (malformed JSON, wrong types).
     * Returns 400 with a safe message, never leaking internal details.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR,
                "请求数据格式不正确"
        );
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Catches all unhandled exceptions.
     * Logs the full stack on the server but returns a generic message to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.INTERNAL_ERROR,
                "服务内部错误，请稍后重试"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private HttpStatus resolveHttpStatus(String code) {
        return switch (code) {
            case ErrorCode.VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case ErrorCode.RESOURCE_NOT_FOUND,
                 ErrorCode.BOOKING_ADDRESS_NOT_FOUND,
                 ErrorCode.COMMUNITY_POST_NOT_FOUND,
                 ErrorCode.COMMUNITY_TOPIC_NOT_FOUND,
                 ErrorCode.COMMUNITY_COMMENT_NOT_FOUND,
                 ErrorCode.PRODUCT_NOT_FOUND,
                 ErrorCode.CART_ITEM_NOT_FOUND,
                 ErrorCode.PRODUCT_ORDER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ErrorCode.STATE_CONFLICT,
                 ErrorCode.BOOKING_TIME_CONFLICT,
                 ErrorCode.BOOKING_STATUS_INVALID,
                 ErrorCode.BOOKING_RETRY_EXHAUSTED,
                 ErrorCode.COMMUNITY_DUPLICATE_LIKE,
                 ErrorCode.COMMUNITY_DUPLICATE_FAVORITE,
                 ErrorCode.COMMUNITY_DUPLICATE_REPORT,
                 ErrorCode.COMMUNITY_REVIEW_STATUS_INVALID,
                 ErrorCode.COMMUNITY_SENSITIVE_WORD_DUPLICATE,
                 ErrorCode.PRODUCT_STOCK_INSUFFICIENT,
                 ErrorCode.PRODUCT_ORDER_STATUS_INVALID -> HttpStatus.CONFLICT;
            case ErrorCode.UNAUTHORIZED,
                 ErrorCode.INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case ErrorCode.FORBIDDEN,
                 ErrorCode.CART_ITEM_FORBIDDEN,
                 ErrorCode.PRODUCT_ORDER_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case ErrorCode.WECHAT_LOGIN_NOT_ENABLED,
                 ErrorCode.PHONE_ALREADY_REGISTERED,
                 ErrorCode.SECURITY_QUESTION_NOT_SET,
                 ErrorCode.SECURITY_ANSWER_INCORRECT -> HttpStatus.UNPROCESSABLE_ENTITY;
            case ErrorCode.BOOKING_SLOT_UNAVAILABLE,
                 ErrorCode.BOOKING_SERVICE_UNAVAILABLE,
                 ErrorCode.BOOKING_DATE_OUT_OF_RANGE,
                 ErrorCode.BOOKING_HOME_DISTANCE_EXCEEDED,
                 ErrorCode.BOOKING_ADDRESS_REQUIRED,
                 ErrorCode.BOOKING_STAFF_UNAVAILABLE,
                 ErrorCode.BUSINESS_RULE_VIOLATION,
                 ErrorCode.COMMUNITY_CONTENT_REJECTED,
                 ErrorCode.COMMUNITY_CONTENT_PENDING_REVIEW,
                 ErrorCode.COMMUNITY_POST_NOT_VISIBLE,
                 ErrorCode.COMMUNITY_FILE_UPLOAD_NOT_DECIDED,
                 ErrorCode.PRODUCT_NOT_ON_SALE,
                 ErrorCode.PRODUCT_NOT_PICKUP_ONLY,
                 ErrorCode.CART_EMPTY,
                 ErrorCode.CART_NO_CHECKED_ITEMS,
                 ErrorCode.PRODUCT_ORDER_PAYMENT_REQUIRED,
                 ErrorCode.PRODUCT_ORDER_PICKUP_REQUIRED,
                 ErrorCode.PRODUCT_ORDER_AMOUNT_INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }
}
