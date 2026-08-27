package com.petcare.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SqlLikeUtils} 单测（2026-08-23 审计 N5/L6 收口守卫）。
 */
class SqlLikeUtilsTest {

    @Test
    @DisplayName("转义 % _ \\ 三个 LIKE 特殊字符")
    void escapesWildcardsAndBackslash() {
        assertThat(SqlLikeUtils.escape("100%")).isEqualTo("100\\%");
        assertThat(SqlLikeUtils.escape("a_b")).isEqualTo("a\\_b");
        assertThat(SqlLikeUtils.escape("a\\b")).isEqualTo("a\\\\b");
        assertThat(SqlLikeUtils.escape("%_\\")).isEqualTo("\\%\\_\\\\");
    }

    @Test
    @DisplayName("普通字符与中文原样保留")
    void normalTextUntouched() {
        assertThat(SqlLikeUtils.escape("13800000000")).isEqualTo("13800000000");
        assertThat(SqlLikeUtils.escape("猫咪洗澡服务")).isEqualTo("猫咪洗澡服务");
        assertThat(SqlLikeUtils.escape("a-b c.d")).isEqualTo("a-b c.d");
    }

    @Test
    @DisplayName("null 与空串原样返回")
    void nullAndEmptyPassthrough() {
        assertThat(SqlLikeUtils.escape(null)).isNull();
        assertThat(SqlLikeUtils.escape("")).isEmpty();
    }
}
