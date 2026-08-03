package com.petcare.ai.rag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * V2 RAG 包架构守卫（D-013，docs/09 §10.2）。
 * <p>
 * 强制约束（对标 V1 {@code AiProviderArchitectureTest}）：
 * <ol>
 *   <li>{@code com.petcare.ai.rag} 包的类不得依赖 {@code *Mapper} / MyBatis / DataSource —— RAG 访问业务数据
 *       只通过业务 Service 接口（docs/09 §2 B1）。</li>
 *   <li>{@code ai.rag} 包不得反向依赖 {@code ai.provider}（provider 不知道 RAG 存在，单向依赖）。</li>
 * </ol>
 * <p>
 * 例外：{@code ai.rag} 可以依赖业务 Service 接口（{@code com.petcare.**.service}）和 langchain4j 类型。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiRagArchitectureTest {

    private static final String RAG_PACKAGE = "com.petcare.ai.rag";

    /** rag 包禁止依赖的前缀（Mapper / MyBatis / DataSource）。 */
    private static final List<String> FORBIDDEN_DEPENDENCIES = List.of(
            "com.petcare.ai.mapper",
            "com.petcare.**.mapper",      // 通配防御（实际用前缀匹配）
            "com.petcare.ai.provider",     // 反向依赖禁止
            "javax.sql.DataSource",
            "org.apache.ibatis",
            "com.baomidou.mybatisplus"
    );

    /** 被检的 rag 包核心类（新增类时手动加入，对标 V1 守卫范式）。 */
    private static final List<Class<?>> RAG_CLASSES = List.of(
            KnowledgeSource.class,
            KnowledgeIndexingService.class,
            RagRetrievalService.class,
            KnowledgeIndexingScheduler.class
    );

    @Test
    @DisplayName("RAG classes do not reference Mapper, DataSource, MyBatis, or provider package")
    void ragClasses_noForbiddenDependencies() {
        for (Class<?> clazz : RAG_CLASSES) {
            assertRagPackage(clazz);
            assertNoForbiddenFields(clazz);
            assertNoForbiddenReturnTypes(clazz);
        }
    }

    private void assertRagPackage(Class<?> clazz) {
        // KnowledgeSource 等可能不在严格 rag 包（嵌套），但顶层包名应以 ai.rag 开头
        assertTrue(clazz.getPackageName().startsWith(RAG_PACKAGE),
                clazz.getName() + " should be in " + RAG_PACKAGE);
    }

    private void assertNoForbiddenFields(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            String fieldTypeName = field.getType().getName();
            for (String forbidden : FORBIDDEN_DEPENDENCIES) {
                assertFalse(matchesPrefix(fieldTypeName, forbidden),
                        clazz.getName() + "." + field.getName() + " has forbidden dependency: " + forbidden);
            }
        }
    }

    private void assertNoForbiddenReturnTypes(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            String returnTypeName = method.getReturnType().getName();
            for (String forbidden : FORBIDDEN_DEPENDENCIES) {
                assertFalse(matchesPrefix(returnTypeName, forbidden),
                        clazz.getName() + "." + method.getName() + "() returns forbidden type: " + forbidden);
            }
        }
    }

    /**
     * 前缀匹配（支持 com.petcare.**.mapper 通配——含 ** 的用 contains 语义，其余用 startsWith）。
     */
    private boolean matchesPrefix(String typeName, String forbidden) {
        if (forbidden.contains("**")) {
            // 通配：取最后一段（如 mapper），用 endsWith 判断包路径段
            String lastSegment = forbidden.substring(forbidden.lastIndexOf('.') + 1);
            return typeName.contains("." + lastSegment + ".") || typeName.endsWith("." + lastSegment);
        }
        return typeName.startsWith(forbidden);
    }
}
