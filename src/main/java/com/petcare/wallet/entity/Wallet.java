package com.petcare.wallet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.petcare.common.entity.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * User wallet account.
 * Per-user ledger balance maintained as admin manual record (D-010/D-012).
 * Balance changes MUST be performed inside a transaction after SELECT ... FOR UPDATE.
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("user_wallet")
public class Wallet extends BaseEntity {

    @TableField("user_id")
    private Long userId;

    /** Available balance, DECIMAL(10,2). */
    @TableField("balance")
    private BigDecimal balance;

    /** Reserved for future freeze/refund flows; not used in current phase. */
    @TableField("frozen_amount")
    private BigDecimal frozenAmount;

    /** Optimistic lock version (also enforced via row lock in deduct flows). */
    @TableField("version")
    private Integer version;
}
