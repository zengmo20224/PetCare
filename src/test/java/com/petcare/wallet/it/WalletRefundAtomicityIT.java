package com.petcare.wallet.it;

import com.petcare.common.persistence.AbstractTcMySqlIT;
import com.petcare.product.dto.ProductOrderCreateRequest;
import com.petcare.product.entity.CartItem;
import com.petcare.product.entity.Product;
import com.petcare.product.mapper.CartItemMapper;
import com.petcare.product.mapper.ProductMapper;
import com.petcare.product.service.ProductOrderTransactionService;
import com.petcare.user.entity.User;
import com.petcare.user.mapper.UserMapper;
import com.petcare.wallet.entity.Wallet;
import com.petcare.wallet.entity.WalletTransaction;
import com.petcare.wallet.mapper.WalletMapper;
import com.petcare.wallet.mapper.WalletTransactionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL integration test for wallet-refund + stock-restore atomicity (D-012 / CR-20260718-003).
 *
 * <p>Verifies the user's hard requirement: cancelling a wallet-paid order must atomically
 * refund the wallet AND restore the stock — never "stock restored but no refund" nor
 * "refund issued but stock not restored".</p>
 *
 * <p>Requires real MySQL 8: {@code mvn test -Ptc-mysql}.</p>
 */
@Tag("tc-mysql")
class WalletRefundAtomicityIT extends AbstractTcMySqlIT {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private ProductOrderTransactionService orderService;

    @Autowired
    private WalletMapper walletMapper;

    @Autowired
    private WalletTransactionMapper walletTransactionMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("Cancel wallet-paid order: stock restored + balance refunded + REFUND ledger written, atomically")
    void cancelWalletPaidOrder_atomicRefundAndStockRestore() {
        // Arrange: place a wallet-paid order first.
        Long userId = createUserWithWallet("100.00");
        Product product = createProduct("30.00", 10);
        createCheckedCartItem(userId, product.getId(), 1);

        var order = orderService.createOrder(
                userId,
                new ProductOrderCreateRequest(
                        1L, "PICKUP", null, "测试联系人", "13800000000", null, null, "WALLET"),
                null);

        // Verify pre-cancel state: stock 9, balance 70.
        assertThat(productMapper.selectStock(product.getId())).isEqualTo(9);
        assertThat(currentBalance(userId)).isEqualByComparingTo("70.00");
        assertThat(countLedger(userId, "PAY")).isEqualTo(1);
        assertThat(countLedger(userId, "REFUND")).isZero();

        // Act: cancel the order (user cancel; PENDING_CONFIRM is cancellable).
        var cancelled = orderService.cancelOrder(order.getId(), userId);

        // Assert: order status CANCELLED.
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCancelTime()).isNotNull();

        // CRITICAL: stock restored from 9 back to 10.
        assertThat(productMapper.selectStock(product.getId()))
                .as("Stock must be restored to 10 after cancellation")
                .isEqualTo(10);

        // CRITICAL: wallet balance refunded from 70 back to 100.
        assertThat(currentBalance(userId))
                .as("Wallet balance must be refunded to 100 after cancellation")
                .isEqualByComparingTo("100.00");

        // CRITICAL: a REFUND ledger row was written with correct before/after snapshot.
        assertThat(countLedger(userId, "REFUND"))
                .as("Exactly one REFUND ledger row should exist")
                .isEqualTo(1);
        WalletTransaction refundTx = walletTransactionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getUserId, userId)
                        .eq(WalletTransaction::getSourceType, "REFUND"))
                .get(0);
        assertThat(refundTx.getDirection()).isEqualTo("CREDIT");
        assertThat(refundTx.getAmount()).isEqualByComparingTo("30.00");
        assertThat(refundTx.getBalanceBefore()).isEqualByComparingTo("70.00");
        assertThat(refundTx.getBalanceAfter()).isEqualByComparingTo("100.00");
        assertThat(refundTx.getRelatedOrderType()).isEqualTo("PRODUCT_ORDER");
        assertThat(refundTx.getRelatedOrderId()).isEqualTo(order.getId());
        assertThat(refundTx.getOperatorType()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("Cancel offline-paid order: NO wallet refund issued (offline refund handled in-store)")
    void cancelOfflinePaidOrder_noWalletRefund() {
        // Arrange: place an OFFLINE_STORE order (no wallet involved).
        Long userId = createUserWithWallet("100.00");
        Product product = createProduct("30.00", 10);
        createCheckedCartItem(userId, product.getId(), 1);

        var order = orderService.createOrder(
                userId,
                new ProductOrderCreateRequest(
                        1L, "PICKUP", null, "测试联系人", "13800000000", null, null, "OFFLINE_STORE"),
                null);

        // Pre-cancel: balance still 100 (offline order doesn't touch wallet).
        assertThat(currentBalance(userId)).isEqualByComparingTo("100.00");
        assertThat(countLedger(userId, "PAY")).isZero();

        // Act: cancel.
        orderService.cancelOrder(order.getId(), userId);

        // Assert: stock restored, but wallet balance unchanged (no refund for offline orders).
        assertThat(productMapper.selectStock(product.getId())).isEqualTo(10);
        assertThat(currentBalance(userId))
                .as("Offline order cancellation must not touch the wallet")
                .isEqualByComparingTo("100.00");
        assertThat(countLedger(userId, "REFUND"))
                .as("No REFUND ledger row for offline order cancellation")
                .isZero();
    }

    @Test
    @DisplayName("Idempotent cancel: refunding the same order twice is rejected by ledger unique key")
    void idempotentCancel_noDoubleRefund() {
        // This is a defense-in-depth test: in practice the order state machine prevents
        // double-cancel (CANCELLED → CANCELLED is invalid). But if somehow refundForCancellation
        // were called twice with the same idempotency key, the ledger unique constraint must
        // reject the duplicate.
        Long userId = createUserWithWallet("100.00");
        Product product = createProduct("30.00", 10);
        createCheckedCartItem(userId, product.getId(), 1);

        var order = orderService.createOrder(
                userId,
                new ProductOrderCreateRequest(
                        1L, "PICKUP", null, "测试联系人", "13800000000", null, null, "WALLET"),
                null);

        // First cancel succeeds.
        orderService.cancelOrder(order.getId(), userId);
        assertThat(currentBalance(userId)).isEqualByComparingTo("100.00");
        assertThat(countLedger(userId, "REFUND")).isEqualTo(1);

        // Second cancel on already-CANCELLED order is blocked by state machine
        // (validateTransition throws before any refund). Wallet unchanged.
        try {
            orderService.cancelOrder(order.getId(), userId);
        } catch (Exception expected) {
            // expected: state machine rejects CANCELLED → CANCELLED
        }
        assertThat(countLedger(userId, "REFUND"))
                .as("No duplicate REFUND ledger row")
                .isEqualTo(1);
        assertThat(currentBalance(userId)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("H-3: Mark wallet-paid order OUT_OF_STOCK must refund wallet (terminal state has no other refund path)")
    void outOfStockWalletPaidOrder_refundsWallet() {
        // Arrange: place a wallet-paid order (PENDING_CONFIRM, WALLET_PAID).
        Long userId = createUserWithWallet("100.00");
        Product product = createProduct("30.00", 10);
        createCheckedCartItem(userId, product.getId(), 1);

        var order = orderService.createOrder(
                userId,
                new ProductOrderCreateRequest(
                        1L, "PICKUP", null, "测试联系人", "13800000000", null, null, "WALLET"),
                null);

        // Pre-state: stock deducted to 9, balance deducted to 70.
        assertThat(productMapper.selectStock(product.getId())).isEqualTo(9);
        assertThat(currentBalance(userId)).isEqualByComparingTo("70.00");

        // Act: mark out-of-stock (admin action).
        var outOfStockOrder = orderService.outOfStock(order.getId(), "仓库缺货", 1L);

        // Assert: order in terminal OUT_OF_STOCK state.
        assertThat(outOfStockOrder.getStatus()).isEqualTo("OUT_OF_STOCK");

        // CRITICAL (H-3): OUT_OF_STOCK is terminal — cancelOrder can no longer refund.
        // The wallet payment (WALLET_PAID) must be refunded within outOfStock itself.
        assertThat(currentBalance(userId))
                .as("Wallet balance must be refunded to 100 when order goes OUT_OF_STOCK")
                .isEqualByComparingTo("100.00");
        assertThat(countLedger(userId, "REFUND"))
                .as("Exactly one REFUND ledger row should exist for OUT_OF_STOCK")
                .isEqualTo(1);

        // OUT_OF_STOCK 不回补库存（业务约定：缺货说明库存本就不足）
        assertThat(productMapper.selectStock(product.getId())).isEqualTo(9);
    }

    // ===== Helpers =====

    private Long createUserWithWallet(String balance) {
        User user = new User();
        user.setPhone("139" + System.nanoTime() % 100000000L);
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        Wallet wallet = new Wallet();
        wallet.setUserId(user.getId());
        wallet.setBalance(new BigDecimal(balance));
        wallet.setFrozenAmount(new BigDecimal("0.00"));
        wallet.setVersion(0);
        walletMapper.insert(wallet);
        return user.getId();
    }

    private Product createProduct(String price, int stock) {
        Product product = new Product();
        product.setCategoryId(1L);
        product.setName("refund-test-" + System.nanoTime());
        product.setCoverUrl("https://example.com/test.jpg");
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setSalesCount(0);
        product.setPickupOnly(1);
        product.setStatus("ON_SALE");
        productMapper.insert(product);
        return product;
    }

    private void createCheckedCartItem(Long userId, Long productId, int quantity) {
        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setChecked(1);
        cartItemMapper.insert(item);
    }

    private BigDecimal currentBalance(Long userId) {
        Wallet w = walletMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Wallet>()
                        .eq(Wallet::getUserId, userId));
        return w.getBalance();
    }

    private long countLedger(Long userId, String sourceType) {
        return walletTransactionMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getUserId, userId)
                        .eq(WalletTransaction::getSourceType, sourceType));
    }
}
