package com.petcare.common.exception;

/**
 * Application-wide error codes.
 * Each code maps to a specific error scenario with a stable identifier.
 */
public final class ErrorCode {

    private ErrorCode() {
        // prevent instantiation
    }

    public static final String VALIDATION_ERROR = "validation_error";
    public static final String RESOURCE_NOT_FOUND = "resource_not_found";
    public static final String BUSINESS_RULE_VIOLATION = "business_rule_violation";
    public static final String STATE_CONFLICT = "state_conflict";
    public static final String UNAUTHORIZED = "unauthorized";
    public static final String FORBIDDEN = "forbidden";
    public static final String WECHAT_LOGIN_NOT_ENABLED = "wechat_login_not_enabled";
    public static final String METHOD_NOT_ALLOWED = "method_not_allowed";

    // Auth error codes
    public static final String PHONE_ALREADY_REGISTERED = "phone_already_registered";
    public static final String INVALID_CREDENTIALS = "invalid_credentials";
    public static final String SECURITY_QUESTION_NOT_SET = "security_question_not_set";
    public static final String SECURITY_ANSWER_INCORRECT = "security_answer_incorrect";
    public static final String INVALID_TOKEN = "invalid_token";
    public static final String EXPIRED_TOKEN = "expired_token";
    public static final String INTERNAL_ERROR = "internal_error";

    // User ban error codes
    public static final String USER_BANNED = "user_banned";
    public static final String PHONE_BANNED = "phone_banned";

    // Booking error codes
    public static final String BOOKING_TIME_CONFLICT = "booking_time_conflict";
    public static final String BOOKING_SLOT_UNAVAILABLE = "booking_slot_unavailable";
    public static final String BOOKING_STATUS_INVALID = "booking_status_invalid";
    public static final String BOOKING_SERVICE_UNAVAILABLE = "booking_service_unavailable";
    public static final String BOOKING_DATE_OUT_OF_RANGE = "booking_date_out_of_range";
    public static final String BOOKING_HOME_DISTANCE_EXCEEDED = "booking_home_distance_exceeded";
    public static final String BOOKING_ADDRESS_REQUIRED = "booking_address_required";
    public static final String BOOKING_ADDRESS_NOT_FOUND = "booking_address_not_found";
    public static final String BOOKING_STAFF_UNAVAILABLE = "booking_staff_unavailable";
    public static final String BOOKING_RETRY_EXHAUSTED = "booking_retry_exhausted";

    // Community error codes
    public static final String COMMUNITY_POST_NOT_FOUND = "community_post_not_found";
    public static final String COMMUNITY_TOPIC_NOT_FOUND = "community_topic_not_found";
    public static final String COMMUNITY_COMMENT_NOT_FOUND = "community_comment_not_found";
    public static final String COMMUNITY_CONTENT_REJECTED = "community_content_rejected";
    public static final String COMMUNITY_CONTENT_PENDING_REVIEW = "community_content_pending_review";
    public static final String COMMUNITY_POST_NOT_VISIBLE = "community_post_not_visible";
    public static final String COMMUNITY_DUPLICATE_LIKE = "community_duplicate_like";
    public static final String COMMUNITY_DUPLICATE_FAVORITE = "community_duplicate_favorite";
    public static final String COMMUNITY_DUPLICATE_REPORT = "community_duplicate_report";
    public static final String COMMUNITY_REVIEW_STATUS_INVALID = "community_review_status_invalid";
    public static final String COMMUNITY_SENSITIVE_WORD_DUPLICATE = "community_sensitive_word_duplicate";
    public static final String COMMUNITY_FILE_UPLOAD_NOT_DECIDED = "community_file_upload_not_decided";

    // Product / Cart / Order error codes
    public static final String PRODUCT_NOT_FOUND = "product_not_found";
    public static final String PRODUCT_NOT_ON_SALE = "product_not_on_sale";
    public static final String PRODUCT_NOT_PICKUP_ONLY = "product_not_pickup_only";
    public static final String PRODUCT_STOCK_INSUFFICIENT = "product_stock_insufficient";
    public static final String CART_ITEM_NOT_FOUND = "cart_item_not_found";
    public static final String CART_ITEM_FORBIDDEN = "cart_item_forbidden";
    public static final String CART_EMPTY = "cart_empty";
    public static final String CART_NO_CHECKED_ITEMS = "cart_no_checked_items";
    public static final String PRODUCT_ORDER_NOT_FOUND = "product_order_not_found";
    public static final String PRODUCT_ORDER_FORBIDDEN = "product_order_forbidden";
    public static final String PRODUCT_ORDER_STATUS_INVALID = "product_order_status_invalid";
    public static final String PRODUCT_ORDER_PAYMENT_REQUIRED = "product_order_payment_required";
    public static final String PRODUCT_ORDER_PICKUP_REQUIRED = "product_order_pickup_required";
    public static final String PRODUCT_ORDER_AMOUNT_INVALID = "product_order_amount_invalid";

    // AI error codes
    public static final String AI_PROVIDER_NOT_ENABLED = "ai_provider_not_enabled";
    public static final String AI_PROVIDER_CONFIGURATION_INVALID = "ai_provider_configuration_invalid";
    public static final String AI_PROVIDER_UNAVAILABLE = "ai_provider_unavailable";
    public static final String AI_PROVIDER_TIMEOUT = "ai_provider_timeout";
    public static final String AI_REQUEST_INVALID = "ai_request_invalid";
    public static final String AI_CONVERSATION_NOT_FOUND = "ai_conversation_not_found";
    public static final String AI_CONVERSATION_FORBIDDEN = "ai_conversation_forbidden";
    public static final String AI_CONVERSATION_TYPE_INVALID = "ai_conversation_type_invalid";
    public static final String AI_OUTPUT_REJECTED = "ai_output_rejected";
    public static final String AI_MEDICAL_SAFETY_BLOCKED = "ai_medical_safety_blocked";
    public static final String AI_GROUNDING_CONTEXT_MISSING = "ai_grounding_context_missing";
    public static final String AI_ANALYSIS_RANGE_INVALID = "ai_analysis_range_invalid";
    public static final String AI_ANALYSIS_DATA_INSUFFICIENT = "ai_analysis_data_insufficient";
}
