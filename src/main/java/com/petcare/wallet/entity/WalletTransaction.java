package com.petcare.wallet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Append-only wallet transaction ledger.
 * Every balance change produces exactly one record with before/after balance snapshot.
 * Does NOT extend BaseEntity: this table has only create_time (no update_time/deleted).
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wallet_transaction")
public class WalletTransaction {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_id")
    private Long userId;

    /** DEBIT (decrease) or CREDIT (increase). */
    @TableField("direction")
    private String direction;

    /** RECHARGE / PAY / REFUND / ADMIN_ADJUST / BONUS. */
    @TableField("source_type")
    private String sourceType;

    /** Always positive; sign is determined by direction. */
    @TableField("amount")
    private BigDecimal amount;

    @TableField("balance_before")
    private BigDecimal balanceBefore;

    @TableField("balance_after")
    private BigDecimal balanceAfter;

    /** PRODUCT_ORDER / SERVICE_BOOKING / null. */
    @TableField("related_order_type")
    private String relatedOrderType;

    @TableField("related_order_id")
    private Long relatedOrderId;

    /** USER / ADMIN / SYSTEM. */
    @TableField("operator_type")
    private String operatorType;

    @TableField("operator_id")
    private Long operatorId;

    /** Idempotency key, unique per (user_id, idempotency_key). */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /** Required for ADMIN_ADJUST; optional otherwise. */
    @TableField("reason")
    private String reason;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
