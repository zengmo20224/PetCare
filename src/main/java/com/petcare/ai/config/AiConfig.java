package com.petcare.ai.config;

import com.petcare.ai.analytics.*;
import com.petcare.ai.domain.CustomerServiceContextBuilder;
import com.petcare.ai.provider.AiProviderClient;
import com.petcare.ai.provider.DeepSeekAiProviderClient;
import com.petcare.ai.provider.DisabledAiProviderClient;
import com.petcare.booking.mapper.ServiceBookingMapper;
import com.petcare.common.config.DeepSeekProperties;
import com.petcare.community.mapper.PostCommentMapper;
import com.petcare.community.mapper.PostMapper;
import com.petcare.community.mapper.PostReportMapper;
import com.petcare.marketing.mapper.MarketingActivityMapper;
import com.petcare.product.mapper.ProductMapper;
import com.petcare.product.mapper.ProductOrderMapper;
import com.petcare.service.mapper.ServiceItemMapper;
import com.petcare.ai.mapper.FaqKnowledgeMapper;
import com.petcare.store.mapper.StoreConfigMapper;
import com.petcare.store.mapper.StoreMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for AI module beans.
 * Wires up the Provider client, context builder, and analytics aggregators.
 */
@Configuration
@EnableConfigurationProperties(DeepSeekProperties.class)
public class AiConfig {

    /**
     * Real DeepSeek provider client. Active only when
     * {@code petcare.ai.provider-enabled=true} (D-004 修订 2026-07-21).
     * The adapter validates DEEPSEEK_API_KEY / DEEPSEEK_MODEL at construction time.
     */
    @Bean
    @ConditionalOnProperty(prefix = "petcare.ai", name = "provider-enabled", havingValue = "true")
    public AiProviderClient deepSeekAiProviderClient(
            DeepSeekProperties properties,
            RestClient.Builder restClientBuilder) {
        return new DeepSeekAiProviderClient(properties, restClientBuilder);
    }

    /**
     * Fallback provider bean when {@code petcare.ai.provider-enabled} is false/absent.
     * Always throws {@code AiProviderUnavailableException}; never fakes a successful response.
     */
    @Bean
    @ConditionalOnMissingBean(AiProviderClient.class)
    public AiProviderClient disabledAiProviderClient() {
        return new DisabledAiProviderClient();
    }

    /**
     * Context builder for AI customer service.
     * Only reads approved data sources: store, store_config, services, products, FAQ.
     */
    @Bean
    public CustomerServiceContextBuilder customerServiceContextBuilder(
            StoreMapper storeMapper,
            StoreConfigMapper storeConfigMapper,
            ServiceItemMapper serviceItemMapper,
            ProductMapper productMapper,
            FaqKnowledgeMapper faqKnowledgeMapper) {
        return new CustomerServiceContextBuilder(
                storeMapper, storeConfigMapper, serviceItemMapper,
                productMapper, faqKnowledgeMapper);
    }

    @Bean
    public BusinessAnalyticsAggregator businessAnalyticsAggregator(ServiceBookingMapper serviceBookingMapper) {
        return new BusinessAnalyticsAggregator(serviceBookingMapper);
    }

    @Bean
    public CommunityAnalyticsAggregator communityAnalyticsAggregator(
            PostMapper postMapper,
            PostCommentMapper postCommentMapper,
            PostReportMapper postReportMapper) {
        return new CommunityAnalyticsAggregator(postMapper, postCommentMapper, postReportMapper);
    }

    @Bean
    public SalesAnalyticsAggregator salesAnalyticsAggregator(ProductOrderMapper productOrderMapper) {
        return new SalesAnalyticsAggregator(productOrderMapper);
    }

    @Bean
    public ActivityAnalyticsAggregator activityAnalyticsAggregator(
            MarketingActivityMapper marketingActivityMapper) {
        return new ActivityAnalyticsAggregator(marketingActivityMapper);
    }
}
