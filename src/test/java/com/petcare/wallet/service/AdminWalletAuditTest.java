package com.petcare.wallet.service;

import com.petcare.admin.entity.AdminOperationLog;
import com.petcare.admin.service.AdminOperationLogService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.wallet.dto.WalletDtos.WalletResponse;
import com.petcare.wallet.entity.Wallet;
import com.petcare.wallet.service.impl.WalletApplicationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for wallet admin operation audit logging (CR-20260718-003, D-012).
 *
 * <p>Verifies the user's hard requirement #1: "钱包操作留痕：钱包的一个操作如果出现失败，必须留痕。"
 * Mirrors {@code BookingAdminAuditTest} (the strict pattern, NOT the lenient product pattern).</p>
 *
 * <p>6 cases covered:</p>
 * <ol>
 *   <li>SUCCESS recharge writes SUCCESS audit with correct module/operation/url fields.</li>
 *   <li>SUCCESS adjust writes SUCCESS audit with direction in params.</li>
 *   <li>Business failure writes FAIL audit, preserves original exception, no SUCCESS audit.</li>
 *   <li>Unknown exception sanitized to "unexpected_error".</li>
 *   <li>Fail-audit write failure does NOT suppress original exception.</li>
 *   <li>Success-audit persistence failure throws IllegalStateException (forces rollback).</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AdminWalletAuditTest {

    @Mock
    private WalletService walletService;

    @Mock
    private AdminOperationLogService operationLogService;

    @Mock
    private com.petcare.user.service.UserService userService;

    private WalletApplicationServiceImpl service;

    private static final Long USER_ID = 1001L;
    private static final Long OPERATOR_ID = 9001L;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @BeforeEach
    void setUp() {
        service = new WalletApplicationServiceImpl(walletService, operationLogService, userService);
        // WalletApplicationService now looks up the user's nickname/phone for audit params.
        // Lenient stub so tests that don't reach the recharge/adjust body don't fail strict stubbing.
        org.mockito.Mockito.lenient().when(userService.getById(USER_ID)).thenReturn(stubUser());
    }

    private com.petcare.user.entity.User stubUser() {
        com.petcare.user.entity.User u = new com.petcare.user.entity.User();
        u.setId(USER_ID);
        u.setNickname("测试用户");
        u.setPhone("13800000000");
        return u;
    }

    private Wallet stubWallet() {
        Wallet wallet = new Wallet();
        wallet.setId(1L);
        wallet.setUserId(USER_ID);
        wallet.setBalance(new BigDecimal("200.00"));
        wallet.setFrozenAmount(new BigDecimal("0.00"));
        wallet.setVersion(1);
        return wallet;
    }

    // ========== SUCCESS AUDIT ==========

    @Nested
    @DisplayName("SUCCESS audit")
    class SuccessAudit {

        @Test
        @DisplayName("Recharge success writes SUCCESS audit with module=wallet, operation=wallet-recharge, correct url")
        void rechargeSuccess_writesSuccessAudit() {
            when(walletService.rechargeByAdmin(USER_ID, AMOUNT, "线下现金充值", OPERATOR_ID))
                    .thenReturn(stubWallet());
            when(operationLogService.save(any(AdminOperationLog.class))).thenReturn(true);

            service.rechargeByAdmin(USER_ID, AMOUNT, "线下现金充值", OPERATOR_ID);

            ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
            verify(operationLogService).save(captor.capture());

            AdminOperationLog entry = captor.getValue();
            assertThat(entry.getAdminId()).isEqualTo(OPERATOR_ID);
            assertThat(entry.getModule()).isEqualTo("wallet");
            assertThat(entry.getOperation()).isEqualTo("wallet-recharge");
            assertThat(entry.getRequestMethod()).isEqualTo("POST");
            assertThat(entry.getRequestUrl())
                    .isEqualTo("/api/v1/admin/wallet/accounts/" + USER_ID + "/recharge");
            assertThat(entry.getRequestParams()).contains(String.valueOf(USER_ID));
            assertThat(entry.getRequestParams()).contains("100.00");
            assertThat(entry.getResult()).isEqualTo("SUCCESS");
            assertThat(entry.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("Adjust success writes SUCCESS audit with direction in params")
        void adjustSuccess_writesAuditWithDirection() {
            when(walletService.adjustByAdmin(USER_ID, AMOUNT, "DEBIT", "纠错", OPERATOR_ID))
                    .thenReturn(stubWallet());
            when(operationLogService.save(any(AdminOperationLog.class))).thenReturn(true);

            service.adjustByAdmin(USER_ID, AMOUNT, "DEBIT", "纠错", OPERATOR_ID);

            ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
            verify(operationLogService).save(captor.capture());

            AdminOperationLog entry = captor.getValue();
            assertThat(entry.getOperation()).isEqualTo("wallet-adjust");
            assertThat(entry.getRequestUrl())
                    .isEqualTo("/api/v1/admin/wallet/accounts/" + USER_ID + "/adjust");
            assertThat(entry.getRequestParams()).contains("direction=DEBIT");
            assertThat(entry.getResult()).isEqualTo("SUCCESS");
        }
    }

    // ========== FAIL AUDIT ==========

    @Nested
    @DisplayName("FAIL audit (硬性要求 #1: 失败必留痕)")
    class FailAudit {

        @Test
        @DisplayName("Business failure writes one FAIL audit, preserves original exception, no SUCCESS audit")
        void businessFailure_writesOneFailAudit() {
            BusinessException original = new BusinessException(
                    ErrorCode.WALLET_ADJUST_REASON_REQUIRED, "钱包调整必须填写理由");
            when(walletService.rechargeByAdmin(USER_ID, AMOUNT, "", OPERATOR_ID))
                    .thenThrow(original);
            when(operationLogService.saveFailLog(any(AdminOperationLog.class))).thenReturn(true);

            assertThatThrownBy(() -> service.rechargeByAdmin(USER_ID, AMOUNT, "", OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("钱包调整必须填写理由");

            ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
            verify(operationLogService).saveFailLog(captor.capture());
            // CRITICAL: no SUCCESS audit on failure.
            verify(operationLogService, never()).save(any());

            AdminOperationLog entry = captor.getValue();
            assertThat(entry.getResult()).isEqualTo("FAIL");
            // Business exception message preserved (not sanitized).
            assertThat(entry.getErrorMessage()).isEqualTo("钱包调整必须填写理由");
            assertThat(entry.getAdminId()).isEqualTo(OPERATOR_ID);
            assertThat(entry.getModule()).isEqualTo("wallet");
        }

        @Test
        @DisplayName("Unknown exception sanitized to 'unexpected_error' (no SQL/stack leak)")
        void unknownFailure_sanitizedToUnexpectedError() {
            RuntimeException unexpected = new RuntimeException(
                    "SQLException: SELECT password FROM users WHERE id=1; cause=...");
            when(walletService.rechargeByAdmin(USER_ID, AMOUNT, "reason", OPERATOR_ID))
                    .thenThrow(unexpected);
            when(operationLogService.saveFailLog(any(AdminOperationLog.class))).thenReturn(true);

            assertThatThrownBy(() -> service.rechargeByAdmin(USER_ID, AMOUNT, "reason", OPERATOR_ID))
                    .isInstanceOf(RuntimeException.class);

            ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
            verify(operationLogService).saveFailLog(captor.capture());

            // CRITICAL: error message must NOT leak the SQL/stack — must be "unexpected_error".
            assertThat(captor.getValue().getErrorMessage()).isEqualTo("unexpected_error");
        }

        @Test
        @DisplayName("Fail-audit write failure does NOT suppress original business exception")
        void failAuditFailure_preservesOriginalException() {
            BusinessException original = new BusinessException(
                    ErrorCode.WALLET_BALANCE_INSUFFICIENT, "钱包余额不足");
            when(walletService.adjustByAdmin(USER_ID, AMOUNT, "DEBIT", "reason", OPERATOR_ID))
                    .thenThrow(original);
            // The FAIL audit write itself throws — must NOT swallow the original.
            when(operationLogService.saveFailLog(any(AdminOperationLog.class)))
                    .thenThrow(new RuntimeException("DB connection lost"));

            assertThatThrownBy(() -> service.adjustByAdmin(USER_ID, AMOUNT, "DEBIT", "reason", OPERATOR_ID))
                    // Original exception surfaces, not the audit failure.
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("钱包余额不足");
        }

        @Test
        @DisplayName("Success-audit persistence failure throws IllegalStateException (forces business rollback)")
        void successAuditPersistenceFailure_throwsIllegalState() {
            when(walletService.rechargeByAdmin(USER_ID, AMOUNT, "reason", OPERATOR_ID))
                    .thenReturn(stubWallet());
            // save() returns false → audit considered failed → whole operation must roll back.
            when(operationLogService.save(any(AdminOperationLog.class))).thenReturn(false);

            assertThatThrownBy(() -> service.rechargeByAdmin(USER_ID, AMOUNT, "reason", OPERATOR_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("admin operation log");
        }
    }
}
