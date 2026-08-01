package com.petcare.user.config;

import com.petcare.common.config.WechatProperties;
import com.petcare.user.auth.DisabledWechatLoginProvider;
import com.petcare.user.auth.MockWechatLoginProvider;
import com.petcare.user.auth.RealWechatLoginProvider;
import com.petcare.user.auth.WechatLoginProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for the WeChat login provider beans.
 *
 * <p>Exactly one {@link WechatLoginProvider} bean is active, selected by
 * {@code petcare.wechat.mode}:
 * <ul>
 *   <li>{@code real} → {@link RealWechatLoginProvider} (calls jscode2session).</li>
 *   <li>{@code mock} → {@link MockWechatLoginProvider} (deterministic local openid).</li>
 *   <li>{@code disabled} / absent → {@link DisabledWechatLoginProvider} via
 *       {@code @ConditionalOnMissingBean} (endpoint returns 422).</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(WechatProperties.class)
public class WechatLoginConfig {

    @Bean
    @ConditionalOnProperty(prefix = "petcare.wechat", name = "mode", havingValue = "real")
    public WechatLoginProvider realWechatLoginProvider(
            WechatProperties properties,
            RestClient.Builder restClientBuilder) {
        return new RealWechatLoginProvider(properties, restClientBuilder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "petcare.wechat", name = "mode", havingValue = "mock")
    public WechatLoginProvider mockWechatLoginProvider(WechatProperties properties) {
        return new MockWechatLoginProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean(WechatLoginProvider.class)
    public WechatLoginProvider disabledWechatLoginProvider() {
        return new DisabledWechatLoginProvider();
    }
}
