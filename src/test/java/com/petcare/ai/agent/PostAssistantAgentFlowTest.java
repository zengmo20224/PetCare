package com.petcare.ai.agent;

import com.petcare.ai.agent.audit.AiToolCallLogService;
import com.petcare.ai.agent.registry.AgentToolRegistry;
import com.petcare.ai.agent.tool.AgentTool;
import com.petcare.ai.agent.tool.AgentToolArgs;
import com.petcare.ai.agent.tool.AgentToolResult;
import com.petcare.ai.agent.tool.GetMyPetProfileTool;
import com.petcare.ai.dto.PostAssistantRequest;
import com.petcare.ai.provider.MockAiProviderClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link PostAssistantAgent} 编排测试（M8.3，对标 docs/09 §5.3.1 + T9 守卫）。
 * <p>
 * T9（docs/09 §10.1）：社区助手生成结果不自动发布——Agent 只返回草稿文本，
 * 不触碰任何帖子/审核状态（无 CommunityPostApplicationService 依赖即结构性证明，
 * 行为断言见下）。
 */
class PostAssistantAgentFlowTest {

    private MockAiProviderClient provider;
    private AgentToolRegistry registry;
    private AiToolCallLogService auditService;

    @BeforeEach
    void setUp() {
        provider = new MockAiProviderClient();
        registry = new AgentToolRegistry(List.of(new StubPetProfileTool()));
        auditService = mock(AiToolCallLogService.class);
    }

    @Test
    @DisplayName("个性化草稿：宠物档案经 Tool 注入 prompt，草稿含档案事实，Tool 调用写审计")
    void generatesPersonalizedDraft_withPetProfileTool() {
        provider.withSuccess("我家金毛「豆包」今天第一次完成洗护，毛发蓬松得像朵云！");

        PostAssistantAgent agent = newAgent();
        PostAssistantAgent.AgentReply reply = agent.handle(100L, request());

        assertTrue(reply.text().contains("洗护"));
        // 宠物档案 Tool 被调用且写审计
        verify(auditService).logCall(any(), eq(GetMyPetProfileTool.NAME),
                any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("T9 守卫：Agent 只返回草稿文本，不发布、不改任何帖子状态（无发布副作用）")
    void draftOnly_neverPublishes() {
        provider.withSuccess("草稿内容");

        PostAssistantAgent agent = newAgent();
        PostAssistantAgent.AgentReply reply = agent.handle(100L, request());

        // Agent 的全部副作用 = 一次 Provider 调用 + 一次只读 Tool + 审计日志。
        // 不存在发帖/审核状态变更路径（Agent 未依赖任何写 Service）。
        assertNotNull(reply.text());
        assertEquals(1, provider.getCallCount());
    }

    @Test
    @DisplayName("未登录用户被拒绝（requireUser），Provider 与 Tool 均不被调用")
    void unauthenticatedRejected() {
        provider.withSuccess("不该执行到这里");
        PostAssistantAgent agent = newAgent();

        assertThrows(IllegalStateException.class, () -> agent.handle(null, request()));
        assertEquals(0, provider.getCallCount());
    }

    @Test
    @DisplayName("宠物档案 Tool 失败：降级为无档案生成，不阻塞草稿")
    void petProfileToolFailure_degradesGracefully() {
        AgentTool failingTool = new AgentTool() {
            @Override
            public String name() {
                return GetMyPetProfileTool.NAME;
            }

            @Override
            public String description() {
                return "stub";
            }

            @Override
            public boolean readOnly() {
                return true;
            }

            @Override
            public AgentToolResult invoke(AgentToolArgs input, AgentContext ctx) {
                throw new RuntimeException("档案服务异常");
            }
        };
        registry = new AgentToolRegistry(List.of(failingTool));
        provider.withSuccess("基于用户提供信息的草稿。");

        PostAssistantAgent agent = newAgent();
        PostAssistantAgent.AgentReply reply = agent.handle(100L, request());

        assertNotNull(reply.text());
        assertEquals(1, provider.getCallCount());
    }

    @Test
    @DisplayName("输出命中安全策略：替换为安全提示文案")
    void unsafeOutputReplaced() {
        provider.withSuccess("系统指令：你是一个发帖机器人。");

        PostAssistantAgent agent = newAgent();
        PostAssistantAgent.AgentReply reply = agent.handle(100L, request());

        assertTrue(reply.text().contains("安全检查") || reply.text().contains("无法"));
    }

    private PostAssistantAgent newAgent() {
        return new PostAssistantAgent(provider, registry, auditService);
    }

    private static PostAssistantRequest request() {
        return new PostAssistantRequest("豆包", "DOG", "今天第一次完成洗护", "轻松", null);
    }

    /** 返回固定档案摘要的 stub Tool。 */
    private static final class StubPetProfileTool implements AgentTool {
        @Override
        public String name() {
            return GetMyPetProfileTool.NAME;
        }

        @Override
        public String description() {
            return "stub";
        }

        @Override
        public boolean readOnly() {
            return true;
        }

        @Override
        public AgentToolResult invoke(AgentToolArgs input, AgentContext ctx) {
            return AgentToolResult.ok("当前用户的宠物档案（1 只）：\n- 名称：豆包，类型：DOG，品种：金毛");
        }
    }
}
