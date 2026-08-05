package com.petcare.ai.service;

import com.petcare.ai.domain.HighRiskSymptomDetector;
import com.petcare.ai.dto.AiMessageCreateRequest;
import com.petcare.ai.dto.AiMessageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StreamingConversationService} 单测。
 * <p>
 * 验证：句切分逻辑（纯函数）+ 高危前置护栏 + Provider 失败降级。
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
        void highRiskShortCircuits() {
            AiConversationApplicationService svc = mock(AiConversationApplicationService.class);
            StreamingConversationService streaming = new StreamingConversationService(svc);

            // 高危症状文本（呕吐是 HighRiskSymptomDetector 的正则之一）
            AiMessageCreateRequest req = new AiMessageCreateRequest("我的狗一直在呕吐");
            SseEmitter emitter = streaming.streamMessage(100L, 1L, req);

            assertNotNull(emitter);
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
            verify(svc).sendMessage(100L, 1L, req);
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
}
