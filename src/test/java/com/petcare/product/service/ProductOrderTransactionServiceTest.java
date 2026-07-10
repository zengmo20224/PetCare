package com.petcare.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.product.dto.ProductOrderCreateRequest;
import com.petcare.product.entity.CartItem;
import com.petcare.product.entity.Product;
import com.petcare.product.entity.ProductOrder;
import com.petcare.product.entity.ProductOrderItem;
import com.petcare.product.enums.PickupStatus;
import com.petcare.product.enums.ProductOrderStatus;
import com.petcare.product.mapper.CartItemMapper;
import com.petcare.product.mapper.ProductMapper;
import com.petcare.product.mapper.ProductOrderItemMapper;
import com.petcare.product.mapper.ProductOrderMapper;
import com.petcare.product.service.impl.ProductOrderIdempotencyHelper;
import com.petcare.product.service.impl.ProductOrderTransactionServiceImpl;
import com.petcare.user.mapper.UserAddressMapper;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ProductOrderTransactionServiceImpl}.
 * Uses Mockito mocks — no Spring context.
 *
 * Uses doReturn().when() stubbing to avoid ambiguous method resolution
 * caused by MyBatis-Plus BaseMapper overloaded methods.
 */
@ExtendWith(MockitoExtension.class)
class ProductOrderTransactionServiceTest {

    @Mock
    private CartItemMapper cartItemMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductOrderMapper orderMapper;

    @Mock
    private ProductOrderItemMapper orderItemMapper;

    @Mock
    private UserAddressMapper userAddressMapper;

    @Mock
    private ProductOrderIdempotencyHelper idempotencyHelper;

    @InjectMocks
    private ProductOrderTransactionServiceImpl orderService;

    private static final Long USER_ID = 1001L;
    private static final Long STORE_ID = 5001L;
    private static final Long PRODUCT_ID = 2001L;
    private static final Long ORDER_ID = 6001L;
    private static final Long CART_ITEM_ID = 3001L;
    private static final Long OPERATOR_ID = 9001L;

    private Product defaultProduct;
    private CartItem defaultCheckedCartItem;
    private ProductOrder defaultOrder;
    private ProductOrderItem defaultOrderItem;

    @BeforeEach
    void setUp() {
        defaultProduct = new Product();
        defaultProduct.setId(PRODUCT_ID);
        defaultProduct.setName("猫粮");
        defaultProduct.setCoverUrl("https://example.com/cat-food.jpg");
        defaultProduct.setPrice(new BigDecimal("99.00"));
        defaultProduct.setStock(50);
        defaultProduct.setSalesCount(10);
        defaultProduct.setPickupOnly(1);
        defaultProduct.setStatus("ON_SALE");
        defaultProduct.setDeleted(0);

        defaultCheckedCartItem = new CartItem();
        defaultCheckedCartItem.setId(CART_ITEM_ID);
        defaultCheckedCartItem.setUserId(USER_ID);
        defaultCheckedCartItem.setProductId(PRODUCT_ID);
        defaultCheckedCartItem.setQuantity(2);
        defaultCheckedCartItem.setChecked(1);

        defaultOrder = new ProductOrder();
        defaultOrder.setId(ORDER_ID);
        defaultOrder.setOrderNo("PO20260601100001");
        defaultOrder.setUserId(USER_ID);
        defaultOrder.setStoreId(STORE_ID);
        defaultOrder.setTotalAmount(new BigDecimal("198.00"));
        defaultOrder.setDeliveryMethod("PICKUP");
        defaultOrder.setPaymentMethod("OFFLINE_STORE");
        defaultOrder.setPaymentStatus("UNPAID");
        defaultOrder.setPickupStatus(PickupStatus.WAIT_PREPARE.getCode());
        defaultOrder.setStatus(ProductOrderStatus.PENDING_CONFIRM.getCode());
        defaultOrder.setContactName("张三");
        defaultOrder.setContactPhone("13800000000");
        defaultOrder.setRemark("请小心轻放");
        defaultOrder.setDeleted(0);

        defaultOrderItem = new ProductOrderItem();
        defaultOrderItem.setId(7001L);
        defaultOrderItem.setOrderId(ORDER_ID);
        defaultOrderItem.setProductId(PRODUCT_ID);
        defaultOrderItem.setProductName("猫粮");
        defaultOrderItem.setProductCoverUrl("https://example.com/cat-food.jpg");
        defaultOrderItem.setPrice(new BigDecimal("99.00"));
        defaultOrderItem.setQuantity(2);
        defaultOrderItem.setTotalAmount(new BigDecimal("198.00"));
    }

    // ==================== createOrder ====================

    @Nested
    @DisplayName("createOrder")
    class CreateOrderTests {

        @Test
        @DisplayName("Creating order with no checked cart items should throw CART_NO_CHECKED_ITEMS")
        void createOrder_noCheckedItems() {
            // Arrange
            doReturn(Collections.emptyList()).when(cartItemMapper).selectList(any());

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(
                    USER_ID, pickupOrderRequest(null), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.CART_NO_CHECKED_ITEMS);

            verify(orderMapper, never()).insert((ProductOrder) any());
        }

        @Test
        @DisplayName("Creating order with product that is OFF_SALE should throw PRODUCT_NOT_ON_SALE")
        void createOrder_productOffSale() {
            // Arrange
            doReturn(List.of(defaultCheckedCartItem)).when(cartItemMapper).selectList(any());

            defaultProduct.setStatus("OFF_SALE");
            doReturn(defaultProduct).when(productMapper).selectById(PRODUCT_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(
                    USER_ID, pickupOrderRequest(null), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);

            verify(orderMapper, never()).insert((ProductOrder) any());
        }

        @Test
        @DisplayName("Creating order with product not pickup_only should throw PRODUCT_NOT_PICKUP_ONLY")
        void createOrder_productNotPickupOnly() {
            // Arrange
            doReturn(List.of(defaultCheckedCartItem)).when(cartItemMapper).selectList(any());

            defaultProduct.setPickupOnly(0);
            doReturn(defaultProduct).when(productMapper).selectById(PRODUCT_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(
                    USER_ID, pickupOrderRequest(null), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_NOT_PICKUP_ONLY);

            verify(orderMapper, never()).insert((ProductOrder) any());
        }

        @Test
        @DisplayName("Creating order with product that is deleted should throw PRODUCT_NOT_ON_SALE")
        void createOrder_productDeleted() {
            // Arrange
            doReturn(List.of(defaultCheckedCartItem)).when(cartItemMapper).selectList(any());

            defaultProduct.setDeleted(1);
            doReturn(defaultProduct).when(productMapper).selectById(PRODUCT_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(
                    USER_ID, pickupOrderRequest(null), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);

            verify(orderMapper, never()).insert((ProductOrder) any());
        }

        @Test
        @DisplayName("Creating order with product that is null should throw PRODUCT_NOT_ON_SALE")
        void createOrder_productNull() {
            // Arrange
            doReturn(List.of(defaultCheckedCartItem)).when(cartItemMapper).selectList(any());
            doReturn(null).when(productMapper).selectById(PRODUCT_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(
                    USER_ID, pickupOrderRequest(null), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);
        }

        @Test
        @DisplayName("Creating order with insufficient stock should throw PRODUCT_STOCK_INSUFFICIENT")
        void createOrder_insufficientStock() {
            // Arrange
            doReturn(List.of(defaultCheckedCartItem)).when(cartItemMapper).selectList(any());
            doReturn(defaultProduct).when(productMapper).selectById(PRODUCT_ID);
            doReturn(0).when(productMapper).deductStock(PRODUCT_ID, 2);

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(
                    USER_ID, pickupOrderRequest(null), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_STOCK_INSUFFICIENT);

            verify(orderMapper, never()).insert((ProductOrder) any());
        }

        @Test
        @DisplayName("Creating order successfully should deduct stock, insert order and items, delete cart items")
        void createOrder_success() {
            // Arrange
            doReturn(List.of(defaultCheckedCartItem)).when(cartItemMapper).selectList(any());
            doReturn(defaultProduct).when(productMapper).selectById(PRODUCT_ID);
            doReturn(1).when(productMapper).deductStock(PRODUCT_ID, 2);
            doReturn(1).when(orderMapper).insert((ProductOrder) any());
            doReturn(1).when(orderItemMapper).insert((ProductOrderItem) any());
            doReturn(1).when(cartItemMapper).deleteByIds(any());

            // Act
            ProductOrder result = orderService.createOrder(
                    USER_ID, pickupOrderRequest("请小心轻放"), null);

            // Assert — order fields
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getStoreId()).isEqualTo(STORE_ID);
            assertThat(result.getDeliveryMethod()).isEqualTo("PICKUP");
            assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("198.00"));
            assertThat(result.getPaymentMethod()).isEqualTo("OFFLINE_STORE");
            assertThat(result.getPaymentStatus()).isEqualTo("UNPAID");
            assertThat(result.getPickupStatus()).isEqualTo(PickupStatus.WAIT_PREPARE.getCode());
            assertThat(result.getStatus()).isEqualTo(ProductOrderStatus.PENDING_CONFIRM.getCode());
            assertThat(result.getContactName()).isEqualTo("张三");
            assertThat(result.getContactPhone()).isEqualTo("13800000000");
            assertThat(result.getRemark()).isEqualTo("请小心轻放");
            assertThat(result.getOrderNo()).startsWith("PO");

            // Verify interactions
            verify(productMapper).deductStock(PRODUCT_ID, 2);
            verify(orderMapper).insert((ProductOrder) any());
            verify(orderItemMapper).insert((ProductOrderItem) any());
            verify(cartItemMapper).deleteByIds(List.of(CART_ITEM_ID));
        }

        @Test
        @DisplayName("Creating order with pickupOnly null should throw PRODUCT_NOT_PICKUP_ONLY")
        void createOrder_pickupOnlyNull() {
            // Arrange
            doReturn(List.of(defaultCheckedCartItem)).when(cartItemMapper).selectList(any());

            defaultProduct.setPickupOnly(null);
            doReturn(defaultProduct).when(productMapper).selectById(PRODUCT_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(
                    USER_ID, pickupOrderRequest(null), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_NOT_PICKUP_ONLY);
        }

        // ==================== 幂等（H1） ====================

        @Test
        @DisplayName("Idempotent create: 同一 userId + idempotencyKey 命中现存订单时，直接返回且不再扣库存/插单")
        void createOrder_idempotentHit_returnsExistingAndSkipsSideEffects() {
            // Arrange — helper 回查返回 defaultOrder
            doReturn(defaultOrder).when(idempotencyHelper).findByIdempotencyKey(USER_ID, "idem-key-abc");

            // Act
            ProductOrder result = orderService.createOrder(
                    USER_ID, pickupOrderRequest("重复请求"), "idem-key-abc");

            // Assert — 返回的是首次订单，未触发任何副作用
            assertThat(result).isSameAs(defaultOrder);
            verify(cartItemMapper, never()).selectList(any());
            verify(productMapper, never()).deductStock(anyLong(), anyInt());
            verify(orderMapper, never()).insert((ProductOrder) any());
            verify(orderItemMapper, never()).insert((ProductOrderItem) any());
            verify(cartItemMapper, never()).deleteByIds(any());
        }

        @Test
        @DisplayName("Idempotent create: 未命中时正常下单，并把 idempotencyKey 写入订单实体")
        void createOrder_idempotentMiss_createsNewOrderWithKey() {
            // Arrange — helper 未命中
            doReturn(null).when(idempotencyHelper).findByIdempotencyKey(USER_ID, "idem-key-new");
            doReturn(List.of(defaultCheckedCartItem)).when(cartItemMapper).selectList(any());
            doReturn(defaultProduct).when(productMapper).selectById(PRODUCT_ID);
            doReturn(1).when(productMapper).deductStock(PRODUCT_ID, 2);
            doReturn(1).when(orderMapper).insert((ProductOrder) any());
            doReturn(1).when(orderItemMapper).insert((ProductOrderItem) any());
            doReturn(1).when(cartItemMapper).deleteByIds(any());

            // Act
            ProductOrder result = orderService.createOrder(
                    USER_ID, pickupOrderRequest(null), "idem-key-new");

            // Assert — 正常下单 + key 写入实体
            assertThat(result).isNotNull();
            assertThat(result.getIdempotencyKey()).isEqualTo("idem-key-new");

            ArgumentCaptor<ProductOrder> captor = ArgumentCaptor.forClass(ProductOrder.class);
            verify(orderMapper).insert(captor.capture());
            assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("idem-key-new");
            verify(productMapper).deductStock(PRODUCT_ID, 2);
        }

        @Test
        @DisplayName("Idempotent create: 并发下 insert 触发 DuplicateKeyException 直接向上抛（由 app 层兜底回查）")
        void createOrder_idempotentRace_duplicateKeyPropagates() {
            // Arrange — helper 预检未命中，但 insert 时唯一约束冲突
            doReturn(null).when(idempotencyHelper).findByIdempotencyKey(USER_ID, "idem-key-race");
            doReturn(List.of(defaultCheckedCartItem)).when(cartItemMapper).selectList(any());
            doReturn(defaultProduct).when(productMapper).selectById(PRODUCT_ID);
            doReturn(1).when(productMapper).deductStock(PRODUCT_ID, 2);
            org.mockito.Mockito.doThrow(new org.springframework.dao.DuplicateKeyException("uk_user_idempotency"))
                    .when(orderMapper).insert((ProductOrder) any());

            // Act & Assert — 异常向上传播（app 层负责在新事务回查首次订单）
            assertThatThrownBy(() -> orderService.createOrder(
                    USER_ID, pickupOrderRequest(null), "idem-key-race"))
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        }

        @Test
        @DisplayName("Idempotent create: idempotencyKey 为 null 时不查现存订单（向后兼容）")
        void createOrder_nullKey_skipsIdempotencyCheck() {
            // Arrange — 不传 key，正常下单路径
            doReturn(List.of(defaultCheckedCartItem)).when(cartItemMapper).selectList(any());
            doReturn(defaultProduct).when(productMapper).selectById(PRODUCT_ID);
            doReturn(1).when(productMapper).deductStock(PRODUCT_ID, 2);
            doReturn(1).when(orderMapper).insert((ProductOrder) any());
            doReturn(1).when(orderItemMapper).insert((ProductOrderItem) any());
            doReturn(1).when(cartItemMapper).deleteByIds(any());

            // Act
            ProductOrder result = orderService.createOrder(
                    USER_ID, pickupOrderRequest(null), null);

            // Assert — 不查现存订单
            assertThat(result).isNotNull();
            verify(idempotencyHelper, never()).findByIdempotencyKey(anyLong(), any());
        }

        @Test
        @DisplayName("Idempotent create: 购物车空 + 幂等键命中首次订单时，返回首次订单（并发兜底）")
        void createOrder_emptyCartWithIdempotencyHit_returnsExisting() {
            // Arrange — helper 预检未命中，但购物车空（被并发首次下单删除），再次回查命中
            // 用 lenient 链式：第一次返回 null（预检），第二次返回 defaultOrder（购物车空回查）
            org.mockito.Mockito.when(idempotencyHelper.findByIdempotencyKey(USER_ID, "idem-cart"))
                    .thenReturn(null).thenReturn(defaultOrder);
            doReturn(Collections.emptyList()).when(cartItemMapper).selectList(any());

            // Act
            ProductOrder result = orderService.createOrder(
                    USER_ID, pickupOrderRequest(null), "idem-cart");

            // Assert — 返回首次订单
            assertThat(result).isSameAs(defaultOrder);
            verify(orderMapper, never()).insert((ProductOrder) any());
        }
    }

    private ProductOrderCreateRequest pickupOrderRequest(String remark) {
        return new ProductOrderCreateRequest(
                STORE_ID, "PICKUP", null, "张三", "13800000000", remark, null);
    }

    // ==================== cancelOrder ====================

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrderTests {

        @Test
        @DisplayName("Cancelling an order should restore stock and set status to CANCELLED")
        void cancelOrder_success() {
            // Arrange
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);
            doReturn(List.of(defaultOrderItem)).when(orderItemMapper).selectByOrderId(ORDER_ID);
            doReturn(1).when(productMapper).restoreStock(PRODUCT_ID, 2);
            doReturn(1).when(orderMapper).updateById((ProductOrder) any());

            // Act
            ProductOrder result = orderService.cancelOrder(ORDER_ID, USER_ID);

            // Assert
            assertThat(result.getStatus()).isEqualTo(ProductOrderStatus.CANCELLED.getCode());
            assertThat(result.getCancelTime()).isNotNull();

            verify(productMapper).restoreStock(PRODUCT_ID, 2);
            verify(orderMapper).updateById((ProductOrder) any());
        }

        @Test
        @DisplayName("Cancelling an order not owned by user should throw PRODUCT_ORDER_FORBIDDEN")
        void cancelOrder_wrongUser() {
            // Arrange
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID, 9999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_FORBIDDEN);

            verify(orderMapper, never()).updateById((ProductOrder) any());
        }

        @Test
        @DisplayName("Cancelling a non-existent order should throw PRODUCT_ORDER_NOT_FOUND")
        void cancelOrder_notFound() {
            // Arrange
            doReturn(null).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("Cancelling a deleted order should throw PRODUCT_ORDER_NOT_FOUND")
        void cancelOrder_deleted() {
            // Arrange
            defaultOrder.setDeleted(1);
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("Cancelling a COMPLETED order should throw PRODUCT_ORDER_STATUS_INVALID")
        void cancelOrder_completedOrder() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.COMPLETED.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_STATUS_INVALID);
        }

        @Test
        @DisplayName("Cancelling an already-paid order should throw PRODUCT_ORDER_STATUS_INVALID")
        void cancelOrder_alreadyPaid() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.READY_FOR_PICKUP.getCode());
            defaultOrder.setPaymentStatus("OFFLINE_PAID");
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_STATUS_INVALID);
        }
    }

    // ==================== confirmOrder ====================

    @Nested
    @DisplayName("confirmOrder")
    class ConfirmOrderTests {

        @Test
        @DisplayName("Confirming PENDING_CONFIRM order should set PREPARING and confirmTime")
        void confirmOrder_success() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.PENDING_CONFIRM.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);
            doReturn(1).when(orderMapper).updateById((ProductOrder) any());

            // Act
            ProductOrder result = orderService.confirmOrder(ORDER_ID, OPERATOR_ID);

            // Assert
            assertThat(result.getStatus()).isEqualTo(ProductOrderStatus.PREPARING.getCode());
            assertThat(result.getConfirmTime()).isNotNull();

            ArgumentCaptor<ProductOrder> captor = ArgumentCaptor.forClass(ProductOrder.class);
            verify(orderMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ProductOrderStatus.PREPARING.getCode());
            assertThat(captor.getValue().getConfirmTime()).isNotNull();
        }

        @Test
        @DisplayName("Confirming COMPLETED order should throw PRODUCT_ORDER_STATUS_INVALID")
        void confirmOrder_completedOrder() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.COMPLETED.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.confirmOrder(ORDER_ID, OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_STATUS_INVALID);

            verify(orderMapper, never()).updateById((ProductOrder) any());
        }

        @Test
        @DisplayName("Confirming a non-existent order should throw PRODUCT_ORDER_NOT_FOUND")
        void confirmOrder_notFound() {
            // Arrange
            doReturn(null).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.confirmOrder(ORDER_ID, OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_NOT_FOUND);
        }
    }

    // ==================== markReadyForPickup ====================

    @Nested
    @DisplayName("markReadyForPickup")
    class MarkReadyForPickupTests {

        @Test
        @DisplayName("markReadyForPickup should set READY_FOR_PICKUP status and pickup status")
        void markReadyForPickup_success() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.PREPARING.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);
            doReturn(1).when(orderMapper).updateById((ProductOrder) any());

            // Act
            ProductOrder result = orderService.markReadyForPickup(ORDER_ID, OPERATOR_ID);

            // Assert
            assertThat(result.getStatus()).isEqualTo(ProductOrderStatus.READY_FOR_PICKUP.getCode());
            assertThat(result.getPickupStatus()).isEqualTo(PickupStatus.READY_FOR_PICKUP.getCode());

            ArgumentCaptor<ProductOrder> captor = ArgumentCaptor.forClass(ProductOrder.class);
            verify(orderMapper).updateById(captor.capture());
            ProductOrder captured = captor.getValue();
            assertThat(captured.getStatus()).isEqualTo(ProductOrderStatus.READY_FOR_PICKUP.getCode());
            assertThat(captured.getPickupStatus()).isEqualTo(PickupStatus.READY_FOR_PICKUP.getCode());
        }

        @Test
        @DisplayName("markReadyForPickup on PENDING_CONFIRM order should throw PRODUCT_ORDER_STATUS_INVALID")
        void markReadyForPickup_wrongStatus() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.PENDING_CONFIRM.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.markReadyForPickup(ORDER_ID, OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_STATUS_INVALID);
        }
    }

    // ==================== confirmPayment ====================

    @Nested
    @DisplayName("confirmPayment")
    class ConfirmPaymentTests {

        @Test
        @DisplayName("confirmPayment should set OFFLINE_PAID and PICKED_UP")
        void confirmPayment_success() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.READY_FOR_PICKUP.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);
            doReturn(1).when(orderMapper).updateById((ProductOrder) any());

            // Act
            ProductOrder result = orderService.confirmPayment(ORDER_ID, OPERATOR_ID);

            // Assert
            assertThat(result.getPaymentStatus()).isEqualTo("OFFLINE_PAID");
            assertThat(result.getPickupStatus()).isEqualTo(PickupStatus.PICKED_UP.getCode());

            ArgumentCaptor<ProductOrder> captor = ArgumentCaptor.forClass(ProductOrder.class);
            verify(orderMapper).updateById(captor.capture());
            ProductOrder captured = captor.getValue();
            assertThat(captured.getPaymentStatus()).isEqualTo("OFFLINE_PAID");
            assertThat(captured.getPickupStatus()).isEqualTo(PickupStatus.PICKED_UP.getCode());
        }

        @Test
        @DisplayName("confirmPayment on non-READY_FOR_PICKUP order should throw PRODUCT_ORDER_STATUS_INVALID")
        void confirmPayment_wrongStatus() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.PREPARING.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.confirmPayment(ORDER_ID, OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_STATUS_INVALID);
        }

        @Test
        @DisplayName("confirmPayment on already OFFLINE_PAID order should throw — prevents duplicate")
        void confirmPayment_alreadyPaid() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.READY_FOR_PICKUP.getCode());
            defaultOrder.setPaymentStatus("OFFLINE_PAID");
            defaultOrder.setPickupStatus(PickupStatus.PICKED_UP.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.confirmPayment(ORDER_ID, OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_STATUS_INVALID);

            // Must not update the order again
            verify(orderMapper, never()).updateById((ProductOrder) any());
        }

        @Test
        @DisplayName("confirmPayment on already PICKED_UP order should throw — prevents duplicate")
        void confirmPayment_alreadyPickedUp() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.READY_FOR_PICKUP.getCode());
            defaultOrder.setPaymentStatus("UNPAID");
            defaultOrder.setPickupStatus(PickupStatus.PICKED_UP.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.confirmPayment(ORDER_ID, OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_STATUS_INVALID);

            // Must not update the order again
            verify(orderMapper, never()).updateById((ProductOrder) any());
        }
    }

    // ==================== completeOrder ====================

    @Nested
    @DisplayName("completeOrder")
    class CompleteOrderTests {

        @Test
        @DisplayName("completeOrder should increase sales count and set COMPLETED")
        void completeOrder_success() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.READY_FOR_PICKUP.getCode());
            defaultOrder.setPaymentStatus("OFFLINE_PAID");
            defaultOrder.setPickupStatus(PickupStatus.PICKED_UP.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);
            doReturn(List.of(defaultOrderItem)).when(orderItemMapper).selectByOrderId(ORDER_ID);
            doReturn(1).when(productMapper).increaseSalesCount(PRODUCT_ID, 2);
            doReturn(1).when(orderMapper).updateById((ProductOrder) any());

            // Act
            ProductOrder result = orderService.completeOrder(ORDER_ID, OPERATOR_ID);

            // Assert
            assertThat(result.getStatus()).isEqualTo(ProductOrderStatus.COMPLETED.getCode());
            assertThat(result.getCompleteTime()).isNotNull();

            verify(productMapper).increaseSalesCount(PRODUCT_ID, 2);
            verify(orderMapper).updateById((ProductOrder) any());
        }

        @Test
        @DisplayName("completeOrder without payment should throw PRODUCT_ORDER_PAYMENT_REQUIRED")
        void completeOrder_noPayment() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.READY_FOR_PICKUP.getCode());
            defaultOrder.setPaymentStatus("UNPAID");
            defaultOrder.setPickupStatus(PickupStatus.PICKED_UP.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.completeOrder(ORDER_ID, OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_PAYMENT_REQUIRED);

            verify(orderMapper, never()).updateById((ProductOrder) any());
            verify(productMapper, never()).increaseSalesCount(anyLong(), anyInt());
        }

        @Test
        @DisplayName("completeOrder without pickup should throw PRODUCT_ORDER_PICKUP_REQUIRED")
        void completeOrder_noPickup() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.READY_FOR_PICKUP.getCode());
            defaultOrder.setPaymentStatus("OFFLINE_PAID");
            defaultOrder.setPickupStatus(PickupStatus.READY_FOR_PICKUP.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.completeOrder(ORDER_ID, OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_PICKUP_REQUIRED);

            verify(orderMapper, never()).updateById((ProductOrder) any());
            verify(productMapper, never()).increaseSalesCount(anyLong(), anyInt());
        }

        @Test
        @DisplayName("completeOrder on PREPARING order should throw PRODUCT_ORDER_STATUS_INVALID")
        void completeOrder_wrongStatus() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.PREPARING.getCode());
            defaultOrder.setPaymentStatus("OFFLINE_PAID");
            defaultOrder.setPickupStatus(PickupStatus.PICKED_UP.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.completeOrder(ORDER_ID, OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_STATUS_INVALID);
        }
    }

    // ==================== adminCancelOrder ====================

    @Nested
    @DisplayName("adminCancelOrder")
    class AdminCancelOrderTests {

        @Test
        @DisplayName("Admin cancelling PENDING_CONFIRM order should restore stock and set CANCELLED")
        void adminCancelOrder_success() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.PENDING_CONFIRM.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);
            doReturn(List.of(defaultOrderItem)).when(orderItemMapper).selectByOrderId(ORDER_ID);
            doReturn(1).when(productMapper).restoreStock(PRODUCT_ID, 2);
            doReturn(1).when(orderMapper).updateById((ProductOrder) any());

            // Act
            ProductOrder result = orderService.adminCancelOrder(ORDER_ID, "库存不足", OPERATOR_ID);

            // Assert
            assertThat(result.getStatus()).isEqualTo(ProductOrderStatus.CANCELLED.getCode());
            assertThat(result.getCancelTime()).isNotNull();
            assertThat(result.getMerchantRemark()).isEqualTo("库存不足");

            verify(productMapper).restoreStock(PRODUCT_ID, 2);

            ArgumentCaptor<ProductOrder> captor = ArgumentCaptor.forClass(ProductOrder.class);
            verify(orderMapper).updateById(captor.capture());
            ProductOrder captured = captor.getValue();
            assertThat(captured.getStatus()).isEqualTo(ProductOrderStatus.CANCELLED.getCode());
            assertThat(captured.getMerchantRemark()).isEqualTo("库存不足");
        }

        @Test
        @DisplayName("Admin cancelling COMPLETED order should throw PRODUCT_ORDER_STATUS_INVALID")
        void adminCancelOrder_completedOrder() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.COMPLETED.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.adminCancelOrder(ORDER_ID, "reason", OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_STATUS_INVALID);

            verify(productMapper, never()).restoreStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("Admin cancelling non-existent order should throw PRODUCT_ORDER_NOT_FOUND")
        void adminCancelOrder_notFound() {
            // Arrange
            doReturn(null).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.adminCancelOrder(ORDER_ID, "reason", OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_NOT_FOUND);
        }
    }

    // ==================== outOfStock ====================

    @Nested
    @DisplayName("outOfStock")
    class OutOfStockTests {

        @Test
        @DisplayName("outOfStock should set OUT_OF_STOCK status and NOT restore stock")
        void outOfStock_success() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.PENDING_CONFIRM.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);
            doReturn(1).when(orderMapper).updateById((ProductOrder) any());

            // Act
            ProductOrder result = orderService.outOfStock(ORDER_ID, "商品已无库存", OPERATOR_ID);

            // Assert
            assertThat(result.getStatus()).isEqualTo(ProductOrderStatus.OUT_OF_STOCK.getCode());
            assertThat(result.getCancelTime()).isNotNull();
            assertThat(result.getMerchantRemark()).isEqualTo("商品已无库存");

            // Should NOT restore stock
            verify(productMapper, never()).restoreStock(anyLong(), anyInt());

            ArgumentCaptor<ProductOrder> captor = ArgumentCaptor.forClass(ProductOrder.class);
            verify(orderMapper).updateById(captor.capture());
            ProductOrder captured = captor.getValue();
            assertThat(captured.getStatus()).isEqualTo(ProductOrderStatus.OUT_OF_STOCK.getCode());
            assertThat(captured.getMerchantRemark()).isEqualTo("商品已无库存");
        }

        @Test
        @DisplayName("outOfStock on PREPARING order should throw PRODUCT_ORDER_STATUS_INVALID")
        void outOfStock_wrongStatus() {
            // Arrange
            defaultOrder.setStatus(ProductOrderStatus.PREPARING.getCode());
            doReturn(defaultOrder).when(orderMapper).selectForUpdate(ORDER_ID);

            // Act & Assert
            assertThatThrownBy(() -> orderService.outOfStock(ORDER_ID, "reason", OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PRODUCT_ORDER_STATUS_INVALID);

            verify(productMapper, never()).restoreStock(anyLong(), anyInt());
        }
    }
}
