package com.petcare.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.petcare.ai.entity.FaqKnowledge;

/**
 * FAQ 知识库 Service（V2 AI Agent，D-013）。
 * <p>
 * 为 {@code ai.rag.KnowledgeIndexingService} 提供只读查询入口（FAQ 此前只有 Mapper，无 Service）。
 * 用 IService 标准能力（{@code list()}），不暴露 Mapper，符合 ai.rag 包不依赖 Mapper 的边界守卫。
 */
public interface FaqKnowledgeService extends IService<FaqKnowledge> {
}
