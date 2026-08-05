package com.petcare.ai.agent.tool;

import com.petcare.ai.agent.AgentContext;
import com.petcare.store.entity.Store;
import com.petcare.store.service.StoreService;

/**
 * 客服只读 Tool：查门店公开信息（对标 docs/09 §5.1）。
 * <p>
 * 依赖 {@link StoreService}（MyBatis-Plus IService）。
 * 注意：StoreService 无定制方法，Tool 内自行判 status/deleted，只返回公开字段（对标 StoreController 公开端点逻辑）。
 */
public class GetStoreInfoTool implements AgentTool {

    public static final String NAME = "getStoreInfo";

    private final StoreService storeService;

    public GetStoreInfoTool(StoreService storeService) {
        this.storeService = storeService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "查询门店的营业时间、地址、联系电话等公开信息。当用户询问门店几点开门、地址、电话时调用。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public AgentToolResult invoke(AgentToolArgs input, AgentContext ctx) {
        try {
            // 单门店定位：取第一条启用的门店（deleted 已由 @TableLogic 自动过滤）
            Store store = storeService.list().stream()
                    .filter(s -> s.getStatus() == null || "OPEN".equalsIgnoreCase(s.getStatus()))
                    .findFirst()
                    .orElse(null);
            if (store == null) {
                return AgentToolResult.fail("暂无门店信息");
            }
            String summary = String.format(
                    "门店【%s】，营业时间：%s，地址：%s，电话：%s。",
                    store.getStoreName() == null ? "未命名" : store.getStoreName(),
                    store.getBusinessHours() == null ? "未提供" : store.getBusinessHours(),
                    store.getAddress() == null ? "未提供" : store.getAddress(),
                    store.getPhone() == null ? "未提供" : store.getPhone());
            return AgentToolResult.ok(summary);
        } catch (Exception e) {
            return AgentToolResult.fail("门店信息暂时无法获取");
        }
    }
}
