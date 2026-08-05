package com.petcare.ai.agent.tool;

import com.petcare.ai.agent.AgentContext;
import com.petcare.product.dto.ProductDetailResponse;
import com.petcare.product.service.ProductCatalogApplicationService;

/**
 * 客服只读 Tool：查商品实时快照（对标 docs/09 §5.1）。
 * <p>
 * 依赖 {@link ProductCatalogApplicationService}（业务只读门面），不依赖 Mapper。
 * 价格/库存实时取自业务库，不信向量库缓存（B6）。
 */
public class GetProductInfoTool implements AgentTool {

    public static final String NAME = "getProductInfo";
    private static final String ARG_PRODUCT_ID = "productId";

    private final ProductCatalogApplicationService productCatalogService;

    public GetProductInfoTool(ProductCatalogApplicationService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "按商品 ID 查询商品的实时名称、价格、库存状态和分类。当用户询问某个具体商品的价格或是否有货时调用。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public AgentToolResult invoke(AgentToolArgs input, AgentContext ctx) {
        Long productId = input.getAsLong(ARG_PRODUCT_ID);
        if (productId == null) {
            return AgentToolResult.fail("缺少有效的商品 ID");
        }
        try {
            ProductDetailResponse p = productCatalogService.getProductDetail(productId);
            if (p == null) {
                return AgentToolResult.fail("未找到该商品");
            }
            String stockDesc = (p.stock() != null && p.stock() > 0)
                    ? ("库存 " + p.stock() + " 件，有货")
                    : "暂无库存";
            String summary = String.format(
                    "商品【%s】（分类：%s），售价 %s 元，%s。",
                    p.name(),
                    p.categoryName() == null ? "未分类" : p.categoryName(),
                    p.price() == null ? "未知" : p.price().toPlainString(),
                    stockDesc);
            return AgentToolResult.ok(summary);
        } catch (Exception e) {
            // 业务 Service 在商品不存在/下架时抛 BusinessException，统一降级为"未找到"
            return AgentToolResult.fail("未找到该商品");
        }
    }
}
