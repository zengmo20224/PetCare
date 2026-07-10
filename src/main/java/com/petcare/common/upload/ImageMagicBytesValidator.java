package com.petcare.common.upload;

import java.util.Map;

/**
 * 通过文件头魔数（magic bytes）校验图片真实类型（M1 安全修复）。
 *
 * <p>背景：仅信任客户端 {@code Content-Type} 头会被伪造——攻击者可上传含脚本的
 * SVG/HTML，伪造 {@code Content-Type: image/jpeg} 绕过扩展名白名单。本类读取文件
 * 前 12 字节，与各图片格式的固定魔数前缀比对，确保字节真的是声明的格式。
 *
 * <p>支持的格式与魔数：
 * <ul>
 *   <li>JPEG：{@code FF D8 FF}</li>
 *   <li>PNG ：{@code 89 50 4E 47 0D 0A 1A 0A}</li>
 *   <li>GIF ：{@code 47 49 46 38 (37|39) 61}（GIF87a / GIF89a）</li>
 *   <li>WebP：前 4 字节 {@code 52 49 46 46}（RIFF）+ 第 8-11 字节 {@code 57 45 42 50}（WEBP）</li>
 * </ul>
 *
 * <p>不支持 SVG（SVG 是文本格式，可内嵌脚本，禁止作为用户上传图片）。
 */
public final class ImageMagicBytesValidator {

    private ImageMagicBytesValidator() {
    }

    /** 各 MIME 类型对应的魔数校验器。 */
    private static final Map<String, MagicMatcher> MATCHERS = Map.of(
            "image/jpeg", bytes -> bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF,
            "image/png", bytes -> bytes.length >= 8
                    && (bytes[0] & 0xFF) == 0x89
                    && (bytes[1] & 0xFF) == 0x50
                    && (bytes[2] & 0xFF) == 0x4E
                    && (bytes[3] & 0xFF) == 0x47
                    && (bytes[4] & 0xFF) == 0x0D
                    && (bytes[5] & 0xFF) == 0x0A
                    && (bytes[6] & 0xFF) == 0x1A
                    && (bytes[7] & 0xFF) == 0x0A,
            "image/gif", bytes -> bytes.length >= 6
                    && (bytes[0] & 0xFF) == 0x47   // G
                    && (bytes[1] & 0xFF) == 0x49   // I
                    && (bytes[2] & 0xFF) == 0x46   // F
                    && (bytes[3] & 0xFF) == 0x38   // 8
                    && ((bytes[4] & 0xFF) == 0x37  // 7 (GIF87a)
                            || (bytes[4] & 0xFF) == 0x39)  // 9 (GIF89a)
                    && (bytes[5] & 0xFF) == 0x61,  // a
            "image/webp", bytes -> bytes.length >= 12
                    && (bytes[0] & 0xFF) == 0x52   // R
                    && (bytes[1] & 0xFF) == 0x49   // I
                    && (bytes[2] & 0xFF) == 0x46   // F
                    && (bytes[3] & 0xFF) == 0x46   // F
                    && (bytes[8] & 0xFF) == 0x57   // W
                    && (bytes[9] & 0xFF) == 0x45   // E
                    && (bytes[10] & 0xFF) == 0x42  // B
                    && (bytes[11] & 0xFF) == 0x50  // P
    );

    /** 需要读取的最大字节数（覆盖所有受支持格式的最长魔数）。 */
    public static final int MAX_BYTES_NEEDED = 12;

    /**
     * 校验给定字节头是否匹配声明的 MIME 类型。
     *
     * @param contentType 声明的 MIME 类型（来自请求）
     * @param headBytes   文件前 {@value #MAX_BYTES_NEEDED} 字节（不足则传实际长度）
     * @return true 如果字节头匹配该 MIME 的魔数；不支持该 MIME 或不匹配则返回 false
     */
    public static boolean matches(String contentType, byte[] headBytes) {
        if (contentType == null || headBytes == null) {
            return false;
        }
        MagicMatcher matcher = MATCHERS.get(contentType);
        return matcher != null && matcher.matches(headBytes);
    }

    @FunctionalInterface
    private interface MagicMatcher {
        boolean matches(byte[] headBytes);
    }
}
