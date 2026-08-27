package com.petcare.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request DTO for creating a booking.
 * userId is NOT accepted from the request body — it comes from the security context.
 *
 * <p>2026-08-23 审计 M8：自由文本/短枚举字段补充长度与格式约束，
 * 防止超长输入触发 DB 列溢出 → 稳定 500。</p>
 */
public record BookingCreateRequest(

        @NotNull(message = "门店ID不能为空")
        Long storeId,

        @NotNull(message = "服务项目ID不能为空")
        Long serviceItemId,

        Long petId,

        @NotBlank(message = "服务模式不能为空")
        @Size(max = 32, message = "服务模式长度不能超过 32")
        String serviceMode,

        @NotNull(message = "预约日期不能为空")
        LocalDate bookingDate,

        @NotNull(message = "开始时间不能为空")
        LocalTime startTime,

        Long addressId,

        @NotBlank(message = "联系人姓名不能为空")
        @Size(max = 64, message = "联系人姓名不能超过 64 字符")
        String contactName,

        @NotBlank(message = "联系电话不能为空")
        @Pattern(regexp = "^[0-9+\\-() ]{5,20}$", message = "联系电话格式不正确")
        String contactPhone,

        @NotBlank(message = "付款方式不能为空")
        @Size(max = 32, message = "付款方式长度不能超过 32")
        String paymentMethod,

        @Size(max = 500, message = "备注不能超过 500 字符")
        String remark
) {
}
