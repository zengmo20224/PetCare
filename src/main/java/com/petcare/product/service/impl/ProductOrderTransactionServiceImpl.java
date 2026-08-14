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
import com.petcare.wallet.service.WalletService;
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
    private final WalletService walletService;

    public ProductOrderTransactionServiceImpl(
            CartItemMapper cartItemMapper,
            ProductMapper productMapper,
            ProductOrderMapper orderMapper,
            ProductOrderItemMapper orderItemMapper,
            UserAddressMapper userAddressMapper,
            ProductOrderIdempotencyHelper idempotencyHelper,
            WalletService walletService) {
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.userAddressMapper = userAddressMapper;
        this.idempotencyHelper = idempotencyHelper;
        this.walletService = walletService;
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
     *
     * <p>钱包支付（CR-20260718-003 / D-012）：当 {@code paymentMethod == WALLET} 时，
     * 钱包行先于商品库存被锁定（锁序 wallet → product asc），扣款与扣库存同处一个事务——
     * 任何一方失败（余额不足/库存不足/幂等冲突）都会整体回滚，绝不出现"扣了钱没扣库存"
     * 或"扣了库存没扣钱"。退款在 {@link #cancelOrder} / {@link #adminCancelOrder} 同事务回滚。</p>
     */
    private ProductOrder doCreateOrder(Long currentUserId, ProductOrderCreateRequest request, String idempotencyKey) {
        String deliveryMethod = request.deliveryMethod();
        boolean isPickup = "PICKUP".equals(deliveryMethod);
        Long storeId = request.storeId();
        Long addressId = request.addressId();
        // 解析付款方式：null/blank 归一化为 OFFLINE_STORE（向后兼容）。
        String paymentMethod = request.effectivePaymentMethod();
        boolean payByWallet = "WALLET".equals(paymentMethod);

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

        // 4b. Wallet payment: lock the wallet row BEFORE stock deduction to fix the global
        //     lock order (wallet → product asc). This serializes concurrent wallet payments
        //     for the same user. Lazy-creates the wallet on first use.
        //     Balance is NOT checked here — the final deduct happens in step 6b after the
        //     order total is known, and it is atomic with stock deduction via the transaction.
        if (payByWallet) {
            walletService.lockWalletForUpdate(currentUserId);
        }

        // 5. Atomically deduct stock for each product.
        //    B2 修复（D-012 规则 2 锁序 product(asc)）：lines 原按购物车项顺序构建
        //    （cart_item 主键序），扣减前必须按 productId 升序排序，
        //    保证并发下单时 product 行锁获取顺序全局一致，消除行间 AB-BA 死锁。
        lines.sort(java.util.Comparator.comparing(LineSnapshot::productId));
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

        // 6b. Wallet payment: deduct the order total from the wallet now that the amount is known.
        //     This runs in the same transaction as stock deduction — if balance is insufficient,
        //     the thrown BusinessException rolls back the already-deducted stock (D-012).
        //     The ledger entry references the order via idempotency key (orderId is assigned
        //     by the DB on insert below; we link by the request-scoped idempotency key to
        //     keep the deduction ahead of order insert while still traceable).
        if (payByWallet) {
            // 钱包流水幂等键：优先复用订单幂等键（保证整个下单流程幂等）；
            // 订单未启用幂等时用 nanoTime 生成一次性键（钱包流水仍可去重）。
            String walletIdemKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                    ? "wallet-pay-" + idempotencyKey
                    : "wallet-pay-" + System.nanoTime();
            walletService.deductForPayment(
                    currentUserId, totalAmount, "PRODUCT_ORDER", null, walletIdemKey);
        }

        // 7. Create order
        ProductOrder order = new ProductOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(currentUserId);
        order.setStoreId(storeId);
        order.setTotalAmount(totalAmount);
        order.setDeliveryMethod(deliveryMethod);
        order.setAddressId(addressId);
        order.setAddressSnapshot(addressSnapshot);
        order.setPaymentMethod(paymentMethod);
        // 钱包支付即时到账：paymentStatus 直接置为 WALLET_PAID；
        // 线下支付保持 UNPAID，等管理员"确认支付"按钮翻转为 OFFLINE_PAID。
        order.setPaymentStatus(payByWallet ? "WALLET_PAID" : "UNPAID");
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

        // B1 修复（D-012 规则 2 全局锁序 wallet → product）：退款（锁 wallet 行）必须在
        // 恢复库存（锁 product 行）之前——与 doCreateOrder 的 W→P 序一致，
        // 消除"并发取消退款 + 钱包下单扣款"的 AB-BA 死锁窗口。同一事务内原子性不变。
        refundWalletIfPaidByWallet(order);

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

        // B1 修复：退款（锁 wallet）先于恢复库存（锁 product），与用户取消/下单路径锁序一致
        refundWalletIfPaidByWallet(order);

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

        // H-3：OUT_OF_STOCK 是终态，取消路径不可达——钱包已付款必须在本次事务内退款，
        // 否则用户资金永久滞留（D-012：退款与状态翻转同事务，幂等键防重复退）。
        refundWalletIfPaidByWallet(order);

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

    /**
     * 如果订单是用钱包支付的（paymentMethod=WALLET），在同事务内把原金额退回用户钱包。
     * 与库存恢复同处一个事务，保证"库存退了钱没退"或"钱退了库存没退"都不会发生（D-012）。
     * 非钱包支付订单（OFFLINE_STORE）不触发任何钱包动作——线下收款的退款由门店线下处理。
     */
    private void refundWalletIfPaidByWallet(ProductOrder order) {
        if (!"WALLET".equals(order.getPaymentMethod())) {
            return;
        }
        // 退款幂等键：基于订单 ID 生成，保证同一订单取消多次（理论上不会，但防御）只退一次。
        String refundIdemKey = "wallet-refund-order-" + order.getId();
        walletService.refundForCancellation(
                order.getUserId(), order.getTotalAmount(), "PRODUCT_ORDER",
                order.getId(), refundIdemKey);
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "PO" + timestamp + random;
    }
}
