package com.petcare.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petcare.ai.agent.audit.AiToolCallLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for {@link AiToolCallLog}（phase17 表，CI-DB-019）。
 * <p>
 * 放在 {@code com.petcare.ai.mapper} 包以被 {@code @MapperScan("com.petcare.**.mapper")} 扫描。
 * 仅 insert，无 update/delete（审计日志只追加）。
 */
@Mapper
public interface AiToolCallLogMapper extends BaseMapper<AiToolCallLog> {
}
