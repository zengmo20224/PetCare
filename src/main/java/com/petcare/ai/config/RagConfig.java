package com.petcare.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.petcare.ai.provider.AiProviderClient;
import com.petcare.ai.provider.LangChain4jProviderClient;
import com.petcare.ai.rag.KnowledgeIndexingService;
import com.petcare.ai.rag.RagRetrievalService;
import com.petcare.ai.service.FaqKnowledgeService;
import com.petcare.common.config.DeepSeekProperties;
import com.petcare.product.service.ProductService;
import com.petcare.service.service.ServiceItemService;
import com.petcare.store.service.StoreService;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

/**
 * V2 AI Agent RAG 装配（D-013，M8.0）。
 * <p>
 * 仅当 {@code petcare.ai.rag-enabled=true} 时生效（降级哲学：未启用时不依赖 PgVector，应用正常启动）。
 * <p>
 * <b>架构边界</b>（{@code docs/09} §3.2、§6.2）：
 * <ul>
 *   <li>本类在 {@code ai.config} 包，<b>不在</b> {@code ai.provider} 包——{@link DataSource}
 *       可作为 EmbeddingStore 的依赖而不触发 {@code AiProviderArchitectureTest} 守卫。</li>
 *   <li>不修改 {@link AiConfig}（其 11 个 Mapper 无条件 Bean 保持 V1 链路）。</li>
 *   <li>PgVector 走 langchain4j {@link EmbeddingStore}（PG JDBC），不经 MyBatis-Plus
 *       {@code @MapperScan}——避免被扫进 MySQL SqlSessionFactory。</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties({PgVectorProperties.class, RagProperties.class})
@ConditionalOnProperty(prefix = "petcare.ai", name = "rag-enabled", havingValue = "true")
public class RagConfig {

    /** PgVector 维度（BGE-small-zh-v1.5 = 512 维；换模型需 DROP+重建 ai_embedding 表）。 */
    private static final int EMBEDDING_DIMENSION = 512;

    /**
     * Embedding 模型（BGE-small-zh-v1.5，中文优化，ONNX 本地推理，512 维，零外部 API 费用）。
     * 模型文件打包在 langchain4j-embeddings-bge-small-zh-v15 JAR 内，无需运行时下载。
     * <p>
     * 选型理由（M8.1 实测）：原 all-MiniLM-L6-v2 英文模型对中文 query 召回质量差
     * （问"营业时间"召回"猫咪洗护"），BGE-zh 是中文优化模型，根治语义偏差。
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new BgeSmallZhV15EmbeddingModel();
    }

    /**
     * PgVector EmbeddingStore（向量库，存派生知识副本）。
     * <p>
     * langchain4j 自管表结构（embedding_id/embedding/text/metadata），createTable=true 首次建表；
     * skipCreateVectorExtension=true 因 schema-pgvector.sql 已 CREATE EXTENSION vector。
     * 表名 {@code ai_embedding} 与 schema-pgvector.sql 设计保持一致命名（结构以 langchain4j 标准为准）。
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(PgVectorProperties properties) {
        // DataSource 局部构造（不暴露为 bean），避免干扰 Spring Boot 主 DataSource 自动配置——
        // 主数据源仍由 profile 提供（MySQL/H2），MyBatis-Plus Mapper 查询不会误打到 PG。
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(properties.jdbcUrl());
        ds.setUsername(properties.username());
        ds.setPassword(properties.password());
        ds.setDriverClassName("org.postgresql.Driver");
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(ds)
                .table("ai_embedding")
                .dimension(EMBEDDING_DIMENSION)
                .createTable(true)
                .skipCreateVectorExtension(true)
                .build();
    }

    /**
     * 知识入库服务（FAQ/商品/服务/门店 → embed → PgVector）。
     */
    @Bean
    public KnowledgeIndexingService knowledgeIndexingService(EmbeddingModel embeddingModel,
                                                             EmbeddingStore<TextSegment> embeddingStore,
                                                             FaqKnowledgeService faqKnowledgeService,
                                                             ProductService productService,
                                                             ServiceItemService serviceItemService,
                                                             StoreService storeService) {
        return new KnowledgeIndexingService(embeddingModel, embeddingStore,
                faqKnowledgeService, productService, serviceItemService, storeService);
    }

    /**
     * RAG 检索服务（query embed → top-K 召回）。
     */
    @Bean
    public RagRetrievalService ragRetrievalService(EmbeddingModel embeddingModel,
                                                   EmbeddingStore<TextSegment> embeddingStore,
                                                   RagProperties ragProperties) {
        return new RagRetrievalService(embeddingModel, embeddingStore, ragProperties.effectiveTopK());
    }

    /**
     * langchain4j ChatModel（仅 provider-type=langchain4j 时装配）。
     * <p>
     * 复用 DeepSeek 同款连接（OpenAI 兼容协议）。DeepSeek 与 langchain4j Provider 二选一，
     * 由 {@code petcare.ai.provider-type} 切换，V1 客服/分析链路在 provider-type=deepseek 时零回归。
     */
    @Bean
    @ConditionalOnProperty(prefix = "petcare.ai", name = "provider-type", havingValue = "langchain4j")
    public ChatModel langchain4jChatModel(DeepSeekProperties properties) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey() == null ? "" : properties.apiKey())
                .modelName(properties.model());
        if (properties.maxTokens() != null) {
            builder.maxTokens(properties.maxTokens());
        }
        if (properties.connectTimeout() != null) {
            builder.timeout(java.time.Duration.ofMillis(properties.connectTimeout()));
        }
        return builder.build();
    }

    /**
     * langchain4j {@link AiProviderClient} 实现（@Primary，覆盖 V1 DeepSeek Bean）。
     * <p>
     * 仅 provider-type=langchain4j 时生效。在 V1 默认（deepseek）下不装配，DeepSeek Bean 继续服务。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "petcare.ai", name = "provider-type", havingValue = "langchain4j")
    public AiProviderClient langchain4jProviderClient(ChatModel langchain4jChatModel) {
        return new LangChain4jProviderClient(langchain4jChatModel);
    }
}
