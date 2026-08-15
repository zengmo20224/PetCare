package com.petcare.ai.service;

import com.petcare.ai.domain.HighRiskSymptomDetector;
import com.petcare.ai.dto.AiMessageCreateRequest;
import com.petcare.ai.dto.AiMessageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StreamingConversationService} 单测。
 * <p>
 * 验证：句切分逻辑（纯函数）+ 高危前置护栏 + 异步生命周期 + Provider 失败降级。
 * <p>
 * complete 状态观测说明：单测环境 emitter 未经过 MVC initialize，onCompletion 回调
 * 不会被触发，故用反射探测 {@link ResponseBodyEmitter} 的 complete 标志
 * （spring-webmvc 在 classpath 的 unnamed module，反射可访问）。
 */
class StreamingConversationServiceTest {

    @Nested
    @DisplayName("splitIntoChunks 切分逻辑（纯函数）")
    class SplitIntoChunks {

        @Test
        @DisplayName("按句号切分保留分隔符")
        void splitBySentence() {
            List<String> chunks = StreamingConversationService.splitIntoChunks(
                    "你好。我是客服。请问有什么可以帮你？");
            assertEquals(3, chunks.size());
            assertTrue(chunks.get(0).contains("你好"));
            assertTrue(chunks.get(2).contains("帮"));
        }

        @Test
        @DisplayName("换行也作为分隔符")
        void splitByNewline() {
            List<String> chunks = StreamingConversationService.splitIntoChunks("第一行\n第二行");
            assertEquals(2, chunks.size());
        }

        @Test
        @DisplayName("超长句按 MAX_CHUNK_CHARS 二次切分（防爆）")
        void longSentenceSplit() {
            StringBuilder longText = new StringBuilder();
            for (int i = 0; i < 200; i++) {
                longText.append("字");
            }
            longText.append("。");
            List<String> chunks = StreamingConversationService.splitIntoChunks(longText.toString());
            assertTrue(chunks.size() >= 3, "200 字应被切成至少 3 块（每块 ≤80）");
            for (String c : chunks) {
                assertTrue(c.length() <= 80, "每块不超过 80 字符，实际: " + c.length());
            }
        }

        @Test
        @DisplayName("空文本返回单个空块")
        void emptyText() {
            List<String> chunks = StreamingConversationService.splitIntoChunks("");
            assertEquals(1, chunks.size());
        }

        @Test
        @DisplayName("null 返回单个空块")
        void nullText() {
            List<String> chunks = StreamingConversationService.splitIntoChunks(null);
            assertEquals(1, chunks.size());
            assertEquals("", chunks.get(0));
        }
    }

    @Nested
    @DisplayName("高危前置护栏（B8 前置）")
    class HighRiskGuardrail {

        @Test
        @DisplayName("PET_CHAT 高危症状：不调 Provider，直接发固定兽医文案（T5）")
        void highRiskShortCircuits() throws Exception {
            AiConversationApplicationService svc = mock(AiConversationApplicationService.class);
            StreamingConversationService streaming = new StreamingConversationService(svc);

            // 高危症状文本（呕吐是 HighRiskSymptomDetector 的正则之一）
            AiMessageCreateRequest req = new AiMessageCreateRequest("我的狗一直在呕吐");
            SseEmitter emitter = streaming.streamMessage(100L, 1L, req);

            assertTrue(awaitEmitterComplete(emitter), "高危短路路径也应 complete emitter");
            // 关键断言：sendMessage 没被调用（前置护栏短路）
            verify(svc, org.mockito.Mockito.never()).sendMessage(anyLong(), anyLong(), any());
            // 验证高危检测器确实命中了
            assertTrue(HighRiskSymptomDetector.isHighRisk("我的狗一直在呕吐"));
        }

        @Test
        @DisplayName("非高危消息：正常调用 sendMessage")
        void normalMessageProceeds() {
            AiConversationApplicationService svc = mock(AiConversationApplicationService.class);
            when(svc.sendMessage(anyLong(), anyLong(), any())).thenReturn(
                    new AiMessageResponse(1L, 1L, "assistant", "营业时间是 9-21 点。", null));
            StreamingConversationService streaming = new StreamingConversationService(svc);

            AiMessageCreateRequest req = new AiMessageCreateRequest("几点开门");
            SseEmitter emitter = streaming.streamMessage(100L, 1L, req);

            assertNotNull(emitter);
            // 异步执行：用 timeout 等待后台线程调用 sendMessage
            verify(svc, org.mockito.Mockito.timeout(2_000)).sendMessage(100L, 1L, req);
        }
    }

    @Nested
    @DisplayName("异步生命周期（SseEmitter 规范用法）")
    class AsyncLifecycle {

        @Test
        @DisplayName("streamMessage 立即返回，生成在后台线程完成后 emitter 才 complete")
        void streamMessageReturnsBeforeGenerationCompletes() throws Exception {
            AiConversationApplicationService svc = mock(AiConversationApplicationService.class);
            CountDownLatch release = new CountDownLatch(1);
            when(svc.sendMessage(anyLong(), anyLong(), any())).thenAnswer(inv -> {
                release.await(5, TimeUnit.SECONDS);
                return new AiMessageResponse(1L, 1L, "assistant", "好。", null);
            });
            StreamingConversationService streaming = new StreamingConversationService(svc);

            long start = System.nanoTime();
            SseEmitter emitter = streaming.streamMessage(100L, 1L, new AiMessageCreateRequest("几点开门"));
            long returnMillis = (System.nanoTime() - start) / 1_000_000;

            // 回归守卫（2026-08-15 验收缺陷）：controller 线程不得阻塞等待生成完成。
            // 旧实现同步 send+complete 后才返回 emitter，Tomcat 关闭连接时不写 chunked
            // 终止块，Node/fetch 侧流永不结束，前端卡"正在思考"。
            assertTrue(returnMillis < 500, "streamMessage 应立即返回，实际耗时 " + returnMillis + "ms");
            assertFalse(isEmitterComplete(emitter), "返回时 emitter 不应已 complete（生成仍在后台进行）");

            release.countDown();
            assertTrue(awaitEmitterComplete(emitter), "生成完成后 emitter 应 complete");
        }
    }

    @Nested
    @DisplayName("Provider 失败降级")
    class FailureDegrades {

        @Test
        @DisplayName("sendMessage 抛异常：发送 error 事件，不向上游抛")
        void sendMessageThrowsSendsErrorEvent() {
            AiConversationApplicationService svc = mock(AiConversationApplicationService.class);
            when(svc.sendMessage(anyLong(), anyLong(), any()))
                    .thenThrow(new RuntimeException("Provider down"));
            StreamingConversationService streaming = new StreamingConversationService(svc);

            AiMessageCreateRequest req = new AiMessageCreateRequest("查询");
            // 不应抛异常（SSE 已开流，应内部捕获发 error 事件）
            SseEmitter emitter = assertDoesNotThrow(
                    () -> streaming.streamMessage(100L, 1L, req));
            assertNotNull(emitter);
        }
    }

    private static boolean isEmitterComplete(SseEmitter emitter) {
        try {
            Field field = ResponseBodyEmitter.class.getDeclaredField("complete");
            field.setAccessible(true);
            return (boolean) field.get(emitter);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法探测 SseEmitter.complete 标志", e);
        }
    }

    private static boolean awaitEmitterComplete(SseEmitter emitter) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (isEmitterComplete(emitter)) {
                return true;
            }
            Thread.sleep(20);
        }
        return isEmitterComplete(emitter);
    }
}
