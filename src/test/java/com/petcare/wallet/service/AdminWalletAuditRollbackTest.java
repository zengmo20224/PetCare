package com.petcare.wallet.service;

import com.petcare.admin.entity.AdminOperationLog;
import com.petcare.admin.service.AdminOperationLogService;
import com.petcare.user.entity.User;
import com.petcare.user.mapper.UserMapper;
import com.petcare.wallet.entity.Wallet;
import com.petcare.wallet.entity.WalletTransaction;
import com.petcare.wallet.mapper.WalletMapper;
import com.petcare.wallet.mapper.WalletTransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Spring integration test for wallet admin audit transaction behavior (CR-20260718-003, D-012).
 *
 * <p>Mirrors {@code BookingAdminAuditRollbackTest}. Uses H2 (profile "test") with a mocked
 * {@link AdminOperationLogService} to verify the transaction contract:</p>
 * <ul>
 *   <li><b>SUCCESS audit failure rolls back the business operation</b>: when {@code save()}
 *       returns false, the recharge is rolled back — balance unchanged, no RECHARGE ledger row.</li>
 *   <li><b>FAIL audit survives business rollback</b>: when the recharge throws a business error,
 *       the FAIL audit is written via {@code saveFailLog} (REQUIRES_NEW) and the business change
 *       is rolled back.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("audit-rollback")
class AdminWalletAuditRollbackTest {

    @Autowired
    private WalletApplicationService walletApplicationService;

    @Autowired
    private WalletMapper walletMapper;

    @Autowired
    private WalletTransactionMapper walletTransactionMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AdminOperationLogService operationLogService;

    private Long testUserId;

    @BeforeEach
    void setUp() {
        // Fresh user + wallet (balance 50.00) per test.
        User user = new User();
        user.setPhone("139" + System.nanoTime() % 100000000L);
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        testUserId = user.getId();

        Wallet wallet = new Wallet();
        wallet.setUserId(testUserId);
        wallet.setBalance(new BigDecimal("50.00"));
        wallet.setFrozenAmount(new BigDecimal("0.00"));
        wallet.setVersion(0);
        walletMapper.insert(wallet);

        // Default: SUCCESS/FAIL audit both succeed unless overridden in a test.
        when(operationLogService.save(any(AdminOperationLog.class))).thenReturn(true);
        when(operationLogService.saveFailLog(any(AdminOperationLog.class))).thenReturn(true);
    }

    @Test
    @DisplayName("SUCCESS audit persistence failure rolls back the recharge (balance unchanged, no ledger)")
    void successAuditFailure_rollsBackRecharge() {
        BigDecimal balanceBefore = currentBalance(testUserId);
        long ledgerBefore = countLedger(testUserId);

        // Force the SUCCESS audit save to return false → service throws IllegalStateException.
        when(operationLogService.save(any(AdminOperationLog.class))).thenReturn(false);

        assertThatThrownBy(() -> walletApplicationService.rechargeByAdmin(
                testUserId, new BigDecimal("100.00"), "线下充值", 9001L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin operation log");

        // CRITICAL: the recharge must have been rolled back.
        assertThat(currentBalance(testUserId))
                .as("Balance must be unchanged after SUCCESS-audit-failure rollback")
                .isEqualByComparingTo(balanceBefore);
        assertThat(countLedger(testUserId))
                .as("No RECHARGE ledger row must remain after rollback")
                .isEqualTo(ledgerBefore);
    }

    @Test
    @DisplayName("Business failure: FAIL audit written via REQUIRES_NEW, business rolled back")
    void businessFailure_failAuditSurvivesRollback() {
        BigDecimal balanceBefore = currentBalance(testUserId);
        long ledgerBefore = countLedger(testUserId);

        // Trigger a business failure: debit amount exceeds balance.
        assertThatThrownBy(() -> walletApplicationService.adjustByAdmin(
                testUserId, new BigDecimal("9999.00"), "DEBIT", "测试扣减", 9001L))
                .isInstanceOf(Exception.class);

        // CRITICAL: business change rolled back (balance unchanged).
        assertThat(currentBalance(testUserId))
                .as("Balance must be unchanged after business failure")
                .isEqualByComparingTo(balanceBefore);
        assertThat(countLedger(testUserId))
                .as("No ADMIN_ADJUST ledger row must remain after rollback")
                .isEqualTo(ledgerBefore);

        // CRITICAL: FAIL audit was still attempted (via saveFailLog REQUIRES_NEW), proving it
        // survives the business rollback. We verify the mock was invoked.
        org.mockito.Mockito.verify(operationLogService)
                .saveFailLog(any(AdminOperationLog.class));
        org.mockito.Mockito.verify(operationLogService, org.mockito.Mockito.never())
                .save(any(AdminOperationLog.class));
    }

    @Test
    @DisplayName("Happy recharge: balance increased, RECHARGE ledger written, SUCCESS audit written")
    void happyRecharge_allPersisted() {
        BigDecimal balanceBefore = currentBalance(testUserId);

        walletApplicationService.rechargeByAdmin(
                testUserId, new BigDecimal("100.00"), "线下现金充值", 9001L);

        // Balance increased by 100.
        assertThat(currentBalance(testUserId))
                .isEqualByComparingTo(balanceBefore.add(new BigDecimal("100.00")));

        // One RECHARGE ledger row with correct snapshot.
        List<WalletTransaction> recharges = walletTransactionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getUserId, testUserId)
                        .eq(WalletTransaction::getSourceType, "RECHARGE"));
        assertThat(recharges).hasSize(1);
        WalletTransaction tx = recharges.get(0);
        assertThat(tx.getDirection()).isEqualTo("CREDIT");
        assertThat(tx.getAmount()).isEqualByComparingTo("100.00");
        assertThat(tx.getBalanceBefore()).isEqualByComparingTo(balanceBefore);
        assertThat(tx.getOperatorType()).isEqualTo("ADMIN");
        assertThat(tx.getReason()).isEqualTo("线下现金充值");

        // SUCCESS audit was written.
        org.mockito.Mockito.verify(operationLogService)
                .save(any(AdminOperationLog.class));
    }

    private BigDecimal currentBalance(Long userId) {
        Wallet w = walletMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Wallet>()
                        .eq(Wallet::getUserId, userId));
        return w.getBalance();
    }

    private long countLedger(Long userId) {
        return walletTransactionMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getUserId, userId));
    }
}
