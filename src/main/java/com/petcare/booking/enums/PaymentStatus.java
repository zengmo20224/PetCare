package com.petcare.booking.enums;

import lombok.Getter;

/**
 * Payment status constants.
 * Default: UNPAID (matches schema.sql `service_booking.payment_status` default)
 */
@Getter
public enum PaymentStatus {
    UNPAID("UNPAID"),
    OFFLINE_PAID("OFFLINE_PAID"),
    WALLET_PAID("WALLET_PAID"),
    REFUNDED("REFUNDED");

    private final String code;

    PaymentStatus(String code) {
        this.code = code;
    }
}
