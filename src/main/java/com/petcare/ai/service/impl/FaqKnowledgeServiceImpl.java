package com.petcare.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petcare.ai.entity.FaqKnowledge;
import com.petcare.ai.mapper.FaqKnowledgeMapper;
import com.petcare.ai.service.FaqKnowledgeService;
import org.springframework.stereotype.Service;

/**
 * FAQ 知识库 Service 实现（V2 AI Agent，D-013）。
 * <p>
 * 标准 MyBatis-Plus {@link ServiceImpl}，仅提供 IService 默认能力（list/getById 等），
 * 供 {@code ai.rag.KnowledgeIndexingService} 只读拉取 FAQ。本类在 {@code ai.service.impl} 包，
 * <b>不在</b> {@code ai.rag} 包——守卫约束的是 rag 包不依赖 Mapper，Service 实现依赖 Mapper 合法。
 */
@Service
public class FaqKnowledgeServiceImpl extends ServiceImpl<FaqKnowledgeMapper, FaqKnowledge>
        implements FaqKnowledgeService {
}
