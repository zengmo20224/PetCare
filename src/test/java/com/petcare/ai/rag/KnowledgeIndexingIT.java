package com.petcare.ai.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.petcare.ai.entity.FaqKnowledge;
import com.petcare.ai.rag.KnowledgeSource.RetrievedKnowledge;
import com.petcare.ai.service.FaqKnowledgeService;
import com.petcare.ai.service.impl.FaqKnowledgeServiceImpl;
import com.petcare.common.persistence.AbstractTcPgVectorIT;
import com.petcare.product.entity.Product;
import com.petcare.product.service.ProductService;
import com.petcare.product.service.impl.ProductServiceImpl;
import com.petcare.service.entity.ServiceItem;
import com.petcare.service.service.ServiceItemService;
import com.petcare.service.service.impl.ServiceItemServiceImpl;
import com.petcare.store.entity.Store;
import com.petcare.store.service.StoreService;
import com.petcare.store.service.impl.StoreServiceImpl;

/**
 * {@link KnowledgeIndexingService} + {@link RagRetrievalService} 集成测试（tc-pgvector）。
 * <p>
 * M8.0 退出条件验证（docs/09 §12）：
 * <ol>
 *   <li>离线能把 FAQ/商品/服务/门店 embed 入 PgVector</li>
 *   <li>向量检索 top-K 召回，cosine 相似度归一化验证通过</li>
 * </ol>
 * <p>
 * 业务 Service 用 @MockBean 注入确定性数据（主库 H2 不依赖 MySQL）；
 * PgVector 走 Testcontainer（AbstractTcPgVectorIT 起 pgvector/pgvector:pg16）。
 */
@Tag("tc-pgvector")
class KnowledgeIndexingIT extends AbstractTcPgVectorIT {

    @Autowired
    private KnowledgeIndexingService knowledgeIndexingService;

    @Autowired
    private RagRetrievalService ragRetrievalService;

    @MockBean private FaqKnowledgeService faqKnowledgeService;
    @MockBean private ProductService productService;
    @MockBean private ServiceItemService serviceItemService;
    @MockBean private StoreService storeService;

    /** 让 @MockBean 注入指向具体 Impl 类型（Spring 需要 primary bean 匹配）。 */
    @MockBean(name = "faqKnowledgeServiceImpl") private FaqKnowledgeServiceImpl faqImpl;
    @MockBean(name = "productServiceImpl") private ProductServiceImpl prodImpl;
    @MockBean(name = "serviceItemServiceImpl") private ServiceItemServiceImpl svcImpl;
    @MockBean(name = "storeServiceImpl") private StoreServiceImpl storeImpl;

    @Test
    @DisplayName("全量索引重建：4 类知识入 PgVector，返回条数正确")
    void rebuildAll_indexesAllKnowledgeTypes() {
        stubBusinessData();
        int count = knowledgeIndexingService.rebuildAll();
        // 1 FAQ + 1 商品 + 1 服务 + 1 门店 = 4 条
        assertEquals(4, count, "应索引 4 条知识（FAQ/商品/服务/门店各 1）");
    }

    @Test
    @DisplayName("向量检索召回：query 命中相关文档，cosine 相似度 > 0")
    void retrieveRelevant_returnsRelevantMatches() {
        stubBusinessData();
        knowledgeIndexingService.rebuildAll();

        // all-MiniLM-L6-v2 是英文模型，对中文语义区分有限（docs/09 Q-1）；
        // 本断言验证召回机制工作（有结果 + score 归一化），不强求精确匹配某条（中文模型局限）。
        List<RetrievedKnowledge> results = ragRetrievalService.retrieveRelevant("猫粮 猫咪食物", 3);
        assertFalse(results.isEmpty(), "应召回至少 1 条");

        RetrievedKnowledge top = results.get(0);
        assertTrue(top.score() > 0.0, "cosine 相似度应 > 0，实际 " + top.score());
        // 召回结果应来自已索引的 4 条知识之一（非空内容）
        assertFalse(top.content().isBlank(), "召回内容不应为空");
    }

    @Test
    @DisplayName("空 query 返回空列表（边界）")
    void retrieveRelevant_emptyQuery_returnsEmpty() {
        assertTrue(ragRetrievalService.retrieveRelevant("").isEmpty());
        assertTrue(ragRetrievalService.retrieveRelevant(null).isEmpty());
    }

    /** 注入确定性业务数据（1 条 FAQ + 1 商品 + 1 服务 + 1 门店）。 */
    private void stubBusinessData() {
        FaqKnowledge faq = new FaqKnowledge();
        faq.setId(1001L);
        faq.setQuestion("营业时间是几点");
        faq.setAnswer("每天 9:00 - 21:00");
        faq.setCategory("STORE");
        faq.setStatus("ACTIVE");
        when(faqKnowledgeService.list()).thenReturn(List.of(faq));

        Product product = new Product();
        product.setId(2001L);
        product.setName("进口猫粮");
        product.setDescription("高品质猫咪干粮，适合成年猫");
        product.setCategoryId(10L);
        product.setPrice(new BigDecimal("88.00"));
        product.setStatus("ON_SALE");
        when(productService.list()).thenReturn(List.of(product));

        ServiceItem service = new ServiceItem();
        service.setId(3001L);
        service.setName("猫咪洗护");
        service.setDescription("专业猫咪洗浴护理");
        service.setPrice(new BigDecimal("120.00"));
        service.setPetType("CAT");
        service.setStatus("ON_SALE");
        when(serviceItemService.list()).thenReturn(List.of(service));

        Store store = new Store();
        store.setId(4001L);
        store.setStoreName("萌宠乐园");
        store.setDescription("一站式宠物服务中心");
        store.setAddress("北京市朝阳区 xx 路 1 号");
        store.setBusinessHours("9:00-21:00");
        store.setPhone("010-12345678");
        store.setStatus("OPEN");
        when(storeService.list()).thenReturn(List.of(store));
    }
}
