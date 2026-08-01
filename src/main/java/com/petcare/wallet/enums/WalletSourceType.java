package com.petcare.wallet.enums;

import lombok.Getter;

/**
 * Wallet transaction source type.
 * BONUS is reserved for future marketing promotions (e.g. "recharge 100 get 20 free")
 * per D-010/D-012 boundary; current code does not write BONUS records.
 */
@Getter
public enum WalletSourceType {
    RECHARGE("RECHARGE"),
    PAY("PAY"),
    REFUND("REFUND"),
    ADMIN_ADJUST("ADMIN_ADJUST"),
    BONUS("BONUS");

    private final String code;

    WalletSourceType(String code) {
        this.code = code;
    }
}
