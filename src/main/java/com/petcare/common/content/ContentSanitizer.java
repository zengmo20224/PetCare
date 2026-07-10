package com.petcare.common.content;

import org.springframework.web.util.HtmlUtils;

/**
 * 用户自由文本内容净化工具（M3 安全修复：防存储型 XSS）。
 *
 * <p>背景：公告、社区等内容若原样存储并在前端用 {@code v-html} 渲染，
 * 攻击者可注入 {@code <script>} / {@code onerror=} 等执行脚本。
 *
 * <p>策略：公告内容为纯文本（非富文本），采用 HTML 转义净化——
 * 将 {@code < > & " '} 转为实体，使浏览器以文本形式显示标签字面量，
 * 而非解释为 HTML。这样既保留了内容可见性，又彻底消除 XSS 执行链。
 *
 * <p>未来若需要支持富文本（加粗/图片），应改用 OWASP Java HTML Sanitizer
 * 的白名单策略，而不是此处的全转义。
 */
public final class ContentSanitizer {

    private ContentSanitizer() {
    }

    /**
     * 对纯文本内容做 HTML 转义。null 返回 null（不改变字段语义）。
     *
     * @param raw 原始用户输入
     * @return 转义后的安全文本，可直接存储与渲染
     */
    public static String escapePlainText(String raw) {
        if (raw == null) {
            return null;
        }
        return HtmlUtils.htmlEscape(raw);
    }
}
