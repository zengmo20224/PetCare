package com.petcare.wallet.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * Wallet transaction direction.
 * DEBIT: balance decreases (payment, admin deduction).
 * CREDIT: balance increases (recharge, refund, admin top-up, bonus).
 */
@Getter
public enum WalletDirection {
    DEBIT("DEBIT"),
    CREDIT("CREDIT");

    private final String code;

    WalletDirection(String code) {
        this.code = code;
    }

    public static WalletDirection getByCode(String code) {
        return Arrays.stream(values())
                .filter(d -> d.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }
}

