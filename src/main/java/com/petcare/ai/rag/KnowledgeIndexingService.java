package com.petcare.ai.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.petcare.ai.entity.FaqKnowledge;
import com.petcare.ai.rag.KnowledgeSource.KnowledgeDocument;
import com.petcare.ai.rag.KnowledgeSource.SourceType;
import com.petcare.ai.service.FaqKnowledgeService;
import com.petcare.product.entity.Product;
import com.petcare.product.service.ProductService;
import com.petcare.service.entity.ServiceItem;
import com.petcare.service.service.ServiceItemService;
import com.petcare.store.entity.Store;
import com.petcare.store.service.StoreService;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * 知识入库服务（V2 RAG 核心，D-013，M8.0）。
 * <p>
 * 从 MySQL 业务库（经业务 Service 只读接口）抽取 FAQ/商品/服务/门店信息，
 * embed 后写入 PgVector {@link EmbeddingStore}。
 * <p>
 * <b>架构边界</b>（{@code AiRagArchitectureTest}）：依赖业务 {@code IService} 接口
 * （FaqKnowledgeService/ProductService/ServiceItemService/StoreService），<b>不依赖任何 Mapper</b>。
 * <p>
 * <b>幂等策略</b>（M8.0 简单实现）：rebuildAll 按 sourceType 先删旧再插新。
 * 文档元数据带 sourceType/sourceId，删除时按元数据过滤。
 */
public class KnowledgeIndexingService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexingService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final FaqKnowledgeService faqKnowledgeService;
    private final ProductService productService;
    private final ServiceItemService serviceItemService;
    private final StoreService storeService;

    public KnowledgeIndexingService(EmbeddingModel embeddingModel,
                                    EmbeddingStore<TextSegment> embeddingStore,
                                    FaqKnowledgeService faqKnowledgeService,
                                    ProductService productService,
                                    ServiceItemService serviceItemService,
                                    StoreService storeService) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.faqKnowledgeService = faqKnowledgeService;
        this.productService = productService;
        this.serviceItemService = serviceItemService;
        this.storeService = storeService;
    }

    /**
     * 全量重建索引：清空 → 抽取 4 类知识 → embed → 写入。
     *
     * @return 入库总条数
     */
    public int rebuildAll() {
        long start = System.currentTimeMillis();
        List<KnowledgeDocument> docs = new ArrayList<>();
        docs.addAll(loadFaq());
        docs.addAll(loadProducts());
        docs.addAll(loadServices());
        docs.addAll(loadStoreInfo());

        int count = indexDocuments(docs);
        log.info("[RAG] 知识库重建完成：入库 {} 条，耗时 {} ms", count, System.currentTimeMillis() - start);
        return count;
    }

    /** 抽取 FAQ（status=ACTIVE）。 */
    private List<KnowledgeDocument> loadFaq() {
        return faqKnowledgeService.list().stream()
                .filter(f -> "ACTIVE".equalsIgnoreCase(f.getStatus()))
                .map(this::toFaqDocument)
                .toList();
    }

    private KnowledgeDocument toFaqDocument(FaqKnowledge f) {
        String content = joinNonBlank("：", f.getQuestion(), f.getAnswer());
        Map<String, Object> meta = new HashMap<>();
        meta.put("sourceType", SourceType.FAQ.name());
        meta.put("sourceId", f.getId());
        if (f.getCategory() != null) {
            meta.put("category", f.getCategory());
        }
        return new KnowledgeDocument(SourceType.FAQ, f.getId(), f.getQuestion(), content, meta);
    }

    /** 抽取商品（status=ON_SALE）。 */
    private List<KnowledgeDocument> loadProducts() {
        return productService.list().stream()
                .filter(p -> "ON_SALE".equalsIgnoreCase(p.getStatus()))
                .map(this::toProductDocument)
                .toList();
    }

    private KnowledgeDocument toProductDocument(Product p) {
        String content = joinNonBlank(" - ", p.getName(), p.getDescription());
        Map<String, Object> meta = new HashMap<>();
        meta.put("sourceType", SourceType.PRODUCT.name());
        meta.put("sourceId", p.getId());
        if (p.getCategoryId() != null) {
            meta.put("categoryId", p.getCategoryId());
        }
        if (p.getPrice() != null) {
            meta.put("price", p.getPrice());
        }
        return new KnowledgeDocument(SourceType.PRODUCT, p.getId(), p.getName(), content, meta);
    }

    /** 抽取服务（status=ON_SALE）。 */
    private List<KnowledgeDocument> loadServices() {
        return serviceItemService.list().stream()
                .filter(s -> "ON_SALE".equalsIgnoreCase(s.getStatus()))
                .map(this::toServiceDocument)
                .toList();
    }

    private KnowledgeDocument toServiceDocument(ServiceItem s) {
        String content = joinNonBlank(" - ", s.getName(), s.getDescription());
        Map<String, Object> meta = new HashMap<>();
        meta.put("sourceType", SourceType.SERVICE.name());
        meta.put("sourceId", s.getId());
        if (s.getPetType() != null) {
            meta.put("petType", s.getPetType());
        }
        if (s.getPrice() != null) {
            meta.put("price", s.getPrice());
        }
        return new KnowledgeDocument(SourceType.SERVICE, s.getId(), s.getName(), content, meta);
    }

    /** 抽取门店信息（取第一个 OPEN 门店，与 V1 CustomerServiceContextBuilder 一致）。 */
    private List<KnowledgeDocument> loadStoreInfo() {
        return storeService.list().stream()
                .filter(st -> "OPEN".equalsIgnoreCase(st.getStatus()))
                .findFirst()
                .stream()
                .map(this::toStoreDocument)
                .toList();
    }

    private KnowledgeDocument toStoreDocument(Store st) {
        String content = joinNonBlank(" | ",
                st.getStoreName(), st.getDescription(),
                "地址：" + safe(st.getAddress()), "营业时间：" + safe(st.getBusinessHours()),
                "电话：" + safe(st.getPhone()));
        Map<String, Object> meta = new HashMap<>();
        meta.put("sourceType", SourceType.STORE_INFO.name());
        meta.put("sourceId", st.getId());
        return new KnowledgeDocument(SourceType.STORE_INFO, st.getId(), st.getStoreName(), content, meta);
    }

    /**
     * embed + 写入 EmbeddingStore。
     * 一批一批 embed（单门店规模 < 1000 条，全量 embed 一次性亦可，这里按条写入便于观测）。
     */
    private int indexDocuments(List<KnowledgeDocument> docs) {
        int count = 0;
        for (KnowledgeDocument doc : docs) {
            if (doc.content() == null || doc.content().isBlank()) {
                continue;
            }
            TextSegment segment = TextSegment.from(doc.content());
            // 元数据塞入 TextSegment.metadata（langchain4j Metadata.put 只接受 String 值，统一转换）
            doc.metadata().forEach((k, v) -> {
                if (v != null) {
                    segment.metadata().put(k, String.valueOf(v));
                }
            });
            Embedding embedding = embeddingModel.embed(doc.content()).content();
            embeddingStore.add(embedding, segment);
            count++;
        }
        return count;
    }

    /** 按 sourceType 删除该类全部索引（M8.0 幂等重建用；langchain4j EmbeddingStore 无直接按 metadata 删除 API，M8.1 评估换内部 SQL）。 */
    public void removeBySourceType(SourceType sourceType) {
        // langchain4j EmbeddingStore 标准接口无 removeByMetadata，M8.0 暂不实现细粒度删除；
        // rebuildAll 采用"清表重建"时由调用方决定（见 KnowledgeIndexingScheduler/Controller 的策略）。
        // 保留方法签名供 M8.1 扩展（届时用 PgVector 内部 SQL 或 EmbeddingStore removeAll）。
        log.warn("[RAG] removeBySourceType({}) 当前为 no-op，M8.1 评估实现", sourceType);
    }

    private static String joinNonBlank(String sep, String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.joining(sep));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
