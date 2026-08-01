package com.petcare.wallet.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.petcare.wallet.dto.WalletDtos.WalletAccountRow;
import com.petcare.wallet.dto.WalletDtos.WalletResponse;
import com.petcare.wallet.dto.WalletDtos.WalletTransactionResponse;
import com.petcare.wallet.entity.Wallet;
import com.petcare.wallet.entity.WalletTransaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

/**
 * Wallet domain service.
 *
 * <p>Extends {@link IService} so that plain CRUD on {@link Wallet} follows the same MyBatis-Plus
 * pattern as other modules (required by {@code MapperAndServiceCoverageTest} architectural guard).</p>
 *
 * <p>Two layers of methods:</p>
 * <ul>
 *   <li><b>Transactional primitives</b> ({@link #deductForPayment}, {@link #refundForCancellation},
 *       {@link #rechargeByAdmin}, {@link #adjustByAdmin}): intended to be called from inside an
 *       already-open business transaction (e.g. the order-creation transaction). They enforce
 *       row locking, conditional UPDATE, and ledger writes.</li>
 *   <li><b>Read-side queries</b> ({@link #getMyWallet}, {@link #getMyTransactions},
 *       {@link #listAccounts}, {@link #listTransactions}): for controllers.</li>
 * </ul>
 *
 * <p>Lock-order discipline (D-012): every mutation calls {@code selectForUpdate} before the
 * conditional UPDATE. Global cross-table order: {@code wallet → product(asc) → booking}.</p>
 */
public interface WalletService extends IService<Wallet> {

    // ------------------------------------------------------------------
    // Read side
    // ------------------------------------------------------------------

    /** Returns the current user's wallet, creating it lazily on first access. */
    WalletResponse getMyWallet(Long userId);

    /** Lists the current user's transactions (newest first). */
    List<WalletTransactionResponse> getMyTransactions(Long userId, int page, int size);

    /**
     * Admin: paged wallet account list. Returns ALL users (with or without a wallet row),
     * optionally filtered by {@code phoneKeyword} (LIKE) and/or {@code userIdKeyword} (LIKE on
     * the snowflake ID as a string). Both filters AND-combine when present.
     */
    List<WalletAccountRow> listAccounts(String phoneKeyword, String userIdKeyword, int page, int size);

    /** Admin: total count of wallet accounts matching the filters (for paging). */
    long countAccounts(String phoneKeyword, String userIdKeyword);

    /** Admin: paged transaction query across all users. */
    List<WalletTransactionResponse> listTransactions(
            Long userId, String sourceType, String direction, int page, int size);

    /** Admin: total count of transactions matching the filter (for paging). */
    long countTransactions(Long userId, String sourceType, String direction);

    // ------------------------------------------------------------------
    // Transactional primitives (called inside an open business transaction)
    // ------------------------------------------------------------------

    /**
     * Lazily creates and returns the wallet for the given user.
     * Safe to call at the top of a business transaction to establish the wallet row lock early.
     */
    Wallet getOrCreateWallet(Long userId);

    /**
     * Locks the wallet row for update. Use this to fix the lock order before product/booking
     * operations when wallet payment may apply.
     */
    Wallet lockWalletForUpdate(Long userId);

    /**
     * Deducts {@code amount} from the user's wallet as payment for a business order.
     * Must be called inside the same transaction as stock/capacity deduction.
     *
     * @throws com.petcare.common.exception.BusinessException with code
     *         {@code wallet_balance_insufficient} if balance &lt; amount
     */
    WalletTransaction deductForPayment(
            Long userId,
            BigDecimal amount,
            String relatedOrderType,
            Long relatedOrderId,
            String idempotencyKey);

    /**
     * Refunds {@code amount} back to the wallet when a business order is cancelled.
     * Must be called inside the same transaction as stock/capacity restoration.
     */
    WalletTransaction refundForCancellation(
            Long userId,
            BigDecimal amount,
            String relatedOrderType,
            Long relatedOrderId,
            String idempotencyKey);

    /**
     * Admin recharge: increases balance. Records a RECHARGE ledger entry with the admin's
     * identity and the supplied reason.
     */
    Wallet rechargeByAdmin(Long userId, BigDecimal amount, String reason, Long operatorId);

    /**
     * Admin bidirectional adjust: increases or decreases balance based on {@code direction}
     * ({@code CREDIT} or {@code DEBIT}). Records an ADMIN_ADJUST ledger entry.
     */
    Wallet adjustByAdmin(Long userId, BigDecimal amount, String direction, String reason, Long operatorId);

    /**
     * Allows a caller to atomically record a ledger entry while holding the wallet lock,
     * without exposing mapper internals. Reserved for future internal flows.
     */
    void recordTransaction(Consumer<WalletTransaction> builder);
}
