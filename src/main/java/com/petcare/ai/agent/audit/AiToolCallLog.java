package com.petcare.ai.agent.audit;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity for table {@code ai_tool_call_log}（phase17, CI-DB-019）。
 * <p>
 * 不继承 BaseEntity（该表无 update_time / deleted，只追加不修改，对标 ai_usage_log 范式）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("ai_tool_call_log")
public class AiToolCallLog {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联 ai_usage_log.id（一次 Agent 编排的 LLM 调用与 Tool 调用关联）。 */
    @TableField("usage_log_id")
    private Long usageLogId;

    @TableField("agent_type")
    private String agentType;

    @TableField("tool_name")
    private String toolName;

    @TableField("user_id")
    private Long userId;

    @TableField("admin_id")
    private Long adminId;

    /** 入参摘要（脱敏，限 500）。 */
    @TableField("args_summary")
    private String argsSummary;

    /** 出参摘要（脱敏，限 500）。 */
    @TableField("result_summary")
    private String resultSummary;

    @TableField("duration_ms")
    private Integer durationMs;

    @TableField("success")
    private Integer success;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
