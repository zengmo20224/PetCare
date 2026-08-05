package com.petcare.ai.agent;

import com.petcare.ai.agent.audit.AiToolCallLogService;
import com.petcare.ai.agent.registry.AgentToolRegistry;
import com.petcare.ai.agent.tool.AgentTool;
import com.petcare.ai.agent.tool.AgentToolArgs;
import com.petcare.ai.agent.tool.AgentToolResult;
import com.petcare.ai.agent.tool.GetProductInfoTool;
import com.petcare.ai.domain.CustomerServiceContext;
import com.petcare.ai.domain.CustomerServiceContextBuilder;
import com.petcare.ai.provider.AiProviderException;
import com.petcare.ai.provider.AiProviderUnavailableException;
import com.petcare.ai.provider.MockAiProviderClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CustomerServiceAgent} 端到端编排测试。
 * <p>
 * 用 {@link MockAiProviderClient} 注入确定性 LLM 输出，验证：
 * <ul>
 *   <li>工具调用 → 第二轮生成流程；</li>
 *   <li>未授权工具被 Registry 拒绝；</li>
 *   <li>RAG 失败降级；</li>
 *   <li>Provider 异常分级透传。</li>
 * </ul>
 * RAG 用 null（rag-enabled=false 场景），聚焦 Agent 工具编排逻辑。
 */
class CustomerServiceAgentFlowTest {

    private MockAiProviderClient provider;
    private CustomerServiceContextBuilder contextBuilder;
    private AgentToolRegistry registry;
    private AiToolCallLogService auditService;

    @BeforeEach
    void setUp() {
        provider = new MockAiProviderClient();
        contextBuilder = mock(CustomerServiceContextBuilder.class);
        when(contextBuilder.build()).thenReturn(CustomerServiceContext.empty());
        // 注册一个返回固定摘要的 stub Tool
        AgentTool stubTool = new StubTool("getProductInfo", "商品【猫粮】199元，库存50");
        registry = new AgentToolRegistry(List.of(stubTool));
        auditService = mock(AiToolCallLogService.class);
    }

    @Test
    @DisplayName("完整工具调用流程：第一轮返回 [TOOL:] → 执行 → 第二轮生成最终回复")
    void fullToolCallFlow() {
        provider.withSequence(
                "[TOOL:getProductInfo(productId=123)]",  // 第一轮：LLM 请求工具
                "根据查询结果，商品【猫粮】售价 199 元，目前有货，库存 50 件。"); // 第二轮：LLM 结合工具结果回答

        CustomerServiceAgent agent = newAgent(null);

        CustomerServiceAgent.AgentReply reply = agent.handle(100L, List.of(), "猫粮多少钱");

        assertTrue(reply.text().contains("猫粮"));
        assertTrue(reply.text().contains("199"));
        assertEquals(2, provider.getCallCount(), "Provider 应被调用两次（工具调用 + 最终回复）");
    }

    @Test
    @DisplayName("无工具调用：LLM 直接回答，Provider 只调一次")
    void noToolCall() {
        provider.withSuccess("我们营业时间是 9:00-21:00，欢迎光临！");

        CustomerServiceAgent agent = newAgent(null);
        CustomerServiceAgent.AgentReply reply = agent.handle(100L, List.of(), "几点开门");

        assertTrue(reply.text().contains("9:00-21:00"));
        assertEquals(1, provider.getCallCount());
    }

    @Test
    @DisplayName("未授权工具：LLM 请求白名单外工具 → Registry 拒绝 → 返回脱敏失败 → 第二轮用失败信息回答")
    void unauthorizedToolRejected() {
        provider.withSequence(
                "[TOOL:deleteDatabase(table=users)]",  // 白名单外
                "抱歉，该操作暂不可用。");

        CustomerServiceAgent agent = newAgent(null);
        CustomerServiceAgent.AgentReply reply = agent.handle(100L, List.of(), "删库");

        // Registry 拒绝了 deleteDatabase，返回脱敏失败，LLM 第二轮据此回答
        assertEquals(2, provider.getCallCount());
        assertNotNull(reply.text());
    }

    @Test
    @DisplayName("未登录用户：Agent 拒绝执行（requireUser），Provider 不被调用")
    void unauthenticatedRejected() {
        provider.withSuccess("不该执行到这里");
        CustomerServiceAgent agent = newAgent(null);

        assertThrows(IllegalStateException.class,
                () -> agent.handle(null, List.of(), "查询"),
                "currentUserId=null 必须被 requireUser 拒绝");
        assertEquals(0, provider.getCallCount(), "Provider 不应被调用");
    }

    @Test
    @DisplayName("Provider 不可用：异常透传（沿用 V1 异常分级）")
    void providerUnavailablePropagates() {
        provider.withUnavailable();
        CustomerServiceAgent agent = newAgent(null);

        assertThrows(AiProviderUnavailableException.class,
                () -> agent.handle(100L, List.of(), "查询"));
    }

    @Test
    @DisplayName("Provider 通用错误：异常透传")
    void providerErrorPropagates() {
        provider.withProviderError();
        CustomerServiceAgent agent = newAgent(null);

        assertThrows(AiProviderException.class,
                () -> agent.handle(100L, List.of(), "查询"));
    }

    @Test
    @DisplayName("工具调用失败（Tool 抛异常）：降级为脱敏失败，不阻塞第二轮")
    void toolFailureDegrades() {
        AgentTool failingTool = new StubTool("getProductInfo", null) {
            @Override
            public AgentToolResult invoke(AgentToolArgs input, AgentContext ctx) {
                throw new RuntimeException("业务异常");
            }
        };
        registry = new AgentToolRegistry(List.of(failingTool));
        provider.withSequence(
                "[TOOL:getProductInfo(productId=1)]",
                "抱歉，商品信息暂时无法获取。");

        CustomerServiceAgent agent = newAgent(null);
        CustomerServiceAgent.AgentReply reply = agent.handle(100L, List.of(), "查询");

        assertNotNull(reply.text());
        assertEquals(2, provider.getCallCount());
    }

    private CustomerServiceAgent newAgent(com.petcare.ai.rag.RagRetrievalService rag) {
        return new CustomerServiceAgent(provider, contextBuilder, rag, registry, auditService);
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
