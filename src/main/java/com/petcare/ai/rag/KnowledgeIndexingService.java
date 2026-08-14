package com.petcare.ai.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.petcare.ai.entity.FaqKnowledge;
import com.petcare.ai.rag.KnowledgeSource.KnowledgeDocument;
import com.petcare.ai.rag.KnowledgeSource.SourceType;
import com.petcare.ai.service.FaqKnowledgeService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
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
 * <b>幂等策略</b>（A6 修复）：rebuildAll 执行前 {@code removeAll()} 全清，
 * 保证"全清 → 全量重建"的强幂等语义——重复执行不产生重复向量（单门店规模 &lt; 1000 条，
 * 全清重建成本可接受）。重建期间持有进程内互斥标志，并发触发直接拒绝。
 */
public class KnowledgeIndexingService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexingService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final FaqKnowledgeService faqKnowledgeService;
    private final ProductService productService;
    private final ServiceItemService serviceItemService;
    private final StoreService storeService;

    /** A7 修复：进程内互斥标志——全量重建进行中时拒绝并发触发（定时任务与管理员手动互斥）。 */
    private final AtomicBoolean rebuilding = new AtomicBoolean(false);

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
     * 全量重建索引：互斥校验 → 全清旧向量 → 抽取 4 类知识 → embed → 写入。
     *
     * @return 入库总条数
     * @throws BusinessException 已有重建正在进行（并发互斥，A7）
     */
    public int rebuildAll() {
        if (!rebuilding.compareAndSet(false, true)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "知识库重建正在进行中，请稍后再试");
        }
        try {
            long start = System.currentTimeMillis();

            // A6 修复：先全清旧向量再重建——旧实现只增不删，定时任务 + 手动重建
            // 会无限追加重复向量（检索质量劣化、表膨胀）
            embeddingStore.removeAll();

            List<KnowledgeDocument> docs = new ArrayList<>();
            docs.addAll(loadFaq());
            docs.addAll(loadProducts());
            docs.addAll(loadServices());
            docs.addAll(loadStoreInfo());

            int count = indexDocuments(docs);
            log.info("[RAG] 知识库重建完成：清空后重新入库 {} 条，耗时 {} ms",
                    count, System.currentTimeMillis() - start);
            return count;
        } finally {
            rebuilding.set(false);
        }
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

    private static String joinNonBlank(String sep, String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.joining(sep));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
