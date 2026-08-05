package com.petcare.ai.agent.tool;

import com.petcare.ai.agent.AgentContext;
import com.petcare.service.entity.ServiceItem;
import com.petcare.service.service.ServiceItemService;

/**
 * 客服只读 Tool：查服务项目实时快照（对标 docs/09 §5.1）。
 * <p>
 * 依赖 {@link ServiceItemService#getOnSaleItem(Long)}（业务只读门面），不依赖 Mapper。
 */
public class GetServiceInfoTool implements AgentTool {

    public static final String NAME = "getServiceInfo";
    private static final String ARG_SERVICE_ID = "serviceId";

    private final ServiceItemService serviceItemService;

    public GetServiceInfoTool(ServiceItemService serviceItemService) {
        this.serviceItemService = serviceItemService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "按服务 ID 查询服务项目的实时时长、价格和适用宠物类型。当用户询问某项服务的价格或时长时调用。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public AgentToolResult invoke(AgentToolArgs input, AgentContext ctx) {
        Long serviceId = input.getAsLong(ARG_SERVICE_ID);
        if (serviceId == null) {
            return AgentToolResult.fail("缺少有效的服务 ID");
        }
        try {
            ServiceItem s = serviceItemService.getOnSaleItem(serviceId);
            if (s == null) {
                return AgentToolResult.fail("未找到该服务");
            }
            String summary = String.format(
                    "服务【%s】（模式：%s），时长 %s 分钟，售价 %s 元，适用宠物：%s/%s。",
                    s.getName(),
                    s.getServiceMode() == null ? "未指定" : s.getServiceMode(),
                    s.getDurationMinutes() == null ? "未知" : s.getDurationMinutes(),
                    s.getPrice() == null ? "未知" : s.getPrice().toPlainString(),
                    s.getPetType() == null ? "不限" : s.getPetType(),
                    s.getPetSize() == null ? "不限" : s.getPetSize());
            return AgentToolResult.ok(summary);
        } catch (Exception e) {
            return AgentToolResult.fail("未找到该服务");
        }
    }
}
