package com.petcare.common.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.petcare.ai.entity.AiAnalysisReport;
import com.petcare.ai.entity.AiConversation;
import com.petcare.ai.entity.AiMessage;
import com.petcare.ai.entity.AiUsageLog;
import com.petcare.ai.entity.FaqKnowledge;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class MapperAndServiceCoverageTest {

    /**
     * AI 模块（AGENTS.md §2：禁用占位）的实体的应用服务直接注入 Mapper，
     * 不经过 IService。其空 IService 实现已作为冗余脚手架删除（见 docs/test-archive/），
     * 因此不参与 "每个实体必须有 IService Bean" 的断言。
     * <p>
     * M8.1 新增 {@code AiToolCallLog}：审计日志由 {@code AiToolCallLogService} 直接 insert，
     * 只追加不修改，不经 IService（对标 AiUsageLog 范式）。
     */
    private static final Set<Class<?>> ENTITIES_WITHOUT_ISERVICE = Set.of(
            AiConversation.class, AiMessage.class, AiUsageLog.class,
            AiAnalysisReport.class,
            com.petcare.ai.agent.audit.AiToolCallLog.class
    );

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void everyEntityHasLoadedMapperBean() {
        Set<Class<?>> expectedEntityTypes = PersistenceContractSupport.entityTypes();
        Set<Class<?>> mapperEntityTypes = Arrays.stream(applicationContext.getBeanNamesForType(BaseMapper.class))
                .map(name -> applicationContext.getBean(name))
                .map(bean -> resolveEntityType(AopUtils.getTargetClass(bean), BaseMapper.class))
                .collect(Collectors.toSet());

        assertEquals(expectedEntityTypes, mapperEntityTypes);
    }

    @Test
    void everyEntityHasLoadedCrudServiceBean() {
        Set<Class<?>> expectedEntityTypes = PersistenceContractSupport.entityTypes().stream()
                .filter(t -> !ENTITIES_WITHOUT_ISERVICE.contains(t))
                .collect(Collectors.toSet());
        Set<Class<?>> serviceEntityTypes = Arrays.stream(applicationContext.getBeanNamesForType(IService.class))
                .map(name -> applicationContext.getBean(name))
                .map(bean -> resolveEntityType(AopUtils.getTargetClass(bean), IService.class))
                .collect(Collectors.toSet());

        assertEquals(expectedEntityTypes, serviceEntityTypes);
    }

    private static Class<?> resolveEntityType(Class<?> beanType, Class<?> genericBaseType) {
        Class<?> entityType = ResolvableType.forClass(beanType)
                .as(genericBaseType)
                .getGeneric(0)
                .resolve();

        if (entityType != null) {
            return entityType;
        }

        for (Class<?> interfaceType : beanType.getInterfaces()) {
            entityType = ResolvableType.forClass(interfaceType)
                    .as(genericBaseType)
                    .getGeneric(0)
                    .resolve();
            if (entityType != null) {
                return entityType;
            }
        }

        throw new AssertionError("Cannot resolve entity type for " + beanType.getName());
    }
}
