package com.petcare.ai.service;

import com.petcare.ai.domain.HighRiskSymptomDetector;
import com.petcare.ai.dto.AiMessageCreateRequest;
import com.petcare.ai.dto.AiMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 分块流式 SSE 编排（M8.1，对标 docs/09 §7.4 / §7.5）。
 * <p>
 * <b>分块流式</b>（非真流式）：后端调同步 {@code complete()} 拿完整文本 → 整段过护栏 → 按句边界切分
 * → SseEmitter 逐块发送，前端呈现打字机效果。首 token 延迟 = 完整生成时间（真流式留作 M8.1+ 优化）。
 * <p>
 * <b>异步生命周期（2026-08-15 修复）</b>：生成与发送必须在独立线程执行，emitter 由 controller
 * 立即返回。若在 controller 线程内同步 send + complete，Spring MVC 走非标准完成路径，Tomcat
 * 关闭连接时不写 chunked 终止块——经 vite/node 代理时 fetch reader 永远不结束，前端卡
 * "正在思考"（验收缺陷，回归守卫见 AsyncLifecycle 测试）。
 * <p>
 * <b>流式护栏（B8）</b>：
 * <ul>
 *   <li>前置护栏（HighRiskSymptomDetector）：用户消息完整，流式前同步执行，命中直接发固定兽医文案；</li>
 *   <li>后置护栏（PetMedicalSafetyPolicy / AiOutputSafetyPolicy）：在拿到完整文本后、切分发块前执行
 *       （由 {@link AiConversationApplicationService#sendMessage} 内部 V1 护栏完成，本类拿到的是已过护栏的文本）。</li>
 * </ul>
 * 这满足 docs/09 §7.5"结束护栏"语义；命中即整段替换为 fallback 再切分发送。
 */
public class StreamingConversationService {

    private static final Logger log = LoggerFactory.getLogger(StreamingConversationService.class);

    /** SSE 超时（毫秒）。单门店客服对话足够。 */
    private static final long SSE_TIMEOUT = 120_000L;
    /** 每块发送间隔（毫秒），模拟打字机。 */
    private static final long CHUNK_INTERVAL_MS = 30L;
    /** 单块最大字符数（防爆。超长句按此二次切分）。 */
    private static final int MAX_CHUNK_CHARS = 80;

    /** 句边界切分：。！？.!? 换行。保留分隔符。 */
    private static final Pattern SENTENCE_SPLIT = Pattern.compile(
            "(?<=[。！？!?\\n])");

    private final AiConversationApplicationService conversationService;
    private final TaskExecutor executor;

    public StreamingConversationService(AiConversationApplicationService conversationService) {
        this(conversationService, new SimpleAsyncTaskExecutor("ai-sse-"));
    }

    StreamingConversationService(AiConversationApplicationService conversationService, TaskExecutor executor) {
        this.conversationService = conversationService;
        this.executor = executor;
    }

    /**
     * 流式发送一条消息。
     * <p>
     * 流程：注册超时回调 → 提交后台任务 → 立即返回 emitter。
     * 后台任务内：前置护栏 → 同步生成完整回复（含后置护栏）→ 切分 → 逐块 SSE 发送 → complete。
     *
     * @param currentUserId  当前用户 ID（Controller 已解析，非空）
     * @param conversationId 会话 ID
     * @param request        消息请求
     * @return 配置好的 SseEmitter（调用方直接返回给 MVC）
     */
    public SseEmitter streamMessage(Long currentUserId, Long conversationId, AiMessageCreateRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitter.onTimeout(() -> log.warn("[AI] SSE timeout (conversation={})", conversationId));
        try {
            executor.execute(() -> runStream(emitter, currentUserId, conversationId, request));
        } catch (RuntimeException e) {
            // executor 拒绝（如关停中）：走缓存路径发降级文案（几乎不可达）
            log.warn("[AI] SSE task rejected (conversation={}): {}", conversationId, e.getMessage());
            sendErrorEvent(emitter, "AI 暂时无法回复，请稍后再试。");
        }
        return emitter;
    }

    private void runStream(SseEmitter emitter, Long currentUserId, Long conversationId,
                           AiMessageCreateRequest request) {
        // PET_CHAT 前置护栏：高危症状直接发固定兽医文案，不调 Provider（B8 前置）
        if (HighRiskSymptomDetector.isHighRisk(request.content())) {
            sendAndComplete(emitter, HighRiskSymptomDetector.getFixedSafetyResponse());
            return;
        }

        // 同步获取完整回复（sendMessage 内部已做后置护栏：PetMedicalSafetyPolicy / AiOutputSafetyPolicy）
        AiMessageResponse response;
        try {
            response = conversationService.sendMessage(currentUserId, conversationId, request);
        } catch (Exception e) {
            // 任何生成失败：发送降级文案，不向上游抛（SSE 已开流）
            log.warn("[AI] SSE stream generation failed (conversation={}): {}", conversationId, e.getMessage());
            sendErrorEvent(emitter, "AI 暂时无法回复，请稍后再试。");
            return;
        }

        // 切分完整文本并逐块发送
        String fullText = response.content() == null ? "" : response.content();
        emitChunks(emitter, splitIntoChunks(fullText));
    }

    /**
     * 把完整文本按句边界切分为多个块。
     */
    static List<String> splitIntoChunks(String text) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }
        List<String> chunks = new ArrayList<>();
        for (String sentence : SENTENCE_SPLIT.split(text)) {
            if (sentence == null || sentence.isEmpty()) {
                continue;
            }
            // 超长句二次切分（防爆）
            if (sentence.length() > MAX_CHUNK_CHARS) {
                for (int i = 0; i < sentence.length(); i += MAX_CHUNK_CHARS) {
                    int end = Math.min(i + MAX_CHUNK_CHARS, sentence.length());
                    chunks.add(sentence.substring(i, end));
                }
            } else {
                chunks.add(sentence);
            }
        }
        if (chunks.isEmpty()) {
            chunks.add(text);
        }
        return chunks;
    }

    private void emitChunks(SseEmitter emitter, List<String> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            try {
                emitter.send(SseEmitter.event()
                        .name("chunk")
                        .data(chunks.get(i)));
                Thread.sleep(CHUNK_INTERVAL_MS);
            } catch (IOException e) {
                log.debug("[AI] SSE client disconnected mid-stream at chunk {}: {}", i, e.getMessage());
                return; // 客户端断开，停止发送
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("[AI] SSE stream interrupted at chunk {}", i);
                return;
            } catch (IllegalStateException e) {
                // emitter 已 complete/timeout
                log.debug("[AI] SSE emitter already completed at chunk {}", i);
                return;
            }
        }
        // 发送结束事件
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        } catch (IOException | IllegalStateException e) {
            log.debug("[AI] SSE done event send failed: {}", e.getMessage());
        }
        emitter.complete();
    }

    private void sendAndComplete(SseEmitter emitter, String text) {
        try {
            for (String chunk : splitIntoChunks(text)) {
                emitter.send(SseEmitter.event().name("chunk").data(chunk));
                Thread.sleep(CHUNK_INTERVAL_MS);
            }
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        } catch (IOException | InterruptedException | IllegalStateException e) {
            log.debug("[AI] SSE fallback send interrupted: {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        emitter.complete();
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (IOException | IllegalStateException e) {
            log.debug("[AI] SSE error event send failed: {}", e.getMessage());
        }
        emitter.complete();
    }
}
