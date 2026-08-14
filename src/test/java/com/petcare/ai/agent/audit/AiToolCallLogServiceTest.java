package com.petcare.ai.agent.audit;

import com.petcare.ai.agent.AgentContext;
import com.petcare.ai.agent.AgentType;
import com.petcare.ai.mapper.AiToolCallLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A4 修复：审计字段 admin_id / user_id 按调用方身份正确分流。
 */
class AiToolCallLogServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<AiToolCallLogMapper> provider = mock(ObjectProvider.class);
    private final AiToolCallLogMapper mapper = mock(AiToolCallLogMapper.class);

    @Test
    @DisplayName("用户端 Agent：userId 正确落 user_id，admin_id 为空")
    void userAgent_writesUserIdOnly() {
        when(provider.getIfAvailable()).thenReturn(mapper);
        AiToolCallLogService service = new AiToolCallLogService(provider);

        AgentContext ctx = new AgentContext(88L, AgentType.CUSTOMER_SERVICE, "conv-1");
        service.logCall(ctx, "getProductInfo", null,
                com.petcare.ai.agent.tool.AgentToolResult.ok("商品信息"), null, 12L);

        ArgumentCaptor<AiToolCallLog> captor = ArgumentCaptor.forClass(AiToolCallLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals(88L, captor.getValue().getUserId());
        assertNull(captor.getValue().getAdminId());
    }

    @Test
    @DisplayName("管理端 Agent（forAdmin）：adminId 正确落 admin_id，user_id 为空（不再错位写入）")
    void adminAgent_writesAdminIdOnly() {
        when(provider.getIfAvailable()).thenReturn(mapper);
        AiToolCallLogService service = new AiToolCallLogService(provider);

        // admin 表 ID=5 与 user 表 ID=5 是两个不同的人——必须分流
        AgentContext ctx = AgentContext.forAdmin(5L, AgentType.ANALYSIS);
        service.logCall(ctx, "getSalesTrend", null,
                com.petcare.ai.agent.tool.AgentToolResult.ok("销售趋势"), null, 30L);

        ArgumentCaptor<AiToolCallLog> captor = ArgumentCaptor.forClass(AiToolCallLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals(5L, captor.getValue().getAdminId());
        assertNull(captor.getValue().getUserId(),
                "管理端 Agent 的审计不得把 admin ID 写进 user_id（A4）");
        assertEquals("ANALYSIS", captor.getValue().getAgentType());
    }

    @Test
    @DisplayName("Mapper 不可用时静默跳过，不抛异常（降级哲学）")
    void mapperUnavailable_skipsSilently() {
        when(provider.getIfAvailable()).thenReturn(null);
        AiToolCallLogService service = new AiToolCallLogService(provider);

        service.logCall(new AgentContext(1L, AgentType.CUSTOMER_SERVICE, null),
                "getProductInfo", null, null, null, 1L);
        verify(mapper, org.mockito.Mockito.never()).insert(any(AiToolCallLog.class));
    }
}
