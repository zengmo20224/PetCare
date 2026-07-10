package com.petcare.common.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ImageMagicBytesValidator} (M1 安全修复)。
 *
 * <p>覆盖：各图片格式真实魔数应通过；伪造 Content-Type（SVG/HTML 字节冒充 JPEG）
 * 应被拒；不支持的 MIME 应被拒；空输入应被拒。
 */
class ImageMagicBytesValidatorTest {

    @Test
    @DisplayName("真实 JPEG 魔数 (FF D8 FF) 通过校验")
    void realJpeg_passes() {
        byte[] head = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x10, 0x00};
        assertThat(ImageMagicBytesValidator.matches("image/jpeg", head)).isTrue();
    }

    @Test
    @DisplayName("真实 PNG 魔数 (89 50 4E 47 0D 0A 1A 0A) 通过校验")
    void realPng_passes() {
        byte[] head = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x0D, 0x0A, 0x00, 0x00};
        assertThat(ImageMagicBytesValidator.matches("image/png", head)).isTrue();
    }

    @Test
    @DisplayName("真实 GIF87a 魔数通过校验")
    void realGif87a_passes() {
        byte[] head = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61, 0x10, 0x00};
        assertThat(ImageMagicBytesValidator.matches("image/gif", head)).isTrue();
    }

    @Test
    @DisplayName("真实 GIF89a 魔数通过校验")
    void realGif89a_passes() {
        byte[] head = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x10, 0x00};
        assertThat(ImageMagicBytesValidator.matches("image/gif", head)).isTrue();
    }

    @Test
    @DisplayName("真实 WebP 魔数 (RIFF....WEBP) 通过校验")
    void realWebp_passes() {
        byte[] head = {0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
        assertThat(ImageMagicBytesValidator.matches("image/webp", head)).isTrue();
    }

    @Test
    @DisplayName("攻击场景：SVG/HTML 字节冒充 image/jpeg 应被拒（M1 核心修复）")
    void svgBytesClaimingJpeg_rejected() {
        // <svg onload=alert(1)> 的 ASCII 头
        byte[] svgHead = "<svg onload=".getBytes();
        assertThat(ImageMagicBytesValidator.matches("image/jpeg", svgHead)).isFalse();
    }

    @Test
    @DisplayName("攻击场景：HTML <script> 字节冒充 image/png 应被拒")
    void htmlBytesClaimingPng_rejected() {
        byte[] htmlHead = "<script>alert(1)</script>".getBytes();
        assertThat(ImageMagicBytesValidator.matches("image/png", htmlHead)).isFalse();
    }

    @Test
    @DisplayName("攻击场景：JPEG 字节冒充 image/png（类型错配）应被拒")
    void jpegBytesClaimingPng_rejected() {
        byte[] jpegHead = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        assertThat(ImageMagicBytesValidator.matches("image/png", jpegHead)).isFalse();
    }

    @Test
    @DisplayName("不支持的 MIME（如 image/svg+xml）应被拒")
    void unsupportedMime_rejected() {
        byte[] svgHead = "<svg>".getBytes();
        assertThat(ImageMagicBytesValidator.matches("image/svg+xml", svgHead)).isFalse();
    }

    @Test
    @DisplayName("null 输入安全返回 false")
    void nullInputs_rejected() {
        assertThat(ImageMagicBytesValidator.matches(null, new byte[3])).isFalse();
        assertThat(ImageMagicBytesValidator.matches("image/jpeg", null)).isFalse();
    }

    @Test
    @DisplayName("字节头不足（如仅 2 字节 JPEG 头）应被拒")
    void tooShortBytes_rejected() {
        byte[] tooShort = {(byte) 0xFF, (byte) 0xD8};  // JPEG 需要 3 字节
        assertThat(ImageMagicBytesValidator.matches("image/jpeg", tooShort)).isFalse();
    }
}
