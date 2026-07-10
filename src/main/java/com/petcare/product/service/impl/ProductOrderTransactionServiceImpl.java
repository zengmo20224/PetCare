package com.petcare.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.product.domain.ProductOrderAmountCalculator;
import com.petcare.product.domain.ProductOrderAmountCalculator.LineSnapshot;
import com.petcare.product.domain.ProductOrderStateMachine;
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
import com.petcare.product.service.ProductOrderTransactionService;
import com.petcare.user.entity.UserAddress;
import com.petcare.user.mapper.UserAddressMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Transactional service for product order operations.
 * Each method runs in a single database transaction.
 * Called from separate beans (ProductOrderApplicationServiceImpl, AdminProductOrderServiceImpl)
 * to avoid self-invocation proxy issues.
 */
@Service
public class ProductOrderTransactionServiceImpl implements ProductOrderTransactionService {

    private static final Logger log = LoggerFactory.getLogger(ProductOrderTransactionServiceImpl.class);

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final ProductOrderMapper orderMapper;
    private final ProductOrderItemMapper orderItemMapper;
    private final UserAddressMapper userAddressMapper;
    private final ProductOrderIdempotencyHelper idempotencyHelper;

    public ProductOrderTransactionServiceImpl(
            CartItemMapper cartItemMapper,
            ProductMapper productMapper,
            ProductOrderMapper orderMapper,
            ProductOrderItemMapper orderItemMapper,
            UserAddressMapper userAddressMapper,
            ProductOrderIdempotencyHelper idempotencyHelper) {
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.userAddressMapper = userAddressMapper;
        this.idempotencyHelper = idempotencyHelper;
    }

    /**
     * Builds a single-line address snapshot string for historical orders.
     * Captured at order time so later edits/deletes of the source address do not
     * affect what was actually shipped.
     */
    private String buildAddressSnapshot(UserAddress addr) {
        StringBuilder sb = new StringBuilder();
        sb.append(addr.getContactName() != null ? addr.getContactName() : "");
        sb.append(" ").append(addr.getContactPhone() != null ? addr.getContactPhone() : "");
        sb.append(" ");
        if (addr.getProvince() != null) sb.append(addr.getProvince());
        if (addr.getCity() != null) sb.append(addr.getCity());
        if (addr.getDistrict() != null) sb.append(addr.getDistrict());
        if (addr.getDetailAddress() != null) sb.append(addr.getDetailAddress());
        return sb.toString().trim();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ProductOrder createOrder(Long currentUserId, ProductOrderCreateRequest request, String idempotencyKey) {
        // H1 幂等：幂等键非空时先查现存订单（应用层已用 REQUIRES_NEW 预检，这里是事务内二次确认）。
        // 并发竞态下若两线程都通过预检，DB 的 UNIQUE(user_id, idempotency_key) 会拦截第二次插入，
        // 抛出 DuplicateKeyException —— 该异常向上传播，触发本事务回滚（库存恢复），
        // 由应用层（ProductOrderApplicationServiceImpl）在新事务回查首次订单返回。
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            ProductOrder existing = idempotencyHelper.findByIdempotencyKey(currentUserId, idempotencyKey);
            if (existing != null) {
                log.info("Idempotent order hit: userId={}, idempotencyKey={}, orderId={}",
                        currentUserId, idempotencyKey, existing.getId());
                return existing;
            }
        }
        return doCreateOrder(currentUserId, request, idempotencyKey);
    }

    /**
     * 实际下单逻辑：扣库存 + 插单 + 插订单项 + 删购物车。
     */
    private ProductOrder doCreateOrder(Long currentUserId, ProductOrderCreateRequest request, String idempotencyKey) {
        String deliveryMethod = request.deliveryMethod();
        boolean isPickup = "PICKUP".equals(deliveryMethod);
        Long storeId = request.storeId();
        Long addressId = request.addressId();

        // 0. Validate fulfillment-specific requirements and snapshot the address.
        //    Address snapshot is captured at order time so later edits/deletes
        //    of user_address do not corrupt historical orders.
        String addressSnapshot = null;
        if (!isPickup) {
            if (addressId == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "快递配送需选择收货地址");
            }
            UserAddress addr = userAddressMapper.selectById(addressId);
            if (addr == null || (addr.getDeleted() != null && addr.getDeleted() == 1)
                    || !addr.getUserId().equals(currentUserId)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "收货地址不存在或无权使用");
            }
            addressSnapshot = buildAddressSnapshot(addr);
        }

        // 1. Load checked cart items for the current user
        LambdaQueryWrapper<CartItem> cartWrapper = new LambdaQueryWrapper<>();
        cartWrapper.eq(CartItem::getUserId, currentUserId)
                   .eq(CartItem::getChecked, 1);
        List<CartItem> checkedItems = cartItemMapper.selectList(cartWrapper);
        if (checkedItems.isEmpty()) {
            // H1 并发兜底：幂等键非空时，购物车空可能是首次成功的并发下单已删除购物车。
            // 用 helper（REQUIRES_NEW）回查，确保能看到已提交的首次订单。
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                ProductOrder existing = idempotencyHelper.findByIdempotencyKey(currentUserId, idempotencyKey);
                if (existing != null) {
                    log.info("Idempotent order hit (cart empty race): userId={}, idempotencyKey={}, orderId={}",
                            currentUserId, idempotencyKey, existing.getId());
                    return existing;
                }
            }
            throw new BusinessException(ErrorCode.CART_NO_CHECKED_ITEMS, "没有已选中的购物车项");
        }

        // 2. Collect product IDs and sort ascending to prevent deadlocks
        List<Long> productIds = checkedItems.stream()
                .map(CartItem::getProductId)
                .distinct()
                .sorted()
                .toList();

        // 3. Load products and validate.
        //    pickupOnly is only enforced when the customer chose store pickup;
        //    express orders may include any on-sale product.
        List<Product> products = new ArrayList<>();
        for (Long pid : productIds) {
            Product product = productMapper.selectById(pid);
            if (product == null || product.getDeleted() == 1
                    || !"ON_SALE".equals(product.getStatus())) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE,
                        "商品不存在或已下架: " + pid);
            }
            if (isPickup && (product.getPickupOnly() == null || product.getPickupOnly() != 1)) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_PICKUP_ONLY,
                        "商品不支持到店自提: " + product.getName());
            }
            products.add(product);
        }

        // 4. Build line snapshots with server-side price calculation
        List<LineSnapshot> lines = new ArrayList<>();
        for (CartItem ci : checkedItems) {
            Product product = products.stream()
                    .filter(p -> p.getId().equals(ci.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "商品不存在"));
            BigDecimal lineTotal = ProductOrderAmountCalculator.calculateLineTotal(
                    product.getPrice(), ci.getQuantity());
            lines.add(new LineSnapshot(
                    product.getId(), product.getName(), product.getCoverUrl(),
                    product.getPrice(), ci.getQuantity(), lineTotal));
        }

        // 5. Atomically deduct stock for each product (in product ID ascending order)
        for (LineSnapshot line : lines) {
            int rows = productMapper.deductStock(line.productId(), line.quantity());
            if (rows == 0) {
                throw new BusinessException(ErrorCode.PRODUCT_STOCK_INSUFFICIENT,
                        "商品库存不足: " + line.productName());
            }
        }

        // 6. Calculate order total
        BigDecimal totalAmount = ProductOrderAmountCalculator.calculateOrderTotal(
                lines.stream().map(LineSnapshot::totalAmount).toList());

        // 7. Create order
        ProductOrder order = new ProductOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(currentUserId);
        order.setStoreId(storeId);
        order.setTotalAmount(totalAmount);
        order.setDeliveryMethod(deliveryMethod);
        order.setAddressId(addressId);
        order.setAddressSnapshot(addressSnapshot);
        order.setPaymentMethod("OFFLINE_STORE");
        order.setPaymentStatus("UNPAID");
        order.setPickupStatus(PickupStatus.WAIT_PREPARE.getCode());
        order.setStatus(ProductOrderStatus.PENDING_CONFIRM.getCode());
        order.setContactName(request.contactName());
        order.setContactPhone(request.contactPhone());
        order.setRemark(request.remark());
        order.setIdempotencyKey(idempotencyKey);
        orderMapper.insert(order);

        // 8. Create order items (price snapshots)
        for (LineSnapshot line : lines) {
            ProductOrderItem item = new ProductOrderItem();
            item.setOrderId(order.getId());
            item.setProductId(line.productId());
            item.setProductName(line.productName());
            item.setProductCoverUrl(line.productCoverUrl());
            item.setPrice(line.price());
            item.setQuantity(line.quantity());
            item.setTotalAmount(line.totalAmount());
            orderItemMapper.insert(item);
        }

        // 9. Delete settled cart items
        List<Long> cartItemIds = checkedItems.stream().map(CartItem::getId).toList();
        cartItemMapper.deleteByIds(cartItemIds);

        log.info("Order created: orderNo={}, userId={}, totalAmount={}, items={}",
                order.getOrderNo(), currentUserId, totalAmount, lines.size());
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductOrder cancelOrder(Long orderId, Long currentUserId) {
        // Lock order row
        ProductOrder order = orderMapper.selectForUpdate(orderId);
        if (order == null || order.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PRODUCT_ORDER_NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ORDER_FORBIDDEN, "无权操作此订单");
        }

        // Validate can cancel (user cancel only for PENDING_CONFIRM)
        ProductOrderStateMachine.validateTransition(order.getStatus(),
                ProductOrderStatus.CANCELLED.getCode());
        ProductOrderStateMachine.validateCanCancel(order.getPaymentStatus(), order.getPickupStatus());

        // Restore stock
        restoreOrderStock(orderId);

        // Update order
        order.setStatus(ProductOrderStatus.CANCELLED.getCode());
        order.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(order);

        log.info("Order cancelled by user: orderId={}, userId={}", orderId, currentUserId);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductOrder confirmOrder(Long orderId, Long operatorId) {
        ProductOrder order = lockAndValidate(orderId);

        ProductOrderStateMachine.validateTransition(order.getStatus(),
                ProductOrderStatus.PREPARING.getCode());

        order.setStatus(ProductOrderStatus.PREPARING.getCode());
        order.setConfirmTime(LocalDateTime.now());
        orderMapper.updateById(order);

        log.info("Order confirmed: orderId={}, operatorId={}", orderId, operatorId);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductOrder markReadyForPickup(Long orderId, Long operatorId) {
        ProductOrder order = lockAndValidate(orderId);

        ProductOrderStateMachine.validateTransition(order.getStatus(),
                ProductOrderStatus.READY_FOR_PICKUP.getCode());

        order.setStatus(ProductOrderStatus.READY_FOR_PICKUP.getCode());
        order.setPickupStatus(PickupStatus.READY_FOR_PICKUP.getCode());
        orderMapper.updateById(order);

        log.info("Order ready for pickup: orderId={}, operatorId={}", orderId, operatorId);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductOrder confirmPayment(Long orderId, Long operatorId) {
        ProductOrder order = lockAndValidate(orderId);

        ProductOrderStateMachine.validateCanConfirmPayment(
                order.getStatus(), order.getPaymentStatus(), order.getPickupStatus());

        order.setPaymentStatus("OFFLINE_PAID");
        order.setPickupStatus(PickupStatus.PICKED_UP.getCode());
        orderMapper.updateById(order);

        log.info("Order payment confirmed: orderId={}, operatorId={}", orderId, operatorId);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductOrder completeOrder(Long orderId, Long operatorId) {
        ProductOrder order = lockAndValidate(orderId);

        ProductOrderStateMachine.validateCanComplete(
                order.getStatus(), order.getPaymentStatus(), order.getPickupStatus());
        ProductOrderStateMachine.validateTransition(order.getStatus(),
                ProductOrderStatus.COMPLETED.getCode());

        // Increase sales count for each order item
        List<ProductOrderItem> items = orderItemMapper.selectByOrderId(orderId);
        for (ProductOrderItem item : items) {
            productMapper.increaseSalesCount(item.getProductId(), item.getQuantity());
        }

        order.setStatus(ProductOrderStatus.COMPLETED.getCode());
        order.setCompleteTime(LocalDateTime.now());
        orderMapper.updateById(order);

        log.info("Order completed: orderId={}, operatorId={}", orderId, operatorId);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductOrder adminCancelOrder(Long orderId, String reason, Long operatorId) {
        ProductOrder order = lockAndValidate(orderId);

        ProductOrderStateMachine.validateTransition(order.getStatus(),
                ProductOrderStatus.CANCELLED.getCode());
        ProductOrderStateMachine.validateCanCancel(order.getPaymentStatus(), order.getPickupStatus());

        // Restore stock
        restoreOrderStock(orderId);

        order.setStatus(ProductOrderStatus.CANCELLED.getCode());
        order.setCancelTime(LocalDateTime.now());
        order.setMerchantRemark(reason);
        orderMapper.updateById(order);

        log.info("Order cancelled by admin: orderId={}, operatorId={}, reason={}",
                orderId, operatorId, reason);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductOrder outOfStock(Long orderId, String reason, Long operatorId) {
        ProductOrder order = lockAndValidate(orderId);

        ProductOrderStateMachine.validateTransition(order.getStatus(),
                ProductOrderStatus.OUT_OF_STOCK.getCode());

        // OUT_OF_STOCK does NOT restore stock
        order.setStatus(ProductOrderStatus.OUT_OF_STOCK.getCode());
        order.setCancelTime(LocalDateTime.now());
        order.setMerchantRemark(reason);
        orderMapper.updateById(order);

        log.info("Order marked out-of-stock: orderId={}, operatorId={}", orderId, operatorId);
        return order;
    }

    private ProductOrder lockAndValidate(Long orderId) {
        ProductOrder order = orderMapper.selectForUpdate(orderId);
        if (order == null || order.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PRODUCT_ORDER_NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private void restoreOrderStock(Long orderId) {
        List<ProductOrderItem> items = orderItemMapper.selectByOrderId(orderId);
        for (ProductOrderItem item : items) {
            productMapper.restoreStock(item.getProductId(), item.getQuantity());
        }
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "PO" + timestamp + random;
    }
}
