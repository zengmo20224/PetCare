package com.petcare.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for admin rejecting a booking.
 */
public record BookingRejectRequest(
        @NotBlank(message = "拒绝原因不能为空")
        @Size(max = 255, message = "拒绝原因不能超过 255 字符")
        String reason
) {
}
