package com.petcare.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端取消预约请求 DTO。
 * 与用户端 {@link BookingCancelRequest}（reason 可选）不同，
 * 管理端取消必须填写原因以留痕（管理员有取消权限，但需记录取消理由）。
 */
public record AdminBookingCancelRequest(
        @NotBlank(message = "取消原因不能为空")
        @Size(max = 500, message = "取消原因最长 500 字符")
        String reason
) {
}
