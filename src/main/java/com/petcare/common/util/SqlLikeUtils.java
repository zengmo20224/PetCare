package com.petcare.common.util;

/**
 * SQL LIKE 通配符转义工具（2026-08-23 审计 N5/L6 收口）。
 *
 * <p>用户输入中的 {@code %} / {@code _} / {@code \} 在进入 MyBatis-Plus
 * {@code like()} 包装前转义，防止：①匿名搜索输入单字符 {@code %} 触发全表扫描；
 * ②管理端用 {@code _} 逐位枚举手机号片段。全部参数化绑定不变，仅处理通配符语义。</p>
 *
 * <p>MySQL 与 H2(MYSQL mode) 的 LIKE 默认转义符均为反斜杠，无需 ESCAPE 子句。</p>
 */
public final class SqlLikeUtils {

    private SqlLikeUtils() {
    }

    /**
     * 转义 LIKE 模式中的特殊字符；null 原样返回。
     */
    public static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
