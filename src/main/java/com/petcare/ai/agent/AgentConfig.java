package com.petcare.ai.agent;

import com.petcare.ai.agent.audit.AiToolCallLogService;
import com.petcare.ai.agent.tool.AgentTool;
import com.petcare.ai.agent.tool.GetActivityEffectTool;
import com.petcare.ai.agent.tool.GetBookingFunnelTool;
import com.petcare.ai.agent.tool.GetCommunityMetricsTool;
import com.petcare.ai.agent.tool.GetMyBookingStatusTool;
import com.petcare.ai.agent.tool.GetMyOrderStatusTool;
import com.petcare.ai.agent.tool.GetMyPetProfileTool;
import com.petcare.ai.agent.tool.GetProductInfoTool;
import com.petcare.ai.agent.tool.GetSalesTrendTool;
import com.petcare.ai.agent.tool.GetServiceInfoTool;
import com.petcare.ai.agent.tool.GetStoreInfoTool;
import com.petcare.ai.agent.registry.AgentToolRegistry;
import com.petcare.ai.analytics.ActivityAnalyticsAggregator;
import com.petcare.ai.analytics.BusinessAnalyticsAggregator;
import com.petcare.ai.analytics.CommunityAnalyticsAggregator;
import com.petcare.ai.analytics.SalesAnalyticsAggregator;
import com.petcare.ai.domain.CustomerServiceContextBuilder;
import com.petcare.ai.provider.AiProviderClient;
import com.petcare.ai.rag.RagRetrievalService;
import com.petcare.ai.service.AiConversationApplicationService;
import com.petcare.ai.service.StreamingConversationService;
import com.petcare.booking.service.BookingApplicationService;
import com.petcare.product.service.ProductCatalogApplicationService;
import com.petcare.product.service.ProductOrderApplicationService;
import com.petcare.service.service.ServiceItemService;
import com.petcare.store.service.StoreService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * V2 AI Agent 装配（M8.1，对标 docs/09 §11）。
 * <p>
 * <b>条件装配</b>：整个配置类仅当 {@code petcare.ai.agent-enabled=true} 时生效。
 * agent-enabled=false 时：
 * <ul>
 *   <li>所有 Tool / Registry / Agent / Streaming Bean 不创建；</li>
 *   <li>{@link AiConversationApplicationServiceImpl} 的 {@code ObjectProvider<CustomerServiceAgent>} 返回 null，
 *       走 V1 同步路径；</li>
 *   <li>{@link AiConversationStreamController} 不注册（404）。</li>
 * </ul>
 * <p>
 * <b>注</b>：{@link AiToolCallLogService} 和 {@link AiToolCallLogMapper} 不加 agent-enabled 条件——
 * 它们是通用审计基础设施（@Mapper 由 MyBatis 扫描，Service 用 @Service 装配），agent 关闭时也安全存在。
 */
@Configuration
@ConditionalOnProperty(prefix = "petcare.ai", name = "agent-enabled", havingValue = "true")
public class AgentConfig {

    // ==================== 客服只读 Tool（5 个，对标 docs/09 §5.1）====================

    @Bean
    public GetProductInfoTool getProductInfoTool(ProductCatalogApplicationService catalogService) {
        return new GetProductInfoTool(catalogService);
    }

    @Bean
    public GetServiceInfoTool getServiceInfoTool(ServiceItemService serviceItemService) {
        return new GetServiceInfoTool(serviceItemService);
    }

    @Bean
    public GetStoreInfoTool getStoreInfoTool(StoreService storeService) {
        return new GetStoreInfoTool(storeService);
    }

    @Bean
    public GetMyOrderStatusTool getMyOrderStatusTool(ProductOrderApplicationService orderService) {
        return new GetMyOrderStatusTool(orderService);
    }

    @Bean
    public GetMyBookingStatusTool getMyBookingStatusTool(BookingApplicationService bookingService) {
        return new GetMyBookingStatusTool(bookingService);
    }

    // ==================== Registry ====================

    @Bean
    public AgentToolRegistry customerServiceToolRegistry(
            GetProductInfoTool productTool,
            GetServiceInfoTool serviceTool,
            GetStoreInfoTool storeTool,
            GetMyOrderStatusTool orderTool,
            GetMyBookingStatusTool bookingTool) {
        List<AgentTool> tools = List.of(productTool, serviceTool, storeTool, orderTool, bookingTool);
        return new AgentToolRegistry(tools);
    }

    // ==================== Agent ====================

    @Bean
    public CustomerServiceAgent customerServiceAgent(
            AiProviderClient providerClient,
            CustomerServiceContextBuilder contextBuilder,
            ObjectProvider<RagRetrievalService> ragRetrievalServiceProvider,
            @Qualifier("customerServiceToolRegistry") AgentToolRegistry toolRegistry,
            AiToolCallLogService auditService) {
        // RagRetrievalService 可选：rag-enabled=false 时为 null，Agent 走纯 Tool + context 路径
        return new CustomerServiceAgent(
                providerClient,
                contextBuilder,
                ragRetrievalServiceProvider.getIfUnique(),
                toolRegistry,
                auditService);
    }

    // ==================== 经营分析只读 Tool（4 个，对标 docs/09 §5.2）====================

    @Bean
    public GetSalesTrendTool getSalesTrendTool(SalesAnalyticsAggregator salesAggregator) {
        return new GetSalesTrendTool(salesAggregator);
    }

    @Bean
    public GetBookingFunnelTool getBookingFunnelTool(BusinessAnalyticsAggregator businessAggregator) {
        return new GetBookingFunnelTool(businessAggregator);
    }

    @Bean
    public GetCommunityMetricsTool getCommunityMetricsTool(CommunityAnalyticsAggregator communityAggregator) {
        return new GetCommunityMetricsTool(communityAggregator);
    }

    @Bean
    public GetActivityEffectTool getActivityEffectTool(ActivityAnalyticsAggregator activityAggregator) {
        return new GetActivityEffectTool(activityAggregator);
    }

    @Bean
    public AgentToolRegistry analyticsToolRegistry(
            GetSalesTrendTool salesTrendTool,
            GetBookingFunnelTool bookingFunnelTool,
            GetCommunityMetricsTool communityMetricsTool,
            GetActivityEffectTool activityEffectTool) {
        List<AgentTool> tools = List.of(salesTrendTool, bookingFunnelTool, communityMetricsTool, activityEffectTool);
        return new AgentToolRegistry(tools);
    }

    @Bean
    public AnalyticsAgent analyticsAgent(
            AiProviderClient providerClient,
            @Qualifier("analyticsToolRegistry") AgentToolRegistry analyticsToolRegistry,
            AiToolCallLogService auditService) {
        return new AnalyticsAgent(providerClient, analyticsToolRegistry, auditService);
    }

    // ==================== M8.3 社区助手 + 文本审核（对标 docs/09 §5.3）====================

    @Bean
    public GetMyPetProfileTool getMyPetProfileTool(com.petcare.user.service.PetApplicationService petApplicationService) {
        return new GetMyPetProfileTool(petApplicationService);
    }

    @Bean
    public AgentToolRegistry postAssistantToolRegistry(GetMyPetProfileTool petProfileTool) {
        return new AgentToolRegistry(List.of(petProfileTool));
    }

    @Bean
    public PostAssistantAgent postAssistantAgent(
            AiProviderClient providerClient,
            @Qualifier("postAssistantToolRegistry") AgentToolRegistry postAssistantToolRegistry,
            AiToolCallLogService auditService) {
        return new PostAssistantAgent(providerClient, postAssistantToolRegistry, auditService);
    }

    @Bean
    public ModerationAgent moderationAgent(AiProviderClient providerClient) {
        return new ModerationAgent(providerClient);
    }

    /**
     * M8.3 文本审核异步钩子。阈值 {@code petcare.ai.moderation.violation-threshold}
     * （默认 0.8）：VIOLATION 且置信度达标才产 PostReport 进人工队列。
     */
    @Bean
    public com.petcare.ai.service.AiModerationReviewService aiModerationReviewService(
            ModerationAgent moderationAgent,
            com.petcare.community.service.CommunityInteractionService communityInteractionService,
            org.springframework.core.env.Environment environment) {
        double threshold = environment.getProperty(
                "petcare.ai.moderation.violation-threshold", Double.class, 0.8);
        return new com.petcare.ai.service.AiModerationReviewService(
                moderationAgent, communityInteractionService, threshold);
    }

    // ==================== Streaming ====================

    @Bean
    public StreamingConversationService streamingConversationService(
            AiConversationApplicationService conversationService) {
        return new StreamingConversationService(conversationService);
    }
}
