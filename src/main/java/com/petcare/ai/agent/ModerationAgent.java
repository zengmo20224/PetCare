package com.petcare.ai.agent;

import com.petcare.ai.domain.AiOutputSafetyPolicy;
import com.petcare.ai.provider.AiApiType;
import com.petcare.ai.provider.AiProviderClient;
import com.petcare.ai.provider.AiProviderException;
import com.petcare.ai.provider.AiProviderMessage;
import com.petcare.ai.provider.AiProviderRequest;
import com.petcare.ai.provider.AiProviderResponse;
import com.petcare.ai.provider.AiProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本内容审核 Agent（V2，对标 docs/09 §5.3.2）。
 *
 * <p><b>定位</b>：辅助现有敏感词规则（{@code ContentModerationPolicy}），不替代——
 * 敏感词规则在前（同步、拦截违规内容），本 Agent 在后（异步、产出 AI 建议）。</p>
 *
 * <p><b>输出契约</b>：verdict（VIOLATION / SUSPICIOUS / NORMAL）+ type
 * （SPAM 广告 / ABUSE 辱骂 / ILLEGAL 医疗误导等违法 / OTHER）+ confidence（0-1）。
 * 由调用方决定处置：超阈值的 VIOLATION 产 {@code PostReport} 进人工队列，
 * <b>本类绝不直接删帖/改状态</b>（B4）。</p>
 *
 * <p><b>容错</b>：LLM 输出解析失败/Provider 异常一律返回 {@code NORMAL}（fail-open，
 * 审核建议缺失不阻塞业务——敏感词规则仍在前置生效）。</p>
 */
public class ModerationAgent {

    private static final Logger log = LoggerFactory.getLogger(ModerationAgent.class);

    private static final Pattern VERDICT_PATTERN = Pattern.compile(
            "\"verdict\"\\s*:\\s*\"(VIOLATION|SUSPICIOUS|NORMAL)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE_PATTERN = Pattern.compile(
            "\"type\"\\s*:\\s*\"(SPAM|ABUSE|ILLEGAL|OTHER)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile(
            "\"confidence\"\\s*:\\s*([0-9.]+)");

    private final AiProviderClient providerClient;

    public ModerationAgent(AiProviderClient providerClient) {
        this.providerClient = providerClient;
    }

    /**
     * 对一段社区内容做 AI 分类。
     *
     * @param contentType 内容类型标记（POST / COMMENT，仅用于日志与审计）
     * @param content     待审核文本
     * @return 分类结果；任何失败都返回 NORMAL（fail-open）
     */
    public ModerationVerdict classify(String contentType, String content) {
        if (content == null || content.isBlank()) {
            return ModerationVerdict.NORMAL;
        }
        try {
            AiProviderResponse response = providerClient.complete(
                    new AiProviderRequest(AiApiType.MODERATION,
                            buildMessages(contentType, content), null));
            return parseVerdict(response.assistantText());
        } catch (AiProviderException e) {
            // fail-open：审核建议缺失不阻塞发帖（敏感词规则仍在前置生效）。
            // AiProviderUnavailableException 是其子类，一并覆盖
            log.warn("[AI] Moderation classify failed (fail-open to NORMAL): {}", e.getMessage());
            return ModerationVerdict.NORMAL;
        } catch (Exception e) {
            log.warn("[AI] Moderation classify unexpected error (fail-open to NORMAL): {}", e.getMessage());
            return ModerationVerdict.NORMAL;
        }
    }

    private List<AiProviderMessage> buildMessages(String contentType, String content) {
        List<AiProviderMessage> messages = new ArrayList<>();
        messages.add(new AiProviderMessage("system", """
                你是宠物社区的内容审核助手。对用户发布的内容做分类判断，只输出一个 JSON 对象，不要输出其他任何文字。
                格式：{"verdict":"VIOLATION|SUSPICIOUS|NORMAL","type":"SPAM|ABUSE|ILLEGAL|OTHER","confidence":0.0到1.0}
                判定标准：
                - VIOLATION：明确的广告推销、人身辱骂攻击、违法信息或医疗误导（如推荐处方药、承诺治愈）
                - SUSPICIOUS：疑似但不确定的上述内容
                - NORMAL：正常社区内容（包括宠物健康讨论，只要不涉及用药推荐/治疗承诺）
                type 仅在 verdict 非 NORMAL 时有意义；正常养宠交流、商品使用心得不算广告。
                """));
        messages.add(new AiProviderMessage("user",
                "内容类型：" + contentType + "\n待审核内容：\n" + content));
        return messages;
    }

    /**
     * 从 LLM 输出解析判定。输出可能被 markdown 代码块包裹或夹杂说明文字，
     * 用正则宽松提取；解析不到关键字段按 NORMAL 处理（fail-open）。
     */
    ModerationVerdict parseVerdict(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return ModerationVerdict.NORMAL;
        }
        // 输出护栏一致性：审核输出本身不得包含注入内容（防把护栏词带进审核建议）
        if (AiOutputSafetyPolicy.isUnsafe(llmOutput)) {
            return ModerationVerdict.NORMAL;
        }
        Matcher verdict = VERDICT_PATTERN.matcher(llmOutput);
        if (!verdict.find()) {
            return ModerationVerdict.NORMAL;
        }
        String verdictValue = verdict.group(1).toUpperCase();

        String typeValue = "OTHER";
        Matcher type = TYPE_PATTERN.matcher(llmOutput);
        if (type.find()) {
            typeValue = type.group(1).toUpperCase();
        }

        double confidence = 0.5;
        Matcher conf = CONFIDENCE_PATTERN.matcher(llmOutput);
        if (conf.find()) {
            try {
                confidence = Math.min(1.0, Math.max(0.0, Double.parseDouble(conf.group(1))));
            } catch (NumberFormatException ignored) {
                // 保留默认 0.5
            }
        }
        return new ModerationVerdict(verdictValue, typeValue, confidence);
    }

    /**
     * 审核判定（不可变）。verdict=NORMAL 时 type/confidence 无业务意义。
     */
    public record ModerationVerdict(String verdict, String type, double confidence) {

        public static final ModerationVerdict NORMAL = new ModerationVerdict("NORMAL", "OTHER", 0.0);
        public static final String VERDICT_VIOLATION = "VIOLATION";
        public static final String VERDICT_SUSPICIOUS = "SUSPICIOUS";

        public boolean isViolation() {
            return VERDICT_VIOLATION.equals(verdict);
        }

        public boolean isSuspicious() {
            return VERDICT_SUSPICIOUS.equals(verdict);
        }
    }
}
