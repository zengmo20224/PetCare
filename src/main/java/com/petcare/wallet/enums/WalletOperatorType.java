package com.petcare.wallet.enums;

import lombok.Getter;

/**
 * Who initiated the wallet transaction.
 * USER: end-user action (e.g. paying for an order).
 * ADMIN: admin manual operation (recharge/adjust via admin console).
 * SYSTEM: automatic action (e.g. scheduled refund, future bonus rule).
 */
@Getter
public enum WalletOperatorType {
    USER("USER"),
    ADMIN("ADMIN"),
    SYSTEM("SYSTEM");

    private final String code;

    WalletOperatorType(String code) {
        this.code = code;
    }
}
