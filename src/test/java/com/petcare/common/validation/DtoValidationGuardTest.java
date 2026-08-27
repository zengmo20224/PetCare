package com.petcare.common.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.petcare.booking.dto.BookingCreateRequest;
import com.petcare.booking.dto.BookingRejectRequest;
import com.petcare.community.dto.ReportPostRequest;
import com.petcare.moderation.dto.SensitiveWordCreateRequest;
import com.petcare.product.dto.ProductOrderCreateRequest;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026-08-23 审计 M8/N6 守卫：自由文本与短枚举字段必须有长度/格式约束，
 * 防止超长输入触发 DB 列溢出 → DataIntegrityViolation → 稳定 500。
 */
class DtoValidationGuardTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(n);
    }

    @Test
    @DisplayName("BookingCreateRequest.remark 超过 500 字必须被拒绝（列 VARCHAR(500)）")
    void bookingRemark_overLimit_rejected() {
        BookingCreateRequest req = new BookingCreateRequest(
                1L, 2L, 3L, "STORE_SERVICE", LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), null,
                "张三", "13800000000", "WALLET", repeat('超', 501));

        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("remark"));
    }

    @Test
    @DisplayName("BookingCreateRequest.contactPhone 非法格式必须被拒绝")
    void bookingContactPhone_badFormat_rejected() {
        BookingCreateRequest req = new BookingCreateRequest(
                1L, 2L, 3L, "STORE_SERVICE", LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), null,
                "张三", "not-a-phone!!", "WALLET", null);

        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("contactPhone"));
    }

    @Test
    @DisplayName("BookingCreateRequest 合法输入零违例")
    void bookingValid_noViolations() {
        BookingCreateRequest req = new BookingCreateRequest(
                1L, 2L, 3L, "STORE_SERVICE", LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), null,
                "张三", "13800000000", "WALLET", "备注");

        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    @DisplayName("ProductOrderCreateRequest.remark 超过 500 字必须被拒绝")
    void orderRemark_overLimit_rejected() {
        ProductOrderCreateRequest req = new ProductOrderCreateRequest(
                1L, "PICKUP", null, "张三", "13800000000", repeat('长', 501));

        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("remark"));
    }

    @Test
    @DisplayName("ProductOrderCreateRequest.contactPhone 非法格式必须被拒绝")
    void orderContactPhone_badFormat_rejected() {
        ProductOrderCreateRequest req = new ProductOrderCreateRequest(
                1L, "PICKUP", null, "张三", "abc!!!def", null);

        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("contactPhone"));
    }

    @Test
    @DisplayName("BookingRejectRequest.reason 超过 255 字必须被拒绝（列 VARCHAR(255)）")
    void rejectReason_overLimit_rejected() {
        BookingRejectRequest req = new BookingRejectRequest(repeat('拒', 256));

        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("reason"));
    }

    @Test
    @DisplayName("ReportPostRequest.reasonType 超过 32 字必须被拒绝（列 VARCHAR(32)）")
    void reportReasonType_overLimit_rejected() {
        ReportPostRequest req = new ReportPostRequest(repeat('类', 33), "正常原因");

        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("reasonType"));
    }

    @Test
    @DisplayName("SensitiveWordCreateRequest.category 超过 32 字必须被拒绝（列 VARCHAR(32)）")
    void sensitiveCategory_overLimit_rejected() {
        SensitiveWordCreateRequest req =
                new SensitiveWordCreateRequest("敏感词", repeat('类', 33), 1);

        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("category"));
    }
}
