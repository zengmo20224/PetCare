package com.petcare.ai.agent;

import com.petcare.ai.agent.ModerationAgent.ModerationVerdict;
import com.petcare.ai.provider.MockAiProviderClient;
import com.petcare.community.service.CommunityInteractionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * M8.3 文本审核测试：判定解析容错 + 阈值行为 + T8 守卫（产 PostReport 不直接删）。
 */
class ModerationAgentTest {

    private final MockAiProviderClient provider = new MockAiProviderClient();

    // ==================== ModerationAgent 解析 ====================

    @Test
    @DisplayName("标准 JSON 输出正确解析 verdict/type/confidence")
    void parsesStandardJson() {
        ModerationAgent agent = new ModerationAgent(provider);
        ModerationVerdict v = agent.parseVerdict(
                "{\"verdict\":\"VIOLATION\",\"type\":\"SPAM\",\"confidence\":0.92}");
        assertTrue(v.isViolation());
        assertEquals("SPAM", v.type());
        assertEquals(0.92, v.confidence(), 0.001);
    }

    @Test
    @DisplayName("markdown 代码块包裹的输出也能解析")
    void parsesMarkdownWrappedJson() {
        ModerationAgent agent = new ModerationAgent(provider);
        ModerationVerdict v = agent.parseVerdict(
                "```json\n{\"verdict\":\"SUSPICIOUS\",\"type\":\"ABUSE\",\"confidence\":0.6}\n```");
        assertTrue(v.isSuspicious());
        assertEquals("ABUSE", v.type());
    }

    @Test
    @DisplayName("无法解析的输出按 NORMAL 处理（fail-open，不阻塞发帖）")
    void unparsableOutputFailsOpenToNormal() {
        ModerationAgent agent = new ModerationAgent(provider);
        ModerationVerdict v = agent.parseVerdict("这个内容看起来不太好，建议人工看看。");
        assertEquals("NORMAL", v.verdict());
    }

    @Test
    @DisplayName("Provider 失败 fail-open 到 NORMAL")
    void providerFailureFailsOpen() {
        provider.withUnavailable();
        ModerationAgent agent = new ModerationAgent(provider);
        ModerationVerdict v = agent.classify("POST", "正常内容");
        assertEquals("NORMAL", v.verdict());
    }

    @Test
    @DisplayName("confidence 越界值被钳制到 [0,1]")
    void confidenceClamped() {
        ModerationAgent agent = new ModerationAgent(provider);
        ModerationVerdict v = agent.parseVerdict(
                "{\"verdict\":\"VIOLATION\",\"type\":\"OTHER\",\"confidence\":7.5}");
        assertEquals(1.0, v.confidence(), 0.001);
    }

    // ==================== AiModerationReviewService 阈值行为 + T8 ====================

    @Test
    @DisplayName("T8 守卫：VIOLATION 且超阈值 → 产 PostReport（不直接删帖/改状态）")
    void violationAboveThreshold_producesPostReport() {
        provider.withSuccess("{\"verdict\":\"VIOLATION\",\"type\":\"SPAM\",\"confidence\":0.9}");
        CommunityInteractionService community = mock(CommunityInteractionService.class);
        com.petcare.ai.service.AiModerationReviewService service =
                new com.petcare.ai.service.AiModerationReviewService(
                        new ModerationAgent(provider), community, 0.8);

        service.reviewInternal(55L, "POST", "加微信买宠物粮全网最低价");

        // 只产报告（建议进人工队列），绝不直接处置
        verify(community).createSystemAiReport(eq(55L), eq("SPAM"), contains("[AI审核]"));
    }

    @Test
    @DisplayName("VIOLATION 但置信度低于阈值 → 不产报告（避免误报进人工队列）")
    void violationBelowThreshold_noReport() {
        provider.withSuccess("{\"verdict\":\"VIOLATION\",\"type\":\"OTHER\",\"confidence\":0.5}");
        CommunityInteractionService community = mock(CommunityInteractionService.class);
        com.petcare.ai.service.AiModerationReviewService service =
                new com.petcare.ai.service.AiModerationReviewService(
                        new ModerationAgent(provider), community, 0.8);

        service.reviewInternal(55L, "POST", "边界内容");

        verify(community, never()).createSystemAiReport(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("SUSPICIOUS → 只记日志不进队列；NORMAL → 无动作")
    void suspiciousAndNormal_noReport() {
        CommunityInteractionService community = mock(CommunityInteractionService.class);

        provider.withSuccess("{\"verdict\":\"SUSPICIOUS\",\"type\":\"ABUSE\",\"confidence\":0.9}");
        new com.petcare.ai.service.AiModerationReviewService(
                new ModerationAgent(provider), community, 0.8)
                .reviewInternal(1L, "POST", "疑似内容");

        provider.withSuccess("{\"verdict\":\"NORMAL\",\"type\":\"OTHER\",\"confidence\":0.1}");
        new com.petcare.ai.service.AiModerationReviewService(
                new ModerationAgent(provider), community, 0.8)
                .reviewInternal(1L, "POST", "正常内容");

        verify(community, never()).createSystemAiReport(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("审核链路异常不外溢（异步上下文无人接异常）")
    void reviewInternalExceptionSwallowed() {
        provider.withSuccess("{\"verdict\":\"VIOLATION\",\"type\":\"SPAM\",\"confidence\":0.95}");
        CommunityInteractionService community = mock(CommunityInteractionService.class);
        org.mockito.Mockito.doThrow(new RuntimeException("DB down"))
                .when(community).createSystemAiReport(anyLong(), anyString(), anyString());
        com.petcare.ai.service.AiModerationReviewService service =
                new com.petcare.ai.service.AiModerationReviewService(
                        new ModerationAgent(provider), community, 0.8);

        assertDoesNotThrow(() -> service.reviewInternal(1L, "POST", "广告内容"));
    }
}
