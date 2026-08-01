package com.petcare.booking.enums;

import lombok.Getter;

/**
 * Payment method constants.
 * Used for both service booking and product orders.
 */
@Getter
public enum PaymentMethod {
    OFFLINE_STORE("OFFLINE_STORE"),
    OFFLINE_HOME("OFFLINE_HOME"),
    ONLINE_WECHAT("ONLINE_WECHAT"),
    WALLET("WALLET"),
    FREE("FREE");

    private final String code;

    PaymentMethod(String code) {
        this.code = code;
    }
}
