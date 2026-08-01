package com.petcare.wallet.it;

import com.petcare.common.exception.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MySQL integration test for wallet-payment + stock-deduction atomicity (D-012 / CR-20260718-003).
 *
 * <p>Verifies the user's hard requirement: wallet deduction and stock deduction must be in the
 * same transaction — never "deducted money without stock" nor "deducted stock without money".</p>
 *
 * <p>Three paths are covered:</p>
 * <ol>
 *   <li><b>Insufficient balance</b>: order fails → stock unchanged + balance unchanged.</li>
 *   <li><b>Insufficient stock</b>: order fails → wallet unchanged (no ledger row).</li>
 *   <li><b>Happy path</b>: order succeeds → stock decremented + balance decremented +
 *       paymentStatus=WALLET_PAID + a PAY ledger row written.</li>
 * </ol>
 *
 * <p>Requires real MySQL 8: {@code mvn test -Ptc-mysql}.</p>
 */
@Tag("tc-mysql")
class WalletPaymentAtomicityIT extends AbstractTcMySqlIT {

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
    @DisplayName("Insufficient wallet balance: order fails, stock NOT deducted, balance unchanged")
    void insufficientBalance_noStockDeduction_noBalanceChange() {
        // Arrange: product price 50, stock 5; wallet balance only 30.
        Long userId = createUserWithWallet("30.00");
        Product product = createProduct("50.00", 5);
        createCheckedCartItem(userId, product.getId(), 1);

        BigDecimal balanceBefore = currentBalance(userId);

        // Act + Assert: order must fail with WALLET_BALANCE_INSUFFICIENT
        assertThatThrownBy(() -> orderService.createOrder(
                userId,
                new ProductOrderCreateRequest(
                        1L, "PICKUP", null, "测试联系人", "13800000000", null, null, "WALLET"),
                null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("wallet_balance_insufficient");

        // CRITICAL: stock must NOT be deducted (atomic rollback).
        assertThat(productMapper.selectStock(product.getId()))
                .as("Stock must be restored to 5 after wallet-insufficient rollback")
                .isEqualTo(5);
        // CRITICAL: wallet balance must be unchanged.
        assertThat(currentBalance(userId))
                .as("Wallet balance must be unchanged after failed payment")
                .isEqualByComparingTo(balanceBefore);
        // No PAY ledger row should exist.
        assertThat(countPayLedger(userId)).isZero();
    }

    @Test
    @DisplayName("Insufficient stock: order fails, wallet NOT deducted (no ledger row)")
    void insufficientStock_noWalletDeduction() {
        // Arrange: product stock 0 (or 1 with quantity 2), wallet balance 1000 (plenty).
        Long userId = createUserWithWallet("1000.00");
        Product product = createProduct("50.00", 1);
        // Cart requests quantity 2 but only 1 in stock.
        createCheckedCartItem(userId, product.getId(), 2);

        BigDecimal balanceBefore = currentBalance(userId);

        // Act + Assert: order fails with PRODUCT_STOCK_INSUFFICIENT
        assertThatThrownBy(() -> orderService.createOrder(
                userId,
                new ProductOrderCreateRequest(
                        1L, "PICKUP", null, "测试联系人", "13800000000", null, null, "WALLET"),
                null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("product_stock_insufficient");

        // CRITICAL: stock must remain 1 (deduction rolled back).
        assertThat(productMapper.selectStock(product.getId()))
                .as("Stock must remain 1 after stock-insufficient rollback")
                .isEqualTo(1);
        // CRITICAL: wallet balance must be unchanged.
        assertThat(currentBalance(userId))
                .as("Wallet balance must be unchanged when stock deduction failed")
                .isEqualByComparingTo(balanceBefore);
        // No PAY ledger row.
        assertThat(countPayLedger(userId)).isZero();
    }

    @Test
    @DisplayName("Happy path: wallet payment succeeds, stock + balance both decremented atomically")
    void happyPath_walletPayment_atomicDeduction() {
        // Arrange: product price 30, stock 10; wallet balance 100.
        Long userId = createUserWithWallet("100.00");
        Product product = createProduct("30.00", 10);
        createCheckedCartItem(userId, product.getId(), 1);

        // Act
        var order = orderService.createOrder(
                userId,
                new ProductOrderCreateRequest(
                        1L, "PICKUP", null, "测试联系人", "13800000000", null, null, "WALLET"),
                null);

        // Assert: order created with WALLET_PAID
        assertThat(order.getPaymentMethod()).isEqualTo("WALLET");
        assertThat(order.getPaymentStatus()).isEqualTo("WALLET_PAID");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("30.00");

        // Stock decremented from 10 to 9.
        assertThat(productMapper.selectStock(product.getId()))
                .as("Stock must be decremented to 9 after successful order")
                .isEqualTo(9);

        // Wallet balance decremented from 100 to 70.
        assertThat(currentBalance(userId))
                .as("Wallet balance must be decremented to 70 after paying 30")
                .isEqualByComparingTo("70.00");

        // Exactly one PAY ledger row, amount 30, balanceAfter 70.
        assertThat(countPayLedger(userId))
                .as("Exactly one PAY ledger row should exist")
                .isEqualTo(1);
        WalletTransaction payTx = walletTransactionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getUserId, userId)
                        .eq(WalletTransaction::getSourceType, "PAY"))
                .get(0);
        assertThat(payTx.getAmount()).isEqualByComparingTo("30.00");
        assertThat(payTx.getBalanceBefore()).isEqualByComparingTo("100.00");
        assertThat(payTx.getBalanceAfter()).isEqualByComparingTo("70.00");
        assertThat(payTx.getRelatedOrderType()).isEqualTo("PRODUCT_ORDER");
    }

    // ===== Helpers =====

    private Long createUserWithWallet(String balance) {
        User user = new User();
        user.setPhone("139" + System.nanoTime() % 100000000L);
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        // Insert wallet directly (bypass service to control initial balance).
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
        product.setName("atomicity-test-" + System.nanoTime());
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

    private long countPayLedger(Long userId) {
        return walletTransactionMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getUserId, userId)
                        .eq(WalletTransaction::getSourceType, "PAY"));
    }
}
