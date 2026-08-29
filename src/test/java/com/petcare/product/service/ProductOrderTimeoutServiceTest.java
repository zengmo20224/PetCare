package com.petcare.product.service;

import com.petcare.product.entity.Product;
import com.petcare.product.entity.ProductOrder;
import com.petcare.product.entity.ProductOrderItem;
import com.petcare.product.enums.ProductOrderStatus;
import com.petcare.product.mapper.ProductMapper;
import com.petcare.product.mapper.ProductOrderItemMapper;
import com.petcare.product.mapper.ProductOrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 超时未支付商品订单自动取消（H2 集成）。
 * 统一时钟 NOW=2026-08-29 15:00，默认宽限 1440 分钟 → 过期线为 2026-08-28 15:00。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductOrderTimeoutServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 29, 15, 0);
    private static final String TIMEOUT_REASON = "超时未支付，系统自动取消";

    @Autowired
    private ProductOrderTimeoutService productOrderTimeoutService;
    @Autowired
    private ProductOrderMapper productOrderMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductOrderItemMapper productOrderItemMapper;

    @Test
    @DisplayName("隔夜未支付订单被取消：状态 CANCELLED、原因落 merchant_remark、库存回补")
    void cancelsOverdueUnpaidOrderAndRestoresStock() {
        Product product = insertProduct(3);
        ProductOrder order = insertOrder("UNPAID", "OFFLINE_STORE",
                ProductOrderStatus.PENDING_CONFIRM.getCode(), NOW.minusHours(48));
        insertOrderItem(order.getId(), product.getId(), 2);

        int cancelled = productOrderTimeoutService.cancelOverdueUnpaidOrders(NOW);

        assertThat(cancelled).isEqualTo(1);
        ProductOrder reloaded = productOrderMapper.selectById(order.getId());
        assertThat(reloaded.getStatus()).isEqualTo(ProductOrderStatus.CANCELLED.getCode());
        assertThat(reloaded.getCancelTime()).isNotNull();
        assertThat(reloaded.getMerchantRemark()).isEqualTo(TIMEOUT_REASON);
        assertThat(productMapper.selectStock(product.getId())).isEqualTo(5);
    }

    @Test
    @DisplayName("已钱包支付的过期订单不被取消，库存不动")
    void keepsWalletPaidOverdueOrder() {
        Product product = insertProduct(3);
        ProductOrder order = insertOrder("WALLET_PAID", "WALLET",
                ProductOrderStatus.PENDING_CONFIRM.getCode(), NOW.minusHours(48));
        insertOrderItem(order.getId(), product.getId(), 2);

        int cancelled = productOrderTimeoutService.cancelOverdueUnpaidOrders(NOW);

        assertThat(cancelled).isZero();
        ProductOrder reloaded = productOrderMapper.selectById(order.getId());
        assertThat(reloaded.getStatus()).isEqualTo(ProductOrderStatus.PENDING_CONFIRM.getCode());
        assertThat(productMapper.selectStock(product.getId())).isEqualTo(3);
    }

    @Test
    @DisplayName("宽限期内（当日创建）的未支付订单不被取消")
    void keepsRecentUnpaidOrder() {
        Product product = insertProduct(3);
        ProductOrder order = insertOrder("UNPAID", "OFFLINE_STORE",
                ProductOrderStatus.PENDING_CONFIRM.getCode(), NOW.minusHours(1));
        insertOrderItem(order.getId(), product.getId(), 2);

        int cancelled = productOrderTimeoutService.cancelOverdueUnpaidOrders(NOW);

        assertThat(cancelled).isZero();
        assertThat(productOrderMapper.selectById(order.getId()).getStatus())
                .isEqualTo(ProductOrderStatus.PENDING_CONFIRM.getCode());
        assertThat(productMapper.selectStock(product.getId())).isEqualTo(3);
    }

    @Test
    @DisplayName("已进入备货（PREPARING）的过期未支付订单不被定时任务取消")
    void keepsPreparingOverdueOrder() {
        Product product = insertProduct(3);
        ProductOrder order = insertOrder("UNPAID", "OFFLINE_STORE",
                ProductOrderStatus.PREPARING.getCode(), NOW.minusHours(48));
        insertOrderItem(order.getId(), product.getId(), 2);

        int cancelled = productOrderTimeoutService.cancelOverdueUnpaidOrders(NOW);

        assertThat(cancelled).isZero();
        assertThat(productOrderMapper.selectById(order.getId()).getStatus())
                .isEqualTo(ProductOrderStatus.PREPARING.getCode());
        assertThat(productMapper.selectStock(product.getId())).isEqualTo(3);
    }

    private Product insertProduct(int stock) {
        Product product = new Product();
        product.setCategoryId(1L);
        product.setName("超时测试商品-" + System.nanoTime());
        product.setPrice(new BigDecimal("50.00"));
        product.setStock(stock);
        product.setSalesCount(0);
        product.setPickupOnly(1);
        product.setStatus("ON_SALE");
        product.setSort(0);
        productMapper.insert(product);
        return product;
    }

    private ProductOrder insertOrder(String paymentStatus, String paymentMethod,
                                     String status, LocalDateTime createTime) {
        ProductOrder order = new ProductOrder();
        order.setOrderNo("PO-TIMEOUT-" + System.nanoTime());
        order.setUserId(9002L);
        order.setStoreId(1L);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setDeliveryMethod("PICKUP");
        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(paymentStatus);
        order.setPickupStatus("WAIT_PREPARE");
        order.setStatus(status);
        order.setContactName("超时测试用户");
        order.setContactPhone("13800000000");
        order.setCreateTime(createTime);
        productOrderMapper.insert(order);
        return order;
    }

    private void insertOrderItem(Long orderId, Long productId, int quantity) {
        ProductOrderItem item = new ProductOrderItem();
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setProductName("超时测试商品");
        item.setPrice(new BigDecimal("50.00"));
        item.setQuantity(quantity);
        item.setTotalAmount(new BigDecimal("100.00"));
        productOrderItemMapper.insert(item);
    }
}
