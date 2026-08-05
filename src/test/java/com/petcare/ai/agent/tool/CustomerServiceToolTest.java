package com.petcare.ai.agent.tool;

import com.petcare.ai.agent.AgentContext;
import com.petcare.ai.agent.AgentType;
import com.petcare.ai.agent.registry.ParsedToolCall;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.product.dto.ProductDetailResponse;
import com.petcare.product.dto.ProductOrderDetailResponse;
import com.petcare.product.service.ProductCatalogApplicationService;
import com.petcare.product.service.ProductOrderApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客服只读 Tool 单测（对标 docs/09 §10 T3：越权校验）。
 * <p>
 * 重点验证：用户身份 Tool（getMyOrderStatus / getMyBookingStatus）的越权防护——
 * 业务 Service 抛 FORBIDDEN/NOT_FOUND 时，Tool 返回脱敏失败，不向 LLM 泄露订单存在性。
 */
class CustomerServiceToolTest {

    @Nested
    @DisplayName("GetProductInfoTool")
    class GetProductInfo {

        @Test
        @DisplayName("正常查询返回脱敏摘要")
        void normalQuery() {
            ProductCatalogApplicationService svc = Mockito.mock(ProductCatalogApplicationService.class);
            when(svc.getProductDetail(123L)).thenReturn(new ProductDetailResponse(
                    123L, 1L, "日用品", "猫粮 5kg", null,
                    new BigDecimal("199.00"), 50, 100, "优质猫粮", 0, null, null));
            GetProductInfoTool tool = new GetProductInfoTool(svc);

            AgentToolArgs args = parsedArgs("productId", "123");
            AgentToolResult result = tool.invoke(args, userCtx());

            assertTrue(result.success());
            assertTrue(result.summary().contains("猫粮 5kg"));
            assertTrue(result.summary().contains("199.00"));
            assertTrue(result.summary().contains("库存 50"));
        }

        @Test
        @DisplayName("商品不存在返回脱敏失败")
        void notFound() {
            ProductCatalogApplicationService svc = Mockito.mock(ProductCatalogApplicationService.class);
            when(svc.getProductDetail(999L)).thenThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "not found"));
            GetProductInfoTool tool = new GetProductInfoTool(svc);

            AgentToolResult result = tool.invoke(parsedArgs("productId", "999"), userCtx());

            assertFalse(result.success());
            assertEquals("未找到该商品", result.errorMessage());
        }

        @Test
        @DisplayName("缺 productId 参数返回失败")
        void missingArg() {
            ProductCatalogApplicationService svc = Mockito.mock(ProductCatalogApplicationService.class);
            GetProductInfoTool tool = new GetProductInfoTool(svc);

            AgentToolResult result = tool.invoke(parsedArgs(), userCtx());

            assertFalse(result.success());
            assertTrue(result.errorMessage().contains("商品 ID"));
        }
    }

    @Nested
    @DisplayName("GetMyOrderStatusTool 越权防护（T3）")
    class GetMyOrderStatusAuthorization {

        @Test
        @DisplayName("查询自己的订单成功")
        void ownOrder() {
            ProductOrderApplicationService svc = Mockito.mock(ProductOrderApplicationService.class);
            when(svc.getOrderDetail(eq(100L), eq(555L))).thenReturn(buildOrderDetail(555L, 100L));
            GetMyOrderStatusTool tool = new GetMyOrderStatusTool(svc);

            AgentToolResult result = tool.invoke(
                    parsedArgs("orderId", "555"),
                    new AgentContext(100L, AgentType.CUSTOMER_SERVICE, null));

            assertTrue(result.success());
            assertTrue(result.summary().contains("ORD-555"));
            verify(svc).getOrderDetail(100L, 555L); // 确认以当前用户身份查询
        }

        @Test
        @DisplayName("★ 用户 A 查用户 B 订单 → 业务层抛 FORBIDDEN → 返回脱敏失败（T3）")
        void crossUserQueryRejected() {
            ProductOrderApplicationService svc = Mockito.mock(ProductOrderApplicationService.class);
            // 模拟越权：用户 100 查订单 666（属于用户 200），业务层抛 FORBIDDEN
            when(svc.getOrderDetail(eq(100L), eq(666L)))
                    .thenThrow(new BusinessException(ErrorCode.PRODUCT_ORDER_FORBIDDEN, "无权查看"));

            GetMyOrderStatusTool tool = new GetMyOrderStatusTool(svc);
            AgentToolResult result = tool.invoke(
                    parsedArgs("orderId", "666"),
                    new AgentContext(100L, AgentType.CUSTOMER_SERVICE, null));

            // 关键断言：不泄露订单存在性，返回通用"未找到"
            assertFalse(result.success());
            assertEquals("未找到相关订单", result.errorMessage());
            // 确认 Tool 用了当前用户 ID 做归属校验，没有绕过
            verify(svc).getOrderDetail(100L, 666L);
        }

        @Test
        @DisplayName("订单不存在也返回同样的脱敏失败（不区分 403/404）")
        void notFoundSameAsForbidden() {
            ProductOrderApplicationService svc = Mockito.mock(ProductOrderApplicationService.class);
            when(svc.getOrderDetail(anyLong(), anyLong()))
                    .thenThrow(new BusinessException(ErrorCode.PRODUCT_ORDER_NOT_FOUND, "not found"));

            GetMyOrderStatusTool tool = new GetMyOrderStatusTool(svc);
            AgentToolResult result = tool.invoke(parsedArgs("orderId", "1"), userCtx());

            assertFalse(result.success());
            assertEquals("未找到相关订单", result.errorMessage());
        }

        @Test
        @DisplayName("手机号脱敏：只显示后 4 位")
        void phoneMasked() {
            ProductOrderApplicationService svc = Mockito.mock(ProductOrderApplicationService.class);
            ProductOrderDetailResponse order = new ProductOrderDetailResponse(
                    555L, "ORD-555", 100L, 1L, new BigDecimal("299.00"), "PICKUP", null,
                    "WALLET", "PAID", null, "CONFIRMED", "张三", "13812345678",
                    null, null, null, null, null, null, null);
            when(svc.getOrderDetail(eq(100L), eq(555L))).thenReturn(order);

            GetMyOrderStatusTool tool = new GetMyOrderStatusTool(svc);
            AgentToolResult result = tool.invoke(parsedArgs("orderId", "555"), userCtx());

            assertTrue(result.success());
            assertTrue(result.summary().contains("****5678"));
            assertFalse(result.summary().contains("13812345678"));
        }
    }

    // ==================== helpers ====================

    private static AgentContext userCtx() {
        return new AgentContext(100L, AgentType.CUSTOMER_SERVICE, null);
    }

    private static AgentToolArgs parsedArgs(String... kv) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return new ParsedToolCall("test", map, "[test]");
    }

    private static ProductOrderDetailResponse buildOrderDetail(Long orderId, Long userId) {
        return new ProductOrderDetailResponse(
                orderId, "ORD-" + orderId, userId, 1L, new BigDecimal("299.00"),
                "PICKUP", null, "WALLET", "PAID", null, "CONFIRMED",
                "张三", "13812345678", null, null, null, null, null, null, null);
    }
}
