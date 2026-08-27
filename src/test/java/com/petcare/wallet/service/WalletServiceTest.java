package com.petcare.wallet.service;

import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.user.entity.User;
import com.petcare.user.mapper.UserMapper;
import com.petcare.wallet.entity.Wallet;
import com.petcare.wallet.entity.WalletTransaction;
import com.petcare.wallet.dto.WalletDtos;
import com.petcare.wallet.mapper.WalletMapper;
import com.petcare.wallet.mapper.WalletTransactionMapper;
import com.petcare.wallet.service.impl.WalletServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link WalletServiceImpl} using Mockito.
 * Concurrency and real-transaction behavior is covered by {@code WalletConcurrencyMySqlIT}.
 */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private WalletTransactionMapper walletTransactionMapper;

    @Mock
    private UserMapper userMapper;

    private WalletServiceImpl walletService;

    private static final Long USER_ID = 1001L;
    private static final Long ADMIN_ID = 9001L;

    @BeforeEach
    void setUp() {
        // ServiceImpl.baseMapper is injected by Spring's field-autowiring in production;
        // in this pure Mockito test we wire the mocked WalletMapper into it via reflection.
        walletService = new WalletServiceImpl(walletTransactionMapper, userMapper);
        ReflectionTestUtils.setField(walletService, "baseMapper", walletMapper);
    }

    @Nested
    @DisplayName("deductForPayment")
    class DeductForPayment {

        @Test
        @DisplayName("happy path: deducts balance and writes PAY ledger with before/after snapshot")
        void deductsAndWritesLedger() {
            Wallet wallet = walletWithBalance("100.00");
            when(walletMapper.selectOne(any())).thenReturn(wallet);
            when(walletMapper.selectForUpdate(USER_ID)).thenReturn(wallet);
            when(walletMapper.deductBalance(eq(USER_ID), eq(new BigDecimal("30.00")))).thenReturn(1);

            WalletTransaction tx = walletService.deductForPayment(
                    USER_ID, new BigDecimal("30.00"), "PRODUCT_ORDER", 555L, "idem-1");

            ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
            verify(walletTransactionMapper).insert(captor.capture());
            WalletTransaction saved = captor.getValue();

            assertThat(saved.getDirection()).isEqualTo("DEBIT");
            assertThat(saved.getSourceType()).isEqualTo("PAY");
            assertThat(saved.getAmount()).isEqualByComparingTo("30.00");
            assertThat(saved.getBalanceBefore()).isEqualByComparingTo("100.00");
            assertThat(saved.getBalanceAfter()).isEqualByComparingTo("70.00");
            assertThat(saved.getRelatedOrderType()).isEqualTo("PRODUCT_ORDER");
            assertThat(saved.getRelatedOrderId()).isEqualTo(555L);
            assertThat(saved.getOperatorType()).isEqualTo("USER");
            assertThat(saved.getOperatorId()).isEqualTo(USER_ID);
            assertThat(saved.getIdempotencyKey()).isEqualTo("idem-1");
            assertThat(tx).isSameAs(saved);
        }

        @Test
        @DisplayName("balance insufficient throws WALLET_BALANCE_INSUFFICIENT without writing ledger")
        void balanceInsufficient() {
            Wallet wallet = walletWithBalance("20.00");
            when(walletMapper.selectOne(any())).thenReturn(wallet);
            when(walletMapper.selectForUpdate(USER_ID)).thenReturn(wallet);

            assertThatThrownBy(() -> walletService.deductForPayment(
                    USER_ID, new BigDecimal("50.00"), "PRODUCT_ORDER", 1L, "idem"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.WALLET_BALANCE_INSUFFICIENT);

            verify(walletMapper, never()).deductBalance(any(), any());
            verify(walletTransactionMapper, never()).insert(any(WalletTransaction.class));
        }

        @Test
        @DisplayName("zero or negative amount rejected with WALLET_AMOUNT_INVALID")
        void amountMustBePositive() {
            assertThatThrownBy(() -> walletService.deductForPayment(
                    USER_ID, BigDecimal.ZERO, "PRODUCT_ORDER", 1L, "idem"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.WALLET_AMOUNT_INVALID);

            assertThatThrownBy(() -> walletService.deductForPayment(
                    USER_ID, new BigDecimal("-5"), "PRODUCT_ORDER", 1L, "idem"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.WALLET_AMOUNT_INVALID);
        }
    }

    @Nested
    @DisplayName("refundForCancellation")
    class RefundForCancellation {

        @Test
        @DisplayName("adds balance back and writes REFUND ledger with SYSTEM operator")
        void refundsAndWritesLedger() {
            Wallet wallet = walletWithBalance("70.00");
            when(walletMapper.selectOne(any())).thenReturn(wallet);
            when(walletMapper.selectForUpdate(USER_ID)).thenReturn(wallet);
            when(walletMapper.addBalance(eq(USER_ID), eq(new BigDecimal("30.00")))).thenReturn(1);

            walletService.refundForCancellation(
                    USER_ID, new BigDecimal("30.00"), "PRODUCT_ORDER", 555L, "refund-1");

            ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
            verify(walletTransactionMapper).insert(captor.capture());
            WalletTransaction saved = captor.getValue();

            assertThat(saved.getDirection()).isEqualTo("CREDIT");
            assertThat(saved.getSourceType()).isEqualTo("REFUND");
            assertThat(saved.getBalanceBefore()).isEqualByComparingTo("70.00");
            assertThat(saved.getBalanceAfter()).isEqualByComparingTo("100.00");
            assertThat(saved.getOperatorType()).isEqualTo("SYSTEM");
            assertThat(saved.getOperatorId()).isNull();
            assertThat(saved.getReason()).contains("取消");
        }
    }

    @Nested
    @DisplayName("rechargeByAdmin")
    class RechargeByAdmin {

        @Test
        @DisplayName("adds balance and writes RECHARGE ledger with ADMIN operator and reason")
        void rechargesAndWritesLedger() {
            Wallet wallet = walletWithBalance("0.00");
            when(walletMapper.selectOne(any())).thenReturn(wallet);
            when(walletMapper.selectForUpdate(USER_ID)).thenReturn(wallet);
            when(walletMapper.addBalance(eq(USER_ID), eq(new BigDecimal("100.00")))).thenReturn(1);
            when(walletMapper.selectById(any())).thenReturn(wallet);

            walletService.rechargeByAdmin(USER_ID, new BigDecimal("100.00"), "客户线下现金充值", ADMIN_ID);

            ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
            verify(walletTransactionMapper).insert(captor.capture());
            WalletTransaction saved = captor.getValue();

            assertThat(saved.getDirection()).isEqualTo("CREDIT");
            assertThat(saved.getSourceType()).isEqualTo("RECHARGE");
            assertThat(saved.getOperatorType()).isEqualTo("ADMIN");
            assertThat(saved.getOperatorId()).isEqualTo(ADMIN_ID);
            assertThat(saved.getReason()).isEqualTo("客户线下现金充值");
            assertThat(saved.getBalanceAfter()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("blank reason rejected with WALLET_ADJUST_REASON_REQUIRED")
        void blankReasonRejected() {
            assertThatThrownBy(() -> walletService.rechargeByAdmin(
                    USER_ID, new BigDecimal("100.00"), "  ", ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.WALLET_ADJUST_REASON_REQUIRED);

            assertThatThrownBy(() -> walletService.rechargeByAdmin(
                    USER_ID, new BigDecimal("100.00"), null, ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.WALLET_ADJUST_REASON_REQUIRED);
        }

        @Test
        @DisplayName("单笔金额超过 100 万上限被拒绝（2026-08-23 审计 M8）")
        void overSingleOperationLimitRejected() {
            assertThatThrownBy(() -> walletService.rechargeByAdmin(
                    USER_ID, new BigDecimal("1000000.01"), "超大充值", ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.WALLET_AMOUNT_INVALID);
        }
    }

    @Nested
    @DisplayName("adjustByAdmin")
    class AdjustByAdmin {

        @Test
        @DisplayName("DEBIT direction decreases balance")
        void debitDirection() {
            Wallet wallet = walletWithBalance("100.00");
            when(walletMapper.selectOne(any())).thenReturn(wallet);
            when(walletMapper.selectForUpdate(USER_ID)).thenReturn(wallet);
            when(walletMapper.deductBalance(eq(USER_ID), eq(new BigDecimal("20.00")))).thenReturn(1);
            when(walletMapper.selectById(any())).thenReturn(wallet);

            walletService.adjustByAdmin(USER_ID, new BigDecimal("20.00"), "DEBIT", "纠错多充值", ADMIN_ID);

            ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
            verify(walletTransactionMapper).insert(captor.capture());
            assertThat(captor.getValue().getDirection()).isEqualTo("DEBIT");
            assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("80.00");
        }

        @Test
        @DisplayName("CREDIT direction increases balance")
        void creditDirection() {
            Wallet wallet = walletWithBalance("100.00");
            when(walletMapper.selectOne(any())).thenReturn(wallet);
            when(walletMapper.selectForUpdate(USER_ID)).thenReturn(wallet);
            when(walletMapper.addBalance(eq(USER_ID), eq(new BigDecimal("50.00")))).thenReturn(1);
            when(walletMapper.selectById(any())).thenReturn(wallet);

            walletService.adjustByAdmin(USER_ID, new BigDecimal("50.00"), "CREDIT", "补偿", ADMIN_ID);

            ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
            verify(walletTransactionMapper).insert(captor.capture());
            assertThat(captor.getValue().getDirection()).isEqualTo("CREDIT");
            assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("150.00");
        }

        @Test
        @DisplayName("DEBIT exceeding balance throws WALLET_BALANCE_INSUFFICIENT")
        void debitExceedingBalance() {
            Wallet wallet = walletWithBalance("10.00");
            when(walletMapper.selectOne(any())).thenReturn(wallet);
            when(walletMapper.selectForUpdate(USER_ID)).thenReturn(wallet);

            assertThatThrownBy(() -> walletService.adjustByAdmin(
                    USER_ID, new BigDecimal("50.00"), "DEBIT", "reason", ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.WALLET_BALANCE_INSUFFICIENT);

            verify(walletMapper, never()).deductBalance(any(), any());
        }

        @Test
        @DisplayName("invalid direction rejected")
        void invalidDirection() {
            assertThatThrownBy(() -> walletService.adjustByAdmin(
                    USER_ID, new BigDecimal("10.00"), "UP", "reason", ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        }
    }

    @Nested
    @DisplayName("getOrCreateWallet")
    class GetOrCreateWallet {

        @Test
        @DisplayName("returns existing wallet without creating new one")
        void returnsExisting() {
            Wallet existing = walletWithBalance("50.00");
            when(walletMapper.selectOne(any())).thenReturn(existing);

            Wallet result = walletService.getOrCreateWallet(USER_ID);

            assertThat(result).isSameAs(existing);
            verify(walletMapper, never()).insert(any(Wallet.class));
        }

        @Test
        @DisplayName("creates new wallet when none exists and user is valid")
        void createsNew() {
            when(walletMapper.selectOne(any())).thenReturn(null);
            User user = new User();
            user.setId(USER_ID);
            user.setDeleted(0);
            when(userMapper.selectById(USER_ID)).thenReturn(user);

            Wallet result = walletService.getOrCreateWallet(USER_ID);

            assertThat(result.getBalance()).isEqualByComparingTo("0.00");
            assertThat(result.getVersion()).isZero();
            verify(walletMapper).insert(any(Wallet.class));
        }

        @Test
        @DisplayName("rejects creation when user does not exist")
        void rejectsInvalidUser() {
            when(walletMapper.selectOne(any())).thenReturn(null);
            when(userMapper.selectById(USER_ID)).thenReturn(null);

            assertThatThrownBy(() -> walletService.getOrCreateWallet(USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("concurrent first-access: DuplicateKeyException triggers reload of existing wallet")
        void concurrentFirstAccess() {
            // Simulate another thread inserting the wallet between our selectOne and insert.
            Wallet race = walletWithBalance("0.00");
            when(walletMapper.selectOne(any())).thenReturn(null, race);
            when(userMapper.selectById(USER_ID)).thenReturn(activeUser());
            // First insert throws DuplicateKeyException; the recovery path re-selects.
            org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_user_id"))
                    .when(walletMapper).insert(any(Wallet.class));

            Wallet result = walletService.getOrCreateWallet(USER_ID);

            assertThat(result).isSameAs(race);
        }

        @Test
        @DisplayName("concurrent first-access: DuplicateKeyException with no recoverable row rethrows")
        void concurrentFirstAccessUnrecoverable() {
            when(walletMapper.selectOne(any())).thenReturn(null, null);
            when(userMapper.selectById(USER_ID)).thenReturn(activeUser());
            org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_user_id"))
                    .when(walletMapper).insert(any(Wallet.class));

            assertThatThrownBy(() -> walletService.getOrCreateWallet(USER_ID))
                    .isInstanceOf(DuplicateKeyException.class);
        }
    }

    @Nested
    @DisplayName("Idempotency & ledger dedup")
    class Idempotency {

        @Test
        @DisplayName("duplicate idempotency key on deduct throws WALLET_TRANSACTION_DUPLICATE")
        void duplicateIdempotencyKeyOnDeduct() {
            Wallet wallet = walletWithBalance("100.00");
            when(walletMapper.selectOne(any())).thenReturn(wallet);
            when(walletMapper.selectForUpdate(USER_ID)).thenReturn(wallet);
            when(walletMapper.deductBalance(eq(USER_ID), any())).thenReturn(1);
            // Ledger insert violates unique (user_id, idempotency_key).
            org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_wallet_idempotency"))
                    .when(walletTransactionMapper).insert(any(WalletTransaction.class));

            assertThatThrownBy(() -> walletService.deductForPayment(
                    USER_ID, new BigDecimal("30.00"), "PRODUCT_ORDER", 1L, "dup-key"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.WALLET_TRANSACTION_DUPLICATE);
        }

        @Test
        @DisplayName("duplicate idempotency key on refund throws WALLET_TRANSACTION_DUPLICATE")
        void duplicateIdempotencyKeyOnRefund() {
            Wallet wallet = walletWithBalance("100.00");
            when(walletMapper.selectOne(any())).thenReturn(wallet);
            when(walletMapper.selectForUpdate(USER_ID)).thenReturn(wallet);
            when(walletMapper.addBalance(eq(USER_ID), any())).thenReturn(1);
            org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_wallet_idempotency"))
                    .when(walletTransactionMapper).insert(any(WalletTransaction.class));

            assertThatThrownBy(() -> walletService.refundForCancellation(
                    USER_ID, new BigDecimal("30.00"), "PRODUCT_ORDER", 1L, "dup-refund"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.WALLET_TRANSACTION_DUPLICATE);
        }
    }

    @Nested
    @DisplayName("Amount precision (BigDecimal HALF_UP, 2 decimals)")
    class AmountPrecision {

        @Test
        @DisplayName("amount with 3 decimals is rounded to 2 places via HALF_UP")
        void roundsThreeDecimals() {
            Wallet wallet = walletWithBalance("100.00");
            when(walletMapper.selectOne(any())).thenReturn(wallet);
            when(walletMapper.selectForUpdate(USER_ID)).thenReturn(wallet);
            when(walletMapper.deductBalance(eq(USER_ID), eq(new BigDecimal("30.01")))).thenReturn(1);

            // 30.005 should round to 30.01 under HALF_UP
            walletService.deductForPayment(
                    USER_ID, new BigDecimal("30.005"), "PRODUCT_ORDER", 1L, "idem-prec");

            ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
            verify(walletTransactionMapper).insert(captor.capture());
            assertThat(captor.getValue().getAmount()).isEqualByComparingTo("30.01");
            // balance_before 100.00 - 30.01 = 69.99
            assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("69.99");
        }

        @Test
        @DisplayName("scale is normalized even when balance stored with extra scale")
        void normalizesBalanceScale() {
            Wallet wallet = walletWithBalance("100.000");
            when(walletMapper.selectOne(any())).thenReturn(wallet);
            when(walletMapper.selectForUpdate(USER_ID)).thenReturn(wallet);
            when(walletMapper.deductBalance(eq(USER_ID), any())).thenReturn(1);

            walletService.deductForPayment(
                    USER_ID, new BigDecimal("30.00"), "PRODUCT_ORDER", 1L, "idem-scale");

            ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
            verify(walletTransactionMapper).insert(captor.capture());
            // balanceBefore must be normalized to 2 decimals
            assertThat(captor.getValue().getBalanceBefore().scale()).isEqualTo(2);
            assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("70.00");
        }
    }

    @Nested
    @DisplayName("Reason validation")
    class ReasonValidation {

        @Test
        @DisplayName("reason longer than 500 chars rejected")
        void reasonTooLong() {
            String longReason = "r".repeat(501);
            assertThatThrownBy(() -> walletService.rechargeByAdmin(
                    USER_ID, new BigDecimal("100.00"), longReason, ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("adjust with reason over 500 chars rejected before any balance change")
        void adjustReasonTooLong() {
            String longReason = "r".repeat(501);
            assertThatThrownBy(() -> walletService.adjustByAdmin(
                    USER_ID, new BigDecimal("10.00"), "DEBIT", longReason, ADMIN_ID))
                    .isInstanceOf(BusinessException.class);

            verify(walletMapper, never()).deductBalance(any(), any());
            verify(walletMapper, never()).addBalance(any(), any());
        }
    }

    @Nested
    @DisplayName("getMyWallet (read-only)")
    class GetMyWallet {

        @Test
        @DisplayName("returns zero-balance view when user has no wallet yet (no insert performed)")
        void returnsZeroBalanceViewForNewUser() {
            when(walletMapper.selectOne(any())).thenReturn(null);

            WalletDtos.WalletResponse response = walletService.getMyWallet(USER_ID);

            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.balance()).isEqualByComparingTo("0.00");
            assertThat(response.frozenAmount()).isEqualByComparingTo("0.00");
            assertThat(response.id()).isNull();
            // CRITICAL: read-only path must never insert a wallet row.
            verify(walletMapper, never()).insert(any(Wallet.class));
        }

        @Test
        @DisplayName("returns actual balance when wallet exists")
        void returnsActualBalance() {
            Wallet wallet = walletWithBalance("123.45");
            when(walletMapper.selectOne(any())).thenReturn(wallet);

            WalletDtos.WalletResponse response = walletService.getMyWallet(USER_ID);

            assertThat(response.balance()).isEqualByComparingTo("123.45");
        }
    }

    @Nested
    @DisplayName("lockWalletForUpdate")
    class LockWalletForUpdate {

        @Test
        @DisplayName("throws WALLET_NOT_FOUND when wallet row disappears after getOrCreate")
        void walletNotFoundAfterCreate() {
            // getOrCreateWallet succeeds (returns a wallet), but selectForUpdate returns null
            // (simulating a concurrent hard-delete between the two calls).
            Wallet wallet = walletWithBalance("50.00");
            when(walletMapper.selectOne(any())).thenReturn(wallet);
            when(walletMapper.selectForUpdate(USER_ID)).thenReturn(null);

            assertThatThrownBy(() -> walletService.lockWalletForUpdate(USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.WALLET_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("listAccounts (Bug1 regression: unrecharged users must appear)")
    class ListAccounts {

        @Test
        @DisplayName("returns ALL users including those without a wallet (zero balance, null walletId)")
        void includesUsersWithoutWallet() {
            // Two users: 1001 has a wallet (balance 100), 1002 has none.
            User u1 = activeUser(); // userId=USER_ID=1001
            User u2 = new User();
            u2.setId(2002L);
            u2.setNickname("未充值用户");
            u2.setPhone("13900000000");
            u2.setDeleted(0);
            Page<User> userPage = new Page<>(1, 10);
            userPage.setRecords(List.of(u1, u2));
            userPage.setTotal(2);
            when(userMapper.selectPage(any(Page.class), any())).thenReturn(userPage);
            // Only u1's wallet exists in DB.
            Wallet w1 = walletWithBalance("100.00");
            w1.setId(7777L);
            when(walletMapper.selectList(any())).thenReturn(List.of(w1));

            List<WalletDtos.WalletAccountRow> rows = walletService.listAccounts(null, null, 1, 10);

            assertThat(rows).hasSize(2);
            // u1: has wallet
            WalletDtos.WalletAccountRow row1 = rows.stream()
                    .filter(r -> r.userId().equals(USER_ID)).findFirst().orElseThrow();
            assertThat(row1.walletId()).isEqualTo(7777L);
            assertThat(row1.balance()).isEqualByComparingTo("100.00");
            // u2: no wallet — still appears with zero balance and null walletId
            WalletDtos.WalletAccountRow row2 = rows.stream()
                    .filter(r -> r.userId().equals(2002L)).findFirst().orElseThrow();
            assertThat(row2.walletId()).isNull();
            assertThat(row2.balance()).isEqualByComparingTo("0.00");
            assertThat(row2.userNickname()).isEqualTo("未充值用户");
        }

        @Test
        @DisplayName("countAccounts counts users (not wallets)")
        void countAccountsCountsUsers() {
            when(userMapper.selectCount(any())).thenReturn(5L);
            assertThat(walletService.countAccounts(null, null)).isEqualTo(5L);
        }

        @Test
        @DisplayName("userIdKeyword filter is applied (CAST id AS CHAR LIKE)")
        void userIdKeywordFilterApplied() {
            // When userIdKeyword is provided, buildUserQuery must add a CAST(id AS CHAR) LIKE
            // clause. We assert the call flows through (selectPage invoked) and returns the
            // expected users with zero-balance rows for those without a wallet.
            User u1 = activeUser();
            Page<User> userPage = new Page<>(1, 10);
            userPage.setRecords(List.of(u1));
            userPage.setTotal(1);
            when(userMapper.selectPage(any(Page.class), any())).thenReturn(userPage);
            when(walletMapper.selectList(any())).thenReturn(List.of());

            List<WalletDtos.WalletAccountRow> rows =
                    walletService.listAccounts(null, "1001", 1, 10);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).userId()).isEqualTo(USER_ID);
        }
    }

    private User activeUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setDeleted(0);
        return user;
    }

    private Wallet walletWithBalance(String balance) {
        Wallet wallet = new Wallet();
        wallet.setId(1L);
        wallet.setUserId(USER_ID);
        wallet.setBalance(new BigDecimal(balance));
        wallet.setFrozenAmount(new BigDecimal("0.00"));
        wallet.setVersion(0);
        return wallet;
    }
}
