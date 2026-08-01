package com.petcare.wallet.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.petcare.common.serialization.SnowflakeIdSerializer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Wallet-related DTOs grouped in one holder class to match the project convention
 * (see {@code MarketingActivityDtos}).
 */
public final class WalletDtos {

    private WalletDtos() {
    }

    /** User-facing wallet summary. */
    public record WalletResponse(
            @JsonSerialize(using = SnowflakeIdSerializer.class) Long id,
            @JsonSerialize(using = SnowflakeIdSerializer.class) Long userId,
            BigDecimal balance,
            BigDecimal frozenAmount,
            LocalDateTime updateTime
    ) {
    }

    /** Append-only ledger row (list/detail). */
    public record WalletTransactionResponse(
            @JsonSerialize(using = SnowflakeIdSerializer.class) Long id,
            @JsonSerialize(using = SnowflakeIdSerializer.class) Long userId,
            String userNickname,
            String userPhone,
            String direction,
            String sourceType,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            String relatedOrderType,
            @JsonSerialize(using = SnowflakeIdSerializer.class) Long relatedOrderId,
            String operatorType,
            @JsonSerialize(using = SnowflakeIdSerializer.class) Long operatorId,
            String reason,
            LocalDateTime createTime
    ) {
    }

    /** Admin recharge request body. */
    public record WalletRechargeRequest(
            @NotNull @DecimalMin(value = "0.01", message = "充值金额必须大于 0") BigDecimal amount,
            @NotBlank(message = "充值理由不能为空") @Size(max = 500) String reason
    ) {
    }

    /** Admin bidirectional adjust request body. */
    public record WalletAdjustRequest(
            @NotNull @DecimalMin(value = "0.01", message = "调整金额必须大于 0") BigDecimal amount,
            @NotNull String direction,
            @NotBlank(message = "调整理由不能为空") @Size(max = 500) String reason
    ) {
    }

    /** Admin wallet account list row (joins user nickname for display). */
    public record WalletAccountRow(
            @JsonSerialize(using = SnowflakeIdSerializer.class) Long walletId,
            @JsonSerialize(using = SnowflakeIdSerializer.class) Long userId,
            String userNickname,
            String userPhone,
            BigDecimal balance,
            BigDecimal frozenAmount,
            Integer version,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
    }
}
