package com.petcare.common.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ContentSanitizer} (M3 防存储型 XSS)。
 *
 * <p>覆盖：典型 XSS payload 应被转义为不可执行文本；null 安全；正常文本保留可读性。
 */
class ContentSanitizerTest {

    @Test
    @DisplayName("XSS payload <img onerror=alert> 被转义为不可执行文本（标签中和）")
    void imgOnerrorPayload_isEscaped() {
        String payload = "<img src=x onerror=alert(document.cookie)>";
        String escaped = ContentSanitizer.escapePlainText(payload);
        // 安全边界：<img 不再作为标签起始被浏览器解析
        assertThat(escaped).doesNotContain("<img");
        assertThat(escaped).contains("&lt;img");
        // onerror 文本可能保留，但因 < 已转义，无法形成有效标签 → 不可执行
    }

    @Test
    @DisplayName("XSS payload <script> 标签被转义")
    void scriptTagPayload_isEscaped() {
        String payload = "<script>alert('xss')</script>";
        String escaped = ContentSanitizer.escapePlainText(payload);
        assertThat(escaped).doesNotContain("<script");
        assertThat(escaped).contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("SVG onload 事件载荷被转义")
    void svgOnloadPayload_isEscaped() {
        String payload = "<svg onload=alert(1)>";
        String escaped = ContentSanitizer.escapePlainText(payload);
        assertThat(escaped).doesNotContain("<svg");
        assertThat(escaped).contains("&lt;svg");
    }

    @Test
    @DisplayName("正常文本（无 HTML）转义后保持可读")
    void plainText_remainsReadable() {
        String text = "本周六门店有宠物义诊活动，欢迎参加！";
        String escaped = ContentSanitizer.escapePlainText(text);
        assertThat(escaped).isEqualTo(text);
    }

    @Test
    @DisplayName("含合法尖括号的文本被转义为字面量（安全显示）")
    void textWithAngleBrackets_escapedToEntities() {
        String text = "价格 < 100 元 且 > 50 元";
        String escaped = ContentSanitizer.escapePlainText(text);
        assertThat(escaped).contains("&lt;").contains("&gt;");
        assertThat(escaped).doesNotContain("< 100");
    }

    @Test
    @DisplayName("null 输入返回 null（不改变字段语义）")
    void nullInput_returnsNull() {
        assertThat(ContentSanitizer.escapePlainText(null)).isNull();
    }

    @Test
    @DisplayName("javascript: URI 伪协议在属性场景下也被中和（引号转义）")
    void javascriptUriQuotesEscaped() {
        String payload = "<a href=\"javascript:alert(1)\">点我</a>";
        String escaped = ContentSanitizer.escapePlainText(payload);
        // 引号被转义为 &quot;，href 属性无法形成有效语法
        assertThat(escaped).contains("&quot;");
        assertThat(escaped).doesNotContain("\"javascript");
    }
}
