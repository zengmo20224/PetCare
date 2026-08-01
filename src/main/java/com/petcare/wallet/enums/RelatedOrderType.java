package com.petcare.wallet.enums;

import lombok.Getter;

/**
 * Type of business order linked to a wallet transaction, if any.
 * NULL when the transaction is not tied to an order (e.g. recharge, admin adjust).
 */
@Getter
public enum RelatedOrderType {
    PRODUCT_ORDER("PRODUCT_ORDER"),
    SERVICE_BOOKING("SERVICE_BOOKING");

    private final String code;

    RelatedOrderType(String code) {
        this.code = code;
    }
}
