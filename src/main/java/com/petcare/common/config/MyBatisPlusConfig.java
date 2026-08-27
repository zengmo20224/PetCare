package com.petcare.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * MyBatis-Plus configuration.
 * Enables pagination plugin, mapper scanning, and sets global field defaults.
 */
@Configuration
@MapperScan("com.petcare.**.mapper")
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 2026-08-23 审计 M2：全局分页 size 上限，兜底所有未显式钳制的列表端点，
        // 防止 ?size=100000 拖库（含匿名可达的 /api/v1/posts、/api/v1/products）。
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /**
     * Provides database product name as databaseId for MyBatis mapper XML.
     * Allows using databaseId attribute to write dialect-specific SQL.
     * MySQL → "mysql", H2 → "H2".
     */
    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties props = new Properties();
        props.setProperty("MySQL", "mysql");
        props.setProperty("H2", "H2");
        provider.setProperties(props);
        return provider;
    }
}
