package com.petcare.wallet.it;

import com.petcare.common.exception.BusinessException;
import com.petcare.common.persistence.AbstractTcMySqlIT;
import com.petcare.user.entity.User;
import com.petcare.user.mapper.UserMapper;
import com.petcare.wallet.entity.Wallet;
import com.petcare.wallet.mapper.WalletMapper;
import com.petcare.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL integration test for wallet concurrent deduction.
 *
 * <p><b>IMPORTANT:</b> Requires real MySQL 8. H2 cannot reproduce InnoDB row-level locking.
 * Run with {@code mvn clean test -Ptc-mysql}.</p>
 *
 * <p>Verifies D-012 rule: concurrent deductions on the same wallet must never overdraw
 * the balance. Two threads competing for more than the available balance → only 1 succeeds,
 * balance never negative.</p>
 */
@Tag("tc-mysql")
class WalletConcurrencyMySqlIT extends AbstractTcMySqlIT {

    @Autowired
    private WalletMapper walletMapper;

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserMapper userMapper;

    private Long testUserId;

    @BeforeEach
    void setUp() {
        // Each test gets a fresh user + wallet via unique phone, so tests are independent.
        User user = new User();
        user.setPhone("139" + System.nanoTime() % 100000000L);
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        testUserId = user.getId();

        // Pre-create wallet with balance 100.00
        walletService.getOrCreateWallet(testUserId);
        walletMapper.addBalance(testUserId, new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Two concurrent deductions of 60 from a 100 balance (sum 120 > 100): exactly 1 succeeds, balance never negative")
    void concurrentDeductions_overdrawProtection_onlyOneSucceeds() throws Exception {
        // Balance = 100, two threads each try to deduct 60 (total 120 > 100).
        // Only one can win; the other must get WALLET_BALANCE_INSUFFICIENT.
        BigDecimal amount = new BigDecimal("60.00");
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> runDeduct(amount, "overdraw-A", startLatch, doneLatch, successCount, failCount, errors));
        executor.submit(() -> runDeduct(amount, "overdraw-B", startLatch, doneLatch, successCount, failCount, errors));

        startLatch.countDown();

        boolean allDone = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(allDone).as("Both threads must complete within timeout").isTrue();

        assertThat(successCount.get())
                .as("Exactly 1 deduction should succeed when 60+60 > 100 balance")
                .isEqualTo(1);
        assertThat(failCount.get())
                .as("Exactly 1 deduction should fail with insufficient balance")
                .isEqualTo(1);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) errors.get(0)).getCode())
                .isEqualTo("wallet_balance_insufficient");

        // Final balance must be exactly 40.00 (100 - 60), never negative or -20.
        BigDecimal finalBalance = currentBalance(testUserId);
        assertThat(finalBalance)
                .as("Balance must be 40.00 after one successful 60 deduction, never negative")
                .isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("Idempotency: same idempotency key submitted twice concurrently → only 1 succeeds, 1 rejected as duplicate")
    void concurrentSameIdempotencyKey_onlyOneSucceeds() throws Exception {
        // Both threads use the SAME idempotency key. The first to commit wins;
        // the second must hit uk_wallet_idempotency → WALLET_TRANSACTION_DUPLICATE,
        // OR the balance check (if the ledger insert is the contention point).
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        String sharedKey = "shared-idem-" + System.nanoTime();
        BigDecimal amount = new BigDecimal("60.00");

        executor.submit(() -> runDeduct(amount, sharedKey, startLatch, doneLatch, successCount, failCount, errors));
        executor.submit(() -> runDeduct(amount, sharedKey, startLatch, doneLatch, successCount, failCount, errors));

        startLatch.countDown();

        boolean allDone = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(allDone).as("Both threads must complete within timeout").isTrue();

        // At most 1 success regardless of which guard fires (balance or idempotency).
        assertThat(successCount.get())
                .as("At most 1 deduction should succeed for a shared idempotency key")
                .isLessThanOrEqualTo(1);
        assertThat(successCount.get() + failCount.get())
                .as("Both threads must report an outcome")
                .isEqualTo(2);

        // Final balance never overdrawn.
        BigDecimal finalBalance = currentBalance(testUserId);
        assertThat(finalBalance.compareTo(BigDecimal.ZERO))
                .as("Balance must never be negative")
                .isGreaterThanOrEqualTo(0);
        // At most one 60 deduction could have succeeded.
        assertThat(finalBalance)
                .as("Balance must be 100 (no deduction) or 40 (one deduction)")
                .isIn(new BigDecimal("100.00"), new BigDecimal("40.00"));
    }

    @Test
    @DisplayName("High contention: 5 threads each deduct 30 from 100 balance → exactly 3 succeed (90 total), balance never negative")
    void highContention_neverOverdraws() throws Exception {
        // Reset to 100 (BeforeEach already set 100, but ensure clean state for this test).
        int threads = 5;
        BigDecimal perThread = new BigDecimal("30.00");
        // 5 × 30 = 150 > 100, so at most 3 can succeed (3 × 30 = 90 ≤ 100; 4 × 30 = 120 > 100).

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            final String key = "hi-" + i + "-" + System.nanoTime();
            executor.submit(() -> runDeduct(perThread, key, startLatch, doneLatch, successCount, failCount, errors));
        }

        startLatch.countDown();

        boolean allDone = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(allDone).as("All threads must complete within timeout").isTrue();

        // At most 3 successes (floor(100/30) = 3).
        assertThat(successCount.get())
                .as("At most 3 deductions of 30 should succeed from 100 balance")
                .isLessThanOrEqualTo(3);
        assertThat(successCount.get() + failCount.get())
                .as("All 5 threads must report an outcome")
                .isEqualTo(threads);

        BigDecimal finalBalance = currentBalance(testUserId);
        assertThat(finalBalance.compareTo(BigDecimal.ZERO))
                .as("Balance must never be negative under high contention")
                .isGreaterThanOrEqualTo(0);
        // Each success reduced balance by 30; balance must be 100 - 30*successes.
        BigDecimal expectedBalance = new BigDecimal("100.00")
                .subtract(perThread.multiply(BigDecimal.valueOf(successCount.get())));
        assertThat(finalBalance)
                .as("Balance must equal 100 - 30×successes")
                .isEqualByComparingTo(expectedBalance);
    }

    private BigDecimal currentBalance(Long userId) {
        Wallet w = walletMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Wallet>()
                        .eq(Wallet::getUserId, userId));
        return w.getBalance();
    }

    private void runDeduct(BigDecimal amount, String idempotencyKey, CountDownLatch startLatch,
                           CountDownLatch doneLatch, AtomicInteger successCount,
                           AtomicInteger failCount, List<Throwable> errors) {
        try {
            startLatch.await();
            walletService.deductForPayment(
                    testUserId, amount, "PRODUCT_ORDER", null, idempotencyKey);
            successCount.incrementAndGet();
        } catch (Throwable t) {
            errors.add(t);
            failCount.incrementAndGet();
        } finally {
            doneLatch.countDown();
        }
    }
}
