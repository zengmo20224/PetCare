package com.petcare.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.ai.agent.AnalyticsAgent;
import com.petcare.ai.analytics.BusinessAnalyticsAggregator;
import com.petcare.ai.analytics.CommunityAnalyticsAggregator;
import com.petcare.ai.analytics.SalesAnalyticsAggregator;
import com.petcare.ai.analytics.ActivityAnalyticsAggregator;
import com.petcare.ai.domain.AiOutputSafetyPolicy;
import com.petcare.ai.domain.PromptFactory;
import com.petcare.ai.dto.AiAnalysisCreateRequest;
import com.petcare.ai.dto.AiAnalysisReportResponse;
import com.petcare.ai.entity.AiAnalysisReport;
import com.petcare.ai.entity.AiUsageLog;
import com.petcare.ai.mapper.AiAnalysisReportMapper;
import com.petcare.ai.mapper.AiUsageLogMapper;
import com.petcare.ai.provider.*;
import com.petcare.ai.service.AiAnalysisApplicationService;
import com.petcare.admin.entity.AdminOperationLog;
import com.petcare.admin.service.AdminOperationLogService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of admin AI analysis application service.
 * Only uses backend-aggregated data — never lets AI generate SQL or query the database directly.
 */
@Service
public class AiAnalysisApplicationServiceImpl implements AiAnalysisApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisApplicationServiceImpl.class);

    /**
     * Pattern to extract a "suggestions" or "recommendations" section from the AI response text.
     * Looks for headings like "## 建议", "### 建议", "## 建议/改进", "Recommendations:", etc.
     */
    private static final Pattern SUGGESTIONS_PATTERN = Pattern.compile(
            "(?:^|\\n)(?:#{1,4}\\s*(?:建议|改进|建议/改进|建议与改进|recommendations?|suggestions?)[:\\s]*)\\s*\\n([\\s\\S]+)",
            Pattern.CASE_INSENSITIVE
    );

    private final AiAnalysisReportMapper reportMapper;
    private final AiUsageLogMapper usageLogMapper;
    private final AdminOperationLogService adminOperationLogService;
    private final AiProviderClient providerClient;
    private final BusinessAnalyticsAggregator businessAnalyticsAggregator;
    private final CommunityAnalyticsAggregator communityAnalyticsAggregator;
    private final SalesAnalyticsAggregator salesAnalyticsAggregator;
    private final ActivityAnalyticsAggregator activityAnalyticsAggregator;
    private final ObjectMapper objectMapper;
    /** M8.2：经营分析 Agent（可选，agent-enabled=false 时为 null，走 V1 全量塞降级路径）。 */
    private final AnalyticsAgent analyticsAgent;

    public AiAnalysisApplicationServiceImpl(
            AiAnalysisReportMapper reportMapper,
            AiUsageLogMapper usageLogMapper,
            AdminOperationLogService adminOperationLogService,
            AiProviderClient providerClient,
            BusinessAnalyticsAggregator businessAnalyticsAggregator,
            CommunityAnalyticsAggregator communityAnalyticsAggregator,
            SalesAnalyticsAggregator salesAnalyticsAggregator,
            ActivityAnalyticsAggregator activityAnalyticsAggregator,
            ObjectMapper objectMapper,
            ObjectProvider<AnalyticsAgent> analyticsAgentProvider) {
        this.reportMapper = reportMapper;
        this.usageLogMapper = usageLogMapper;
        this.adminOperationLogService = adminOperationLogService;
        this.providerClient = providerClient;
        this.businessAnalyticsAggregator = businessAnalyticsAggregator;
        this.communityAnalyticsAggregator = communityAnalyticsAggregator;
        this.salesAnalyticsAggregator = salesAnalyticsAggregator;
        this.activityAnalyticsAggregator = activityAnalyticsAggregator;
        this.objectMapper = objectMapper;
        // M8.2：ObjectProvider 可选注入，agent-enabled=false 时 getIfUnique 返回 null
        this.analyticsAgent = analyticsAgentProvider.getIfUnique();
    }

    @Override
    public AiAnalysisReportResponse generateReport(Long adminId, AiAnalysisCreateRequest request) {
        validateDateRange(request.startDate(), request.endDate());

        // Step 1: Aggregate overview data from backend (read-only parameterized queries, no transaction needed)
        String overviewDataJson = aggregateData(request.reportType(), request.startDate(), request.endDate());

        if (overviewDataJson == null) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_DATA_INSUFFICIENT, "数据不足，无法生成分析报告");
        }

        // M8.2：agent-enabled=true 时走 Agent（下钻 + 工具协议），否则走 V1 全量塞 prompt 路径。
        // 两条路径共用：聚合数据 → 输出护栏 → 持久化 → 审计；差异只在 LLM 调用方式。
        String output;
        AiProviderResponse response;
        if (analyticsAgent != null) {
            try {
                AnalyticsAgent.AgentReply reply = analyticsAgent.handle(adminId, request, overviewDataJson);
                output = reply.text();
                response = reply.usageResponse();
            } catch (AiProviderUnavailableException e) {
                logFailedUsage(adminId, "agent_provider_unavailable");
                logAdminOperation(adminId, "AI分析", "生成报告", "FAIL", e.getMessage());
                throw e;
            } catch (AiProviderException e) {
                logFailedUsage(adminId, e.getInternalCode());
                logAdminOperation(adminId, "AI分析", "生成报告", "FAIL", e.getInternalCode());
                throw e;
            }
        } else {
            // V1 降级路径：全量塞 prompt，无工具调用
            List<AiProviderMessage> messages = PromptFactory.buildAnalysisMessages(
                    request.reportType(), overviewDataJson);
            try {
                response = providerClient.complete(
                        new AiProviderRequest(AiApiType.ANALYSIS, messages, null));
            } catch (AiProviderUnavailableException e) {
                logFailedUsage(adminId, "provider_unavailable");
                logAdminOperation(adminId, "AI分析", "生成报告", "FAIL", e.getMessage());
                throw e;
            } catch (AiProviderException e) {
                logFailedUsage(adminId, e.getInternalCode());
                logAdminOperation(adminId, "AI分析", "生成报告", "FAIL", e.getInternalCode());
                throw e;
            }
            output = response.assistantText();
        }

        // Output safety check (Agent 路径已在 Agent 内过一次，V1 路径在此过；双保险)
        if (AiOutputSafetyPolicy.isUnsafe(output)) {
            logFailedUsage(adminId, "output_unsafe");
            logAdminOperation(adminId, "AI分析", "生成报告", "FAIL", "AI输出未通过安全检查");
            throw new BusinessException(ErrorCode.AI_OUTPUT_REJECTED, "AI 输出未通过安全检查");
        }

        // Persist report in a short transaction (slow provider call has already completed)
        String suggestions = extractSuggestions(output);
        AiAnalysisReportResponse reportResponse = saveReport(adminId, request, overviewDataJson, output, suggestions);

        // Log successful usage with admin attribution
        logSuccessUsage(adminId, response);

        // Log admin operation for audit trail
        logAdminOperation(adminId, "AI分析", "生成报告", "SUCCESS", null);

        return reportResponse;
    }

    @Override
    public PageResponse<AiAnalysisReportResponse> listReports(int page, int size, String reportType) {
        Page<AiAnalysisReport> pageParam = new Page<>(page, size);
        QueryWrapper<AiAnalysisReport> query = new QueryWrapper<AiAnalysisReport>()
                .orderByDesc("create_time");

        if (reportType != null && !reportType.isBlank()) {
            query.eq("report_type", reportType);
        }

        Page<AiAnalysisReport> result = reportMapper.selectPage(pageParam, query);

        List<AiAnalysisReportResponse> items = result.getRecords().stream()
                .map(this::toReportResponse)
                .toList();

        return PageResponse.of(items, result.getTotal(), page, size);
    }

    @Override
    public AiAnalysisReportResponse getReport(Long id) {
        AiAnalysisReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报告不存在");
        }
        return toReportResponse(report);
    }

    /**
     * Saves the analysis report in a short, focused transaction.
     * Only the DB write is transactional — the slow provider call has already completed.
     */
    @Transactional
    protected AiAnalysisReportResponse saveReport(Long adminId, AiAnalysisCreateRequest request,
                                                   String aggregatedDataJson, String aiSummary, String suggestions) {
        AiAnalysisReport report = new AiAnalysisReport();
        report.setReportType(request.reportType());
        report.setStartDate(request.startDate());
        report.setEndDate(request.endDate());
        report.setRawDataJson(aggregatedDataJson);
        report.setAiSummary(aiSummary);
        report.setSuggestions(suggestions);
        report.setCreatedBy(adminId);
        reportMapper.insert(report);
        return toReportResponse(report);
    }

    private String aggregateData(String reportType, LocalDate startDate, LocalDate endDate) {
        try {
            return switch (reportType) {
                case "BUSINESS" -> objectMapper.writeValueAsString(
                        businessAnalyticsAggregator.aggregate(startDate, endDate));
                case "COMMUNITY" -> objectMapper.writeValueAsString(
                        communityAnalyticsAggregator.aggregate(startDate, endDate));
                case "SALES" -> objectMapper.writeValueAsString(
                        salesAnalyticsAggregator.aggregate(startDate, endDate));
                case "ACTIVITY" -> objectMapper.writeValueAsString(
                        activityAnalyticsAggregator.aggregate(startDate, endDate));
                default -> null;
            };
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize aggregated data", e);
            return null;
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_RANGE_INVALID, "日期范围不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_RANGE_INVALID, "开始日期不能晚于结束日期");
        }
        if (endDate.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_RANGE_INVALID, "结束日期不能超过今天");
        }
    }

    /**
     * Extracts the suggestions/recommendations section from the AI response text.
     * Returns the full AI summary text if no distinct suggestions section is found,
     * so that recommendation content is never discarded.
     */
    private String extractSuggestions(String aiSummary) {
        if (aiSummary == null || aiSummary.isBlank()) {
            return "";
        }
        Matcher matcher = SUGGESTIONS_PATTERN.matcher(aiSummary);
        if (matcher.find()) {
            String extracted = matcher.group(1).trim();
            return extracted.isBlank() ? aiSummary : extracted;
        }
        // No dedicated suggestions section — the entire summary contains recommendations
        return aiSummary;
    }

    private void logSuccessUsage(Long adminId, AiProviderResponse response) {
        try {
            AiUsageLog usageLog = new AiUsageLog();
            usageLog.setAdminId(adminId);
            usageLog.setApiType(AiApiType.ANALYSIS.name());
            usageLog.setModelName(response.modelName());
            if (response.usage() != null) {
                usageLog.setPromptTokens(response.usage().promptTokens());
                usageLog.setCompletionTokens(response.usage().completionTokens());
                usageLog.setTotalTokens(response.usage().totalTokens());
            }
            usageLog.setSuccess(1);
            usageLogMapper.insert(usageLog);
        } catch (Exception e) {
            log.warn("Failed to log AI usage: {}", e.getMessage());
        }
    }

    private void logFailedUsage(Long adminId, String errorCode) {
        try {
            AiUsageLog usageLog = new AiUsageLog();
            usageLog.setAdminId(adminId);
            usageLog.setApiType(AiApiType.ANALYSIS.name());
            usageLog.setSuccess(0);
            usageLog.setErrorMessage(errorCode);
            usageLogMapper.insert(usageLog);
        } catch (Exception e) {
            log.warn("Failed to log AI usage: {}", e.getMessage());
        }
    }

    private void logAdminOperation(Long adminId, String module, String operation,
                                    String result, String errorMessage) {
        try {
            AdminOperationLog opLog = new AdminOperationLog();
            opLog.setAdminId(adminId);
            opLog.setModule(module);
            opLog.setOperation(operation);
            opLog.setRequestMethod("POST");
            opLog.setRequestUrl("/api/v1/admin/ai/analysis/reports");
            opLog.setResult(result);
            opLog.setErrorMessage(errorMessage);
            adminOperationLogService.save(opLog);
        } catch (Exception e) {
            log.warn("Failed to log admin operation: {}", e.getMessage());
        }
    }

    private AiAnalysisReportResponse toReportResponse(AiAnalysisReport r) {
        return new AiAnalysisReportResponse(
                r.getId(),
                r.getReportType(),
                r.getStartDate(),
                r.getEndDate(),
                r.getAiSummary(),
                r.getSuggestions(),
                r.getCreatedBy(),
                r.getCreateTime()
        );
    }
}
