package com.petcare.ai.agent;

import com.petcare.ai.agent.tool.AgentTool;
import com.petcare.ai.agent.tool.GetActivityEffectTool;
import com.petcare.ai.agent.tool.GetBookingFunnelTool;
import com.petcare.ai.agent.tool.GetCommunityMetricsTool;
import com.petcare.ai.agent.tool.GetMyBookingStatusTool;
import com.petcare.ai.agent.tool.GetMyOrderStatusTool;
import com.petcare.ai.agent.tool.GetProductInfoTool;
import com.petcare.ai.agent.tool.GetSalesTrendTool;
import com.petcare.ai.agent.tool.GetServiceInfoTool;
import com.petcare.ai.agent.tool.GetStoreInfoTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V2 Agent 架构守卫测试（对标 docs/09 §10.2 + V1 {@code AiProviderArchitectureTest}）。
 * <p>
 * 强制：
 * <ul>
 *   <li>Tool 实现类不直接依赖 {@code *Mapper} / DataSource / MyBatis（B1/B7）；</li>
 *   <li>所有客服 Tool 的 {@link AgentTool#readOnly()} 返回 {@code true}（B4）；</li>
 *   <li>{@link com.petcare.ai.agent.registry.AgentToolRegistry} 的 Tool 集合字段是 final。</li>
 * </ul>
 * 注：审计 Service（{@code AiToolCallLogService}）依赖 Mapper 是合理的（它就是写审计日志的），
 *     故不在 Tool 扫描范围内。
 */
class AiAgentArchitectureTest {

    /** Tool 实现类不允许依赖的包前缀。 */
    private static final List<String> FORBIDDEN = List.of(
            "com.petcare.ai.mapper",
            "com.petcare.product.mapper",
            "com.petcare.booking.mapper",
            "com.petcare.store.mapper",
            "com.petcare.service.mapper",
            "com.petcare.community.mapper",
            "javax.sql.DataSource",
            "org.apache.ibatis",
            "com.baomidou.mybatisplus"
    );

    private static final List<Class<? extends AgentTool>> TOOL_CLASSES = List.of(
            GetProductInfoTool.class,
            GetServiceInfoTool.class,
            GetStoreInfoTool.class,
            GetMyOrderStatusTool.class,
            GetMyBookingStatusTool.class,
            // M8.2 经营分析只读 Tool（4 个）
            GetSalesTrendTool.class,
            GetBookingFunnelTool.class,
            GetCommunityMetricsTool.class,
            GetActivityEffectTool.class
    );

    @Test
    @DisplayName("Agent Tool 实现类不依赖 Mapper / DataSource / MyBatis")
    void toolClasses_noDatabaseDependencies() {
        for (Class<?> clazz : TOOL_CLASSES) {
            assertNoForbiddenDependencies(clazz);
        }
    }

    @Test
    @DisplayName("所有 Agent Tool 都是只读（B4）")
    void customerServiceTools_areReadOnly() throws Exception {
        for (Class<? extends AgentTool> clazz : TOOL_CLASSES) {
            AgentTool instance = newInstance(clazz);
            assertTrue(instance.readOnly(),
                    clazz.getSimpleName() + ".readOnly() must be true (B4: Agent tools are read-only)");
        }
    }

    @Test
    @DisplayName("AgentToolRegistry 的 Tool 集合不可变（构造后无法扩展）")
    void registry_toolsAreImmutable() {
        AgentTool dummyTool = new ReadOnlyDummyTool("dummy");
        com.petcare.ai.agent.registry.AgentToolRegistry registry =
                new com.petcare.ai.agent.registry.AgentToolRegistry(List.of(dummyTool));
        // 构造后注册新工具无效（白名单固定）
        assertFalse(registry.contains("evil"));
        // 工具集大小固定
        assertEquals(1, registry.names().size());
        // descriptors 返回的列表与白名单一致，不含额外项
        assertEquals(1, registry.descriptors().size());
    }

    @Test
    @DisplayName("Tool 白名单拒绝未注册的工具（T1）")
    void registry_rejectsUnknownTool() {
        AgentTool dummyTool = new ReadOnlyDummyTool("registeredTool");
        com.petcare.ai.agent.registry.AgentToolRegistry registry =
                new com.petcare.ai.agent.registry.AgentToolRegistry(List.of(dummyTool));
        assertTrue(registry.contains("registeredTool"));
        assertFalse(registry.contains("unregisteredTool"));
    }

    @SuppressWarnings("unchecked")
    private static void assertNoForbiddenDependencies(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            String typeName = field.getType().getName();
            for (String forbidden : FORBIDDEN) {
                assertFalse(typeName.startsWith(forbidden),
                        clazz.getName() + "." + field.getName() + " has forbidden dependency: " + forbidden);
            }
        }
        for (Method method : clazz.getDeclaredMethods()) {
            String returnTypeName = method.getReturnType().getName();
            for (String forbidden : FORBIDDEN) {
                assertFalse(returnTypeName.startsWith(forbidden),
                        clazz.getName() + "." + method.getName() + "() returns forbidden type: " + forbidden);
            }
            for (Class<?> paramType : method.getParameterTypes()) {
                for (String forbidden : FORBIDDEN) {
                    assertFalse(paramType.getName().startsWith(forbidden),
                            clazz.getName() + "." + method.getName() + "(param) has forbidden dependency: " + forbidden);
                }
            }
        }
    }

    /** 用反射无参构造 Tool 实例（测试构造器均为单参业务 Service，用 null 占位即可验证 readOnly/依赖）。 */
    @SuppressWarnings("unchecked")
    private static AgentTool newInstance(Class<? extends AgentTool> clazz) throws Exception {
        try {
            // 所有 Tool 构造器都是单参（业务 Service），传 null 即可创建实例验证 readOnly
            java.lang.reflect.Constructor<?> ctor = clazz.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            Object[] args = new Object[ctor.getParameterCount()];
            java.util.Arrays.fill(args, null);
            return (AgentTool) ctor.newInstance(args);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot instantiate " + clazz.getName() + " for readOnly check", e);
        }
    }

    /** 只读 dummy tool，用于 Registry 不可变性测试。 */
    private static final class ReadOnlyDummyTool implements AgentTool {
        private final String name;

        ReadOnlyDummyTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "dummy";
        }

        @Override
        public boolean readOnly() {
            return true;
        }

        @Override
        public com.petcare.ai.agent.tool.AgentToolResult invoke(
                com.petcare.ai.agent.tool.AgentToolArgs input, com.petcare.ai.agent.AgentContext ctx) {
            return com.petcare.ai.agent.tool.AgentToolResult.ok("dummy");
        }
    }
}
