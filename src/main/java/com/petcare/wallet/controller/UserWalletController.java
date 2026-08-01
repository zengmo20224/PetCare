package com.petcare.wallet.controller;

import com.petcare.common.api.ApiResponse;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import com.petcare.common.security.SecurityContextHelper;
import com.petcare.wallet.dto.WalletDtos.WalletResponse;
import com.petcare.wallet.dto.WalletDtos.WalletTransactionResponse;
import com.petcare.wallet.service.WalletApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-facing wallet endpoints (read-only).
 *
 * <p>Users can view their own balance and transaction history but cannot self-recharge —
 * top-ups are admin-only operations (D-010/D-012). User-side reads do NOT write to
 * {@code admin_operation_log} (that table is for admin actions only, mirroring booking).</p>
 */
@RestController
@RequestMapping("/api/v1/user/wallet")
@PreAuthorize("hasRole('USER')")
public class UserWalletController {

    private final WalletApplicationService walletApplicationService;

    public UserWalletController(WalletApplicationService walletApplicationService) {
        this.walletApplicationService = walletApplicationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet() {
        Long userId = currentUserId();
        WalletResponse response = walletApplicationService.getMyWallet(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<WalletTransactionResponse>>> getMyTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = currentUserId();
        PageResponse<WalletTransactionResponse> response =
                walletApplicationService.getMyTransactions(userId, page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private Long currentUserId() {
        return SecurityContextHelper.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"));
    }
}
