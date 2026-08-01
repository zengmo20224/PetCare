package com.petcare.wallet.service;

import com.petcare.common.pagination.PageResponse;
import com.petcare.wallet.dto.WalletDtos.WalletAccountRow;
import com.petcare.wallet.dto.WalletDtos.WalletResponse;
import com.petcare.wallet.dto.WalletDtos.WalletTransactionResponse;

import java.math.BigDecimal;

/**
 * Application-service facade for wallet admin and user-facing operations.
 *
 * <p>Admin write operations (recharge / adjust) follow the booking audit pattern (CR-20260718-003,
 * D-012): they run inside a single transaction that includes both the balance mutation and the
 * audit log. Success audit failure rolls back the business; failure audit uses REQUIRES_NEW via
 * {@code saveFailLog} so it survives business rollback; unknown exceptions are sanitized to
 * {@code "unexpected_error"}.</p>
 */
public interface WalletApplicationService {

    // ===== User-facing (read-only) =====

    /** Returns the current user's wallet (zero-balance view if none exists yet). */
    WalletResponse getMyWallet(Long userId);

    /** Returns the current user's transactions (newest first), paged. */
    PageResponse<WalletTransactionResponse> getMyTransactions(Long userId, int page, int size);

    // ===== Admin read =====

    /** Admin: paged wallet account list, optionally filtered by phone keyword. */
    PageResponse<WalletAccountRow> listAccounts(String phoneKeyword, String userIdKeyword, int page, int size);

    /** Admin: paged transaction query across all users. */
    PageResponse<WalletTransactionResponse> listTransactions(
            Long userId, String sourceType, String direction, int page, int size);

    // ===== Admin write (audited) =====

    /**
     * Admin recharge: increases the user's balance. Reason is required.
     * Writes a SUCCESS/FAIL audit log entry with module {@code "wallet"}.
     */
    WalletResponse rechargeByAdmin(Long userId, BigDecimal amount, String reason, Long operatorId);

    /**
     * Admin bidirectional adjust: increases (CREDIT) or decreases (DEBIT) the balance.
     * Reason is required. Writes a SUCCESS/FAIL audit log entry with module {@code "wallet"}.
     */
    WalletResponse adjustByAdmin(Long userId, BigDecimal amount, String direction,
                                 String reason, Long operatorId);
}
