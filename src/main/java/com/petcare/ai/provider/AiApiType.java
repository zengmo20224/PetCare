package com.petcare.ai.provider;

/**
 * AI API call type. Used in usage logging and request routing.
 * Matches schema.sql ai_usage_log.api_type values.
 */
public enum AiApiType {
    CUSTOMER_SERVICE,
    CHAT,
    CONTENT_GENERATE,
    ANALYSIS,
    /** M8.3：文本内容审核（ModerationAgent，docs/09 §5.3.2）。 */
    MODERATION
}
