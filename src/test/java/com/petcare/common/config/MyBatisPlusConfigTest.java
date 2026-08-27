package com.petcare.common.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MyBatisPlusConfig} 单测。
 * <p>
 * 2026-08-23 安全审计 M2：分页插件必须设置全局 maxLimit，
 * 否则客户端可用 size=100000 拖库（含匿名可达的 /api/v1/posts、/api/v1/products）。
 */
class MyBatisPlusConfigTest {

    @Test
    @DisplayName("分页插件必须设置全局 maxLimit=100，钳制所有未显式校验的分页端点")
    void paginationInterceptor_hasGlobalMaxLimit() {
        MyBatisPlusConfig config = new MyBatisPlusConfig();
        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = interceptor.getInterceptors().stream()
                .filter(PaginationInnerInterceptor.class::isInstance)
                .map(PaginationInnerInterceptor.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未配置 PaginationInnerInterceptor"));

        assertThat(pagination.getMaxLimit()).as("全局分页 size 上限").isEqualTo(100L);
    }
}
