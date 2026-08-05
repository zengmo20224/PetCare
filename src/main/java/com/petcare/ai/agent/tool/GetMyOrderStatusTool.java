package com.petcare.ai.agent.tool;

import com.petcare.ai.agent.AgentContext;
import com.petcare.product.dto.ProductOrderDetailResponse;
import com.petcare.product.service.ProductOrderApplicationService;

/**
 * 客服只读 Tool：查当前用户的订单状态（对标 docs/09 §5.1，用户身份 Tool）。
 * <p>
 * <b>越权防护（B7 + T3）</b>：复用 {@link ProductOrderApplicationService#getOrderDetail(Long, Long)}
 * 的归属校验——该方法在 orderId 非当前用户时抛 FORBIDDEN。
 * Tool 捕获所有异常统一返回"未找到相关订单"，不向 LLM 泄露订单是否存在或属于他人。
 */
public class GetMyOrderStatusTool implements AgentTool {

    public static final String NAME = "getMyOrderStatus";
    private static final String ARG_ORDER_ID = "orderId";

    private final ProductOrderApplicationService orderService;

    public GetMyOrderStatusTool(ProductOrderApplicationService orderService) {
        this.orderService = orderService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "按订单 ID 查询【当前用户自己】的订单状态、金额和商品清单。用户问'我的订单怎么样了'时调用，需用户提供订单号。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public AgentToolResult invoke(AgentToolArgs input, AgentContext ctx) {
        // 强制登录态（Registry 已校验，此处防御性二次确认）
        ctx.requireUser();
        Long orderId = input.getAsLong(ARG_ORDER_ID);
        if (orderId == null) {
            return AgentToolResult.fail("缺少有效的订单号");
        }
        try {
            ProductOrderDetailResponse order = orderService.getOrderDetail(ctx.currentUserId(), orderId);
            if (order == null) {
                return AgentToolResult.fail("未找到相关订单");
            }
            // 脱敏：contactPhone 截断后 4 位前加星
            String maskedPhone = maskPhone(order.contactPhone());
            String itemsDesc = order.items() == null || order.items().isEmpty()
                    ? "（无商品明细）"
                    : order.items().stream()
                            .map(i -> i.productName() + " x" + i.quantity())
                            .reduce((a, b) -> a + "，" + b)
                            .orElse("（无商品明细）");
            String summary = String.format(
                    "订单 %s（状态：%s，支付：%s/%s），合计 %s 元，商品：%s，联系电话：%s。",
                    order.orderNo() == null ? String.valueOf(orderId) : order.orderNo(),
                    order.status() == null ? "未知" : order.status(),
                    order.paymentMethod() == null ? "未知" : order.paymentMethod(),
                    order.paymentStatus() == null ? "未知" : order.paymentStatus(),
                    order.totalAmount() == null ? "未知" : order.totalAmount().toPlainString(),
                    itemsDesc,
                    maskedPhone);
            return AgentToolResult.ok(summary);
        } catch (Exception e) {
            // 含越权（FORBIDDEN）/ 不存在（NOT_FOUND）：统一降级，不泄露订单存在性
            return AgentToolResult.fail("未找到相关订单");
        }
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "未提供";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
}
