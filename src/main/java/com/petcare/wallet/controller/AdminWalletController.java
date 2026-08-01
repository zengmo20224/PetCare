package com.petcare.wallet.controller;

import com.petcare.common.api.ApiResponse;
import com.petcare.common.pagination.PageResponse;
import com.petcare.common.security.SecurityContextHelper;
import com.petcare.wallet.dto.WalletDtos.WalletAccountRow;
import com.petcare.wallet.dto.WalletDtos.WalletAdjustRequest;
import com.petcare.wallet.dto.WalletDtos.WalletRechargeRequest;
import com.petcare.wallet.dto.WalletDtos.WalletResponse;
import com.petcare.wallet.dto.WalletDtos.WalletTransactionResponse;
import com.petcare.wallet.service.WalletApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin wallet management endpoints.
 *
 * <p>All write operations (recharge / adjust) are audited via the booking pattern
 * (CR-20260718-003, D-012): success audit in-transaction, failure audit via REQUIRES_NEW,
 * unknown exceptions sanitized. See {@code WalletApplicationServiceImpl}.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/wallet")
public class AdminWalletController {

    private final WalletApplicationService walletApplicationService;

    public AdminWalletController(WalletApplicationService walletApplicationService) {
        this.walletApplicationService = walletApplicationService;
    }

    // ===== Admin read =====

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('wallet:account:read')")
    public ResponseEntity<ApiResponse<PageResponse<WalletAccountRow>>> listAccounts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String userId) {
        PageResponse<WalletAccountRow> response =
                walletApplicationService.listAccounts(phone, userId, page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/accounts/{userId}")
    @PreAuthorize("hasAuthority('wallet:account:read')")
    public ResponseEntity<ApiResponse<WalletResponse>> getAccount(@PathVariable Long userId) {
        // Reuse the user-facing read; admin sees the same wallet state.
        WalletResponse response = walletApplicationService.getMyWallet(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('wallet:transaction:read')")
    public ResponseEntity<ApiResponse<PageResponse<WalletTransactionResponse>>> listTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String direction) {
        PageResponse<WalletTransactionResponse> response =
                walletApplicationService.listTransactions(userId, sourceType, direction, page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ===== Admin write (audited) =====

    @PostMapping("/accounts/{userId}/recharge")
    @PreAuthorize("hasAuthority('wallet:account:recharge')")
    public ResponseEntity<ApiResponse<WalletResponse>> recharge(
            @PathVariable Long userId,
            @Valid @RequestBody WalletRechargeRequest request) {
        Long operatorId = SecurityContextHelper.getCurrentAdminId()
                .orElseThrow(() -> new IllegalStateException("No admin identity"));
        WalletResponse response = walletApplicationService.rechargeByAdmin(
                userId, request.amount(), request.reason(), operatorId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/accounts/{userId}/adjust")
    @PreAuthorize("hasAuthority('wallet:account:adjust')")
    public ResponseEntity<ApiResponse<WalletResponse>> adjust(
            @PathVariable Long userId,
            @Valid @RequestBody WalletAdjustRequest request) {
        Long operatorId = SecurityContextHelper.getCurrentAdminId()
                .orElseThrow(() -> new IllegalStateException("No admin identity"));
        WalletResponse response = walletApplicationService.adjustByAdmin(
                userId, request.amount(), request.direction(), request.reason(), operatorId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
