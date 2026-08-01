package com.petcare.wallet.service.impl;

import com.petcare.admin.entity.AdminOperationLog;
import com.petcare.admin.service.AdminOperationLogService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.pagination.PageResponse;
import com.petcare.user.entity.User;
import com.petcare.user.service.UserService;
import com.petcare.wallet.dto.WalletDtos.WalletAccountRow;
import com.petcare.wallet.dto.WalletDtos.WalletResponse;
import com.petcare.wallet.dto.WalletDtos.WalletTransactionResponse;
import com.petcare.wallet.entity.Wallet;
import com.petcare.wallet.service.WalletApplicationService;
import com.petcare.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Default {@link WalletApplicationService}.
 *
 * <p>Audit strategy mirrors {@code BookingApplicationServiceImpl} (the strict pattern, NOT the
 * lenient product pattern):
 * <ul>
 *   <li>Success: {@code operationLogService.save()} in the same transaction. If save returns
 *       false, throw {@link IllegalStateException} to roll back the whole business operation.</li>
 *   <li>Failure: {@code operationLogService.saveFailLog()} (REQUIRES_NEW) so the FAIL entry
 *       survives the business rollback. Failures inside the audit call are swallowed and only
 *       warned — they must NEVER suppress the original business exception.</li>
 *   <li>Unknown (non-Business) exceptions: error message sanitized to {@code "unexpected_error"}
 *       to avoid leaking SQL/stack traces into the audit log.</li>
 * </ul>
 */
@Service
public class WalletApplicationServiceImpl implements WalletApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WalletApplicationServiceImpl.class);

    private final WalletService walletService;
    private final AdminOperationLogService operationLogService;
    private final UserService userService;

    public WalletApplicationServiceImpl(WalletService walletService,
                                        AdminOperationLogService operationLogService,
                                        UserService userService) {
        this.walletService = walletService;
        this.operationLogService = operationLogService;
        this.userService = userService;
    }

    // ==================================================================
    // User-facing read
    // ==================================================================

    @Override
    public WalletResponse getMyWallet(Long userId) {
        return walletService.getMyWallet(userId);
    }

    @Override
    public PageResponse<WalletTransactionResponse> getMyTransactions(Long userId, int page, int size) {
        List<WalletTransactionResponse> items = walletService.getMyTransactions(userId, page, size);
        // User-facing list paging: total count is the user's own transaction count.
        // We approximate by treating the returned page size; for the miniapp preview use case
        // this is sufficient (the page is small). A precise total would need a dedicated count
        // call, added when the frontend requires it.
        long total = walletService.countTransactions(userId, null, null);
        return PageResponse.of(items, total, page, size);
    }

    // ==================================================================
    // Admin read
    // ==================================================================

    @Override
    public PageResponse<WalletAccountRow> listAccounts(String phoneKeyword, String userIdKeyword, int page, int size) {
        long total = walletService.countAccounts(phoneKeyword, userIdKeyword);
        List<WalletAccountRow> items = walletService.listAccounts(phoneKeyword, userIdKeyword, page, size);
        return PageResponse.of(items, total, page, size);
    }

    @Override
    public PageResponse<WalletTransactionResponse> listTransactions(
            Long userId, String sourceType, String direction, int page, int size) {
        long total = walletService.countTransactions(userId, sourceType, direction);
        List<WalletTransactionResponse> items = walletService.listTransactions(
                userId, sourceType, direction, page, size);
        return PageResponse.of(items, total, page, size);
    }

    // ==================================================================
    // Admin write (audited)
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletResponse rechargeByAdmin(Long userId, BigDecimal amount, String reason, Long operatorId) {
        String url = "/api/v1/admin/wallet/accounts/" + userId + "/recharge";
        User user = userService != null ? userService.getById(userId) : null;
        String targetName = user != null ? user.getNickname() : null;
        String phone = user != null ? user.getPhone() : null;
        String params = "targetName=" + sanitizeParams(targetName) + ", phone=" + phone
                + ", userId=" + userId + ",amount=" + amount + ",reason=" + sanitizeParams(reason);
        try {
            Wallet wallet = walletService.rechargeByAdmin(userId, amount, reason, operatorId);
            auditSuccess(operatorId, "wallet-recharge", url, params);
            return toResponse(wallet);
        } catch (RuntimeException e) {
            auditFail(operatorId, "wallet-recharge", url, params, e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletResponse adjustByAdmin(Long userId, BigDecimal amount, String direction,
                                        String reason, Long operatorId) {
        String url = "/api/v1/admin/wallet/accounts/" + userId + "/adjust";
        User user = userService != null ? userService.getById(userId) : null;
        String targetName = user != null ? user.getNickname() : null;
        String phone = user != null ? user.getPhone() : null;
        String params = "targetName=" + sanitizeParams(targetName) + ", phone=" + phone
                + ", userId=" + userId + ",amount=" + amount + ",direction=" + direction
                + ",reason=" + sanitizeParams(reason);
        try {
            Wallet wallet = walletService.adjustByAdmin(userId, amount, direction, reason, operatorId);
            auditSuccess(operatorId, "wallet-adjust", url, params);
            return toResponse(wallet);
        } catch (RuntimeException e) {
            auditFail(operatorId, "wallet-adjust", url, params, e);
            throw e;
        }
    }

    // ==================================================================
    // Audit helpers (mirror BookingApplicationServiceImpl)
    // ==================================================================

    private void auditSuccess(Long operatorId, String operation, String url, String params) {
        AdminOperationLog entry = buildAuditEntry(operatorId, operation, url, params);
        entry.setResult("SUCCESS");
        // Critical: if the SUCCESS log cannot be persisted, treat the whole operation as failed
        // by rolling back the business transaction. "Business done but no audit" is forbidden.
        if (!operationLogService.save(entry)) {
            throw new IllegalStateException("Failed to persist required admin operation log");
        }
    }

    private void auditFail(Long operatorId, String operation, String url,
                           String params, RuntimeException cause) {
        AdminOperationLog entry = buildAuditEntry(operatorId, operation, url, params);
        entry.setResult("FAIL");
        entry.setErrorMessage(sanitizeErrorMessage(cause));
        try {
            // saveFailLog runs in REQUIRES_NEW so the FAIL entry survives business rollback.
            if (!operationLogService.saveFailLog(entry)) {
                log.warn("Failed to write FAIL admin operation log: operatorId={}, operation={}",
                        operatorId, operation);
            }
        } catch (RuntimeException auditException) {
            // Swallow audit failures — they must never suppress the original business exception.
            log.warn("Failed to write FAIL admin operation log: operatorId={}, operation={}",
                    operatorId, operation);
        }
    }

    private AdminOperationLog buildAuditEntry(Long operatorId, String operation,
                                              String url, String params) {
        AdminOperationLog entry = new AdminOperationLog();
        entry.setAdminId(operatorId);
        entry.setModule("wallet");
        entry.setOperation(operation);
        entry.setRequestMethod("POST");
        entry.setRequestUrl(url);
        entry.setRequestParams(params);
        entry.setCreateTime(LocalDateTime.now());
        return entry;
    }

    /**
     * Only {@link BusinessException} messages are persisted (truncated to 1000 chars, the column
     * width). Everything else is replaced with {@code "unexpected_error"} so SQL fragments,
     * stack traces, or secrets never leak into the audit log.
     */
    private String sanitizeErrorMessage(RuntimeException exception) {
        if (exception instanceof BusinessException && exception.getMessage() != null) {
            return exception.getMessage().substring(0, Math.min(exception.getMessage().length(), 1000));
        }
        return "unexpected_error";
    }

    /** Truncate the params string to avoid blowing the request_params TEXT column arbitrarily. */
    private String sanitizeParams(String params) {
        if (params == null) {
            return "";
        }
        return params.length() > 500 ? params.substring(0, 500) : params;
    }

    private WalletResponse toResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(), wallet.getUserId(), wallet.getBalance(),
                wallet.getFrozenAmount(), wallet.getUpdateTime());
    }
}
