package com.petcare.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petcare.wallet.entity.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * Mapper for {@link Wallet}.
 *
 * <p>Lock order discipline (per D-012): balance changes must lock the wallet row first via
 * {@link #selectForUpdate} before any conditional UPDATE. Global cross-table lock order is
 * {@code wallet → product(asc) → booking} to prevent deadlocks.</p>
 */
@Mapper
public interface WalletMapper extends BaseMapper<Wallet> {

    /**
     * Pessimistic row lock on the wallet. Must be called inside a transaction before any
     * balance mutation to serialize concurrent deductions on the same user.
     */
    @Select("SELECT * FROM user_wallet WHERE user_id = #{userId} AND deleted = 0 FOR UPDATE")
    Wallet selectForUpdate(@Param("userId") Long userId);

    /**
     * Atomically deducts balance. Only succeeds if balance &gt;= amount.
     * Callers MUST have already locked the row via {@link #selectForUpdate} to prevent
     * the lost-update window between read and conditional write.
     *
     * @return 1 on success, 0 if balance insufficient (should not happen when row is locked)
     */
    @Update("UPDATE user_wallet SET balance = balance - #{amount}, version = version + 1, update_time = NOW() " +
            "WHERE user_id = #{userId} AND balance >= #{amount} AND deleted = 0")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * Atomically increases balance (recharge / refund / admin top-up).
     */
    @Update("UPDATE user_wallet SET balance = balance + #{amount}, version = version + 1, update_time = NOW() " +
            "WHERE user_id = #{userId} AND deleted = 0")
    int addBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
