package com.petcare.ai.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture boundary test for AI Provider package.
 * Verifies that the provider layer does NOT depend on database, Mapper, DataSource, or MyBatis.
 */
class AiProviderArchitectureTest {

    private static final String PROVIDER_PACKAGE = "com.petcare.ai.provider";

    private static final List<String> FORBIDDEN_DEPENDENCIES = List.of(
            "com.petcare.ai.mapper",
            "com.petcare.common.entity.BaseEntity",
            "javax.sql.DataSource",
            "org.apache.ibatis",
            "com.baomidou.mybatisplus"
    );

    @Test
    @DisplayName("Provider classes do not reference Mapper, DataSource, or MyBatis")
    void providerClasses_noDatabaseDependencies() {
        Class<?>[] providerClasses = {
                AiProviderClient.class,
                AiProviderRequest.class,
                AiProviderResponse.class,
                AiProviderMessage.class,
                AiProviderUsage.class,
                AiProviderException.class,
                AiProviderUnavailableException.class,
                DisabledAiProviderClient.class,
                DeepSeekAiProviderClient.class,
                LangChain4jProviderClient.class,
                AiApiType.class
        };

        for (Class<?> clazz : providerClasses) {
            assertNoForbiddenImports(clazz, clazz.getName());
        }
    }

    @Test
    @DisplayName("AiProviderRequest does not contain SQL, database, or tool fields")
    void providerRequest_noForbiddenFields() {
        // AiProviderRequest is a record — check its components
        List<String> fieldNames = Arrays.stream(AiProviderRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertFalse(fieldNames.contains("sql"), "Must not contain 'sql' field");
        assertFalse(fieldNames.contains("dataSource"), "Must not contain 'dataSource' field");
        assertFalse(fieldNames.contains("apiKey"), "Must not contain 'apiKey' field");
        assertFalse(fieldNames.contains("baseUrl"), "Must not contain 'baseUrl' field");
        assertFalse(fieldNames.contains("tools"), "Must not contain 'tools' field");
        assertFalse(fieldNames.contains("userId"), "Must not contain 'userId' field");
        assertFalse(fieldNames.contains("adminId"), "Must not contain 'adminId' field");
        assertFalse(fieldNames.contains("modelName"), "Must not contain 'modelName' field (business code cannot specify model)");
    }

    @Test
    @DisplayName("AiProviderResponse does not expose raw error or headers")
    void providerResponse_noSensitiveFields() {
        List<String> fieldNames = Arrays.stream(AiProviderResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertFalse(fieldNames.contains("rawError"), "Must not expose raw error");
        assertFalse(fieldNames.contains("rawResponse"), "Must not expose raw response");
        assertFalse(fieldNames.contains("headers"), "Must not expose headers");
        assertFalse(fieldNames.contains("apiKey"), "Must not expose API key");
    }

    private void assertNoForbiddenImports(Class<?> clazz, String className) {
        // Check that the class package is in the provider package
        String packageName = clazz.getPackageName();
        assertTrue(packageName.startsWith(PROVIDER_PACKAGE),
                className + " should be in " + PROVIDER_PACKAGE);

        // Check declared fields
        for (Field field : clazz.getDeclaredFields()) {
            String fieldTypeName = field.getType().getName();
            for (String forbidden : FORBIDDEN_DEPENDENCIES) {
                assertFalse(fieldTypeName.startsWith(forbidden),
                        className + "." + field.getName() + " has forbidden dependency: " + forbidden);
            }
        }

        // Check method return types and parameters
        for (Method method : clazz.getDeclaredMethods()) {
            String returnTypeName = method.getReturnType().getName();
            for (String forbidden : FORBIDDEN_DEPENDENCIES) {
                assertFalse(returnTypeName.startsWith(forbidden),
                        className + "." + method.getName() + "() returns forbidden type: " + forbidden);
            }
        }
    }
}
