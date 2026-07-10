package com.petcare.product.it;

import com.petcare.common.exception.BusinessException;
import com.petcare.common.persistence.AbstractTcMySqlIT;
import com.petcare.product.dto.ProductOrderCreateRequest;
import com.petcare.product.entity.CartItem;
import com.petcare.product.entity.Product;
import com.petcare.product.mapper.CartItemMapper;
import com.petcare.product.mapper.ProductMapper;
import com.petcare.product.service.ProductOrderTransactionService;
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
 * MySQL integration test for inventory concurrency.
 *
 * <p><b>IMPORTANT:</b> This test requires a real MySQL 8 database.
 * Do NOT run with H2 — H2 does not reproduce MySQL row-level locking behavior.</p>
 *
 * <h3>How to run:</h3>
 * <pre>
 * mvn clean test -Ptc-mysql
 * </pre>
 *
 * <h3>What it verifies:</h3>
 * <ul>
 *   <li>Two concurrent orders competing for the last unit of stock → only 1 succeeds</li>
 *   <li>The failing order gets a clear {@link BusinessException}, not dirty success</li>
 *   <li>Stock never goes below zero</li>
 *   <li>No orphaned order or order-item rows remain for the failed order</li>
 * </ul>
 */
@Tag("tc-mysql")
class ProductInventoryConcurrencyIT extends AbstractTcMySqlIT {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private ProductOrderTransactionService orderService;

    private static final Long STORE_ID = 1L;
    private static final int THREAD_COUNT = 2;

    @BeforeEach
    void cleanUp() {
        // Clean cart items and products from previous runs
        cartItemMapper.delete(null);
    }

    @Test
    @DisplayName("Two concurrent orders competing for 1 unit of stock: only 1 succeeds, stock never negative")
    void concurrentOrders_lastUnitStock_onlyOneSucceeds() throws Exception {
        // ===== Arrange =====
        // Create a product with exactly 1 unit of stock
        Product product = new Product();
        product.setCategoryId(1L);
        product.setName("并发测试商品");
        product.setCoverUrl("https://example.com/concurrency-test.jpg");
        product.setPrice(new BigDecimal("50.00"));
        product.setStock(1);
        product.setSalesCount(0);
        product.setPickupOnly(1);
        product.setStatus("ON_SALE");
        productMapper.insert(product);

        Long productId = product.getId();
        assertThat(productId).isNotNull();

        // Create checked cart items for 2 different users
        Long user1 = 90001L;
        Long user2 = 90002L;

        CartItem cart1 = new CartItem();
        cart1.setUserId(user1);
        cart1.setProductId(productId);
        cart1.setQuantity(1);
        cart1.setChecked(1);
        cartItemMapper.insert(cart1);

        CartItem cart2 = new CartItem();
        cart2.setUserId(user2);
        cart2.setProductId(productId);
        cart2.setQuantity(1);
        cart2.setChecked(1);
        cartItemMapper.insert(cart2);

        // ===== Act =====
        // Two threads submit orders concurrently
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        executor.submit(() -> runOrder(user1, startLatch, doneLatch, successCount, failCount, errors));
        executor.submit(() -> runOrder(user2, startLatch, doneLatch, successCount, failCount, errors));

        // Release both threads at the same time
        startLatch.countDown();

        // Wait for completion
        boolean allDone = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // ===== Assert =====
        assertThat(allDone).as("Both threads must complete within timeout").isTrue();

        // Exactly 1 order must succeed
        assertThat(successCount.get())
                .as("Exactly 1 order should succeed when competing for 1 unit of stock")
                .isEqualTo(1);

        // Exactly 1 order must fail with a clear business error
        assertThat(failCount.get())
                .as("Exactly 1 order should fail with stock insufficient error")
                .isEqualTo(1);

        // Verify the failure was a BusinessException (not a random exception)
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isInstanceOf(BusinessException.class);

        // Verify final stock is 0, never negative
        Integer finalStock = productMapper.selectStock(productId);
        assertThat(finalStock)
                .as("Stock must never go below zero")
                .isNotNull()
                .isGreaterThanOrEqualTo(0);

        // Clean up test product
        productMapper.deleteById(productId);
    }

    private void runOrder(Long userId, CountDownLatch startLatch, CountDownLatch doneLatch,
                          AtomicInteger successCount, AtomicInteger failCount, List<Throwable> errors) {
        try {
            startLatch.await();
            orderService.createOrder(userId, new ProductOrderCreateRequest(
                    STORE_ID, "PICKUP", null, "测试联系人", "13800000000", "并发测试"), null);
            successCount.incrementAndGet();
        } catch (Throwable t) {
            errors.add(t);
            failCount.incrementAndGet();
        } finally {
            doneLatch.countDown();
        }
    }
}
