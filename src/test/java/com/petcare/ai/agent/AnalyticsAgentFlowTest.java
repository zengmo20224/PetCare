package com.petcare.ai.agent;

import com.petcare.ai.agent.audit.AiToolCallLogService;
import com.petcare.ai.agent.registry.AgentToolRegistry;
import com.petcare.ai.agent.tool.AgentTool;
import com.petcare.ai.agent.tool.AgentToolArgs;
import com.petcare.ai.agent.tool.AgentToolResult;
import com.petcare.ai.dto.AiAnalysisCreateRequest;
import com.petcare.ai.provider.AiProviderException;
import com.petcare.ai.provider.AiProviderUnavailableException;
import com.petcare.ai.provider.MockAiProviderClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link AnalyticsAgent} 端到端编排测试（对标 {@code CustomerServiceAgentFlowTest}）。
 * <p>
 * 用 {@link MockAiProviderClient} 注入确定性 LLM 输出，验证：
 * <ul>
 *   <li>完整工具下钻流程（第一轮返回 [TOOL:] → 执行 → 第二轮生成报告）；</li>
 *   <li>无工具调用直接生成报告；</li>
 *   <li>白名单外工具被 Registry 拒绝；</li>
 *   <li>admin 登录态校验；</li>
 *   <li>Provider 异常分级透传；</li>
 *   <li>每次 Tool 调用都写审计日志。</li>
 * </ul>
 */
class AnalyticsAgentFlowTest {

    private static final AiAnalysisCreateRequest REQUEST = new AiAnalysisCreateRequest(
            "SALES", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

    private MockAiProviderClient provider;
    private AgentToolRegistry registry;
    private AiToolCallLogService auditService;

    @BeforeEach
    void setUp() {
        provider = new MockAiProviderClient();
        AgentTool stubTool = new StubTool("getSalesTrend",
                "销售趋势：总订单 120 单，总营收 15600 元，客单价 130 元，热销商品【猫粮】销量 40。");
        registry = new AgentToolRegistry(List.of(stubTool));
        auditService = mock(AiToolCallLogService.class);
    }

    @Test
    @DisplayName("完整下钻流程：第一轮返回 [TOOL:] → 执行 → 第二轮生成最终报告")
    void fullDrillDownFlow() {
        provider.withSequence(
                "[TOOL:getSalesTrend(startDate=2026-07-01,endDate=2026-07-31)]",
                "## 销售分析报告\n\n7 月销售 120 单，总营收 15600 元，客单价 130 元。\n\n### 建议\n保持当前 SKU 策略。");

        AnalyticsAgent agent = newAgent();

        AnalyticsAgent.AgentReply reply = agent.handle(1L, REQUEST, "{\"totalOrders\":120}");

        assertTrue(reply.text().contains("销售分析报告"));
        assertTrue(reply.text().contains("120"));
        assertEquals(2, provider.getCallCount(), "Provider 应被调用两次（工具调用 + 最终报告）");
        verify(auditService).logCall(any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("无工具调用：概览数据已足够，LLM 直接生成报告，Provider 只调一次")
    void noToolCallWhenOverviewSufficient() {
        provider.withSuccess("## 销售概览\n7 月营收稳定，无异常。");

        AnalyticsAgent agent = newAgent();
        AnalyticsAgent.AgentReply reply = agent.handle(1L, REQUEST, "{\"totalOrders\":120}");

        assertTrue(reply.text().contains("销售概览"));
        assertEquals(1, provider.getCallCount());
    }

    @Test
    @DisplayName("白名单外工具：Registry 拒绝 → 返回脱敏失败 → 第二轮用失败信息生成报告")
    void unauthorizedToolRejected() {
        provider.withSequence(
                "[TOOL:deleteDatabase(table=orders)]",
                "## 报告\n基于概览数据，销售平稳。");

        AnalyticsAgent agent = newAgent();
        AnalyticsAgent.AgentReply reply = agent.handle(1L, REQUEST, "{}");

        assertEquals(2, provider.getCallCount());
        assertNotNull(reply.text());
    }

    @Test
    @DisplayName("未登录 admin：Agent 拒绝执行（requireUser），Provider 不被调用")
    void unauthenticatedRejected() {
        provider.withSuccess("不该执行到这里");
        AnalyticsAgent agent = newAgent();

        assertThrows(IllegalStateException.class,
                () -> agent.handle(null, REQUEST, "{}"),
                "adminId=null 必须被 requireUser 拒绝");
        assertEquals(0, provider.getCallCount(), "Provider 不应被调用");
    }

    @Test
    @DisplayName("Provider 不可用：异常透传（沿用 V1 异常分级）")
    void providerUnavailablePropagates() {
        provider.withUnavailable();
        AnalyticsAgent agent = newAgent();

        assertThrows(AiProviderUnavailableException.class,
                () -> agent.handle(1L, REQUEST, "{}"));
    }

    @Test
    @DisplayName("Provider 通用错误：异常透传")
    void providerErrorPropagates() {
        provider.withProviderError();
        AnalyticsAgent agent = newAgent();

        assertThrows(AiProviderException.class,
                () -> agent.handle(1L, REQUEST, "{}"));
    }

    @Test
    @DisplayName("Tool 抛异常：降级为脱敏失败，不阻塞第二轮")
    void toolFailureDegrades() {
        AgentTool failingTool = new StubTool("getSalesTrend", null) {
            @Override
            public AgentToolResult invoke(AgentToolArgs input, AgentContext ctx) {
                throw new RuntimeException("聚合器异常");
            }
        };
        registry = new AgentToolRegistry(List.of(failingTool));
        provider.withSequence(
                "[TOOL:getSalesTrend()]",
                "## 报告\n概览数据已足够，无需下钻。");

        AnalyticsAgent agent = newAgent();
        AnalyticsAgent.AgentReply reply = agent.handle(1L, REQUEST, "{}");

        assertNotNull(reply.text());
        assertEquals(2, provider.getCallCount());
    }

    private AnalyticsAgent newAgent() {
        return new AnalyticsAgent(provider, registry, auditService);
    }

    /** 固定返回的 stub Tool。summary 为 null 时模拟失败。 */
    private static class StubTool implements AgentTool {
        private final String name;
        private final String summary;

        StubTool(String name, String summary) {
            this.name = name;
            this.summary = summary;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "stub for test";
        }

        @Override
        public boolean readOnly() {
            return true;
        }

        @Override
        public AgentToolResult invoke(AgentToolArgs input, AgentContext ctx) {
            return summary != null ? AgentToolResult.ok(summary) : AgentToolResult.fail("stub failure");
        }
    }
}
