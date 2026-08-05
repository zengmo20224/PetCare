package com.petcare.ai.controller;

import com.petcare.ai.dto.AiMessageCreateRequest;
import com.petcare.ai.service.StreamingConversationService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.security.SecurityContextHelper;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 客服 SSE 流式端点（M8.1，对标 docs/09 §7.4）。
 * <p>
 * <b>条件装配</b>：仅当 {@code petcare.ai.agent-enabled=true} 时注册。
 * agent-enabled=false 时端点不存在（404），符合降级哲学——V1 同步端点仍在
 * {@link AiConversationController#sendMessage}。
 */
@RestController
@RequestMapping("/api/v1/ai/conversations")
@ConditionalOnProperty(prefix = "petcare.ai", name = "agent-enabled", havingValue = "true")
public class AiConversationStreamController {

    private final StreamingConversationService streamingService;

    public AiConversationStreamController(StreamingConversationService streamingService) {
        this.streamingService = streamingService;
    }

    /**
     * 流式发送消息（打字机效果）。
     * <p>
     * 事件类型：
     * <ul>
     *   <li>{@code chunk}：文本块（data 为字符串片段）</li>
     *   <li>{@code done}：结束（data 为 {@code [DONE]}）</li>
     *   <li>{@code error}：降级（data 为降级文案）</li>
     * </ul>
     */
    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @PathVariable Long id,
            @Valid @RequestBody AiMessageCreateRequest request) {
        Long currentUserId = resolveCurrentUserId();
        return streamingService.streamMessage(currentUserId, id, request);
    }

    private Long resolveCurrentUserId() {
        return SecurityContextHelper.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"));
    }
}
