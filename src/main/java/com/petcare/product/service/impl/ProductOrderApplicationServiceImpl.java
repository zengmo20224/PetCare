package com.petcare.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import com.petcare.product.dto.ProductOrderCreateRequest;
import com.petcare.product.dto.ProductOrderDetailResponse;
import com.petcare.product.dto.ProductOrderDetailResponse.OrderItemResponse;
import com.petcare.product.dto.ProductOrderResponse;
import com.petcare.product.entity.ProductOrder;
import com.petcare.product.entity.ProductOrderItem;
import com.petcare.product.mapper.ProductOrderItemMapper;
import com.petcare.product.mapper.ProductOrderMapper;
import com.petcare.product.service.ProductOrderApplicationService;
import com.petcare.product.service.ProductOrderTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * User-facing product order application service.
 * Orchestrates transaction service calls and converts entities to DTOs.
 *
 * <p>H1 幂等协调：本类不是 @Transactional，可自由组合多个事务方法调用。
 * createOrder 先用 helper 预检幂等，再调 transactionService 下单；
 * 若下单事务因唯一约束抛 DuplicateKeyException（并发竞态），本类用 helper
 * 在新事务回查首次订单返回，从而保证两并发请求都拿到同一订单且库存仅扣一次。
 */
@Service
public class ProductOrderApplicationServiceImpl implements ProductOrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ProductOrderApplicationServiceImpl.class);

    private final ProductOrderMapper orderMapper;
    private final ProductOrderItemMapper orderItemMapper;
    private final ProductOrderTransactionService transactionService;
    private final ProductOrderIdempotencyHelper idempotencyHelper;

    public ProductOrderApplicationServiceImpl(
            ProductOrderMapper orderMapper,
            ProductOrderItemMapper orderItemMapper,
            ProductOrderTransactionService transactionService,
            ProductOrderIdempotencyHelper idempotencyHelper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.transactionService = transactionService;
        this.idempotencyHelper = idempotencyHelper;
    }

    @Override
    public ProductOrderResponse createOrder(Long currentUserId, ProductOrderCreateRequest request, String idempotencyKey) {
        // H1 预检：幂等键非空时先查首次订单，命中直接返回（无事务开销）
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            ProductOrder existing = idempotencyHelper.findByIdempotencyKey(currentUserId, idempotencyKey);
            if (existing != null) {
                log.info("Idempotent order hit (app precheck): userId={}, key={}, orderId={}",
                        currentUserId, idempotencyKey, existing.getId());
                return toOrderResponse(existing);
            }
        }

        try {
            ProductOrder order = transactionService.createOrder(currentUserId, request, idempotencyKey);
            return toOrderResponse(order);
        } catch (DuplicateKeyException e) {
            // H1 并发兜底：两线程都通过预检后，唯一约束拦截第二次插入，txService 事务已回滚（库存恢复）。
            // 在新事务回查首次订单返回。并发下首次订单可能尚未提交，做短重试等待其可见。
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                ProductOrder existing = waitForIdempotentOrder(currentUserId, idempotencyKey);
                if (existing != null) {
                    log.info("Idempotent order resolved (DuplicateKey race): userId={}, key={}, orderId={}",
                            currentUserId, idempotencyKey, existing.getId());
                    return toOrderResponse(existing);
                }
            }
            throw e;
        }
    }

    /**
     * 并发兜底回查：最多重试 5 次（每次间隔 50ms），等待首次订单事务提交可见。
     * 总等待上限 250ms，覆盖正常事务提交延迟。
     */
    private ProductOrder waitForIdempotentOrder(Long userId, String idempotencyKey) {
        for (int i = 0; i < 5; i++) {
            ProductOrder existing = idempotencyHelper.findByIdempotencyKey(userId, idempotencyKey);
            if (existing != null) {
                return existing;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    @Override
    public PageResponse<ProductOrderResponse> getMyOrders(Long currentUserId, int page, int size) {
        LambdaQueryWrapper<ProductOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductOrder::getUserId, currentUserId)
               .eq(ProductOrder::getDeleted, 0)
               .orderByDesc(ProductOrder::getCreateTime);

        IPage<ProductOrder> result = orderMapper.selectPage(new Page<>(page, size), wrapper);

        List<ProductOrderResponse> items = result.getRecords().stream()
                .map(this::toOrderResponse)
                .toList();
        return PageResponse.of(items, result.getTotal(), page, size);
    }

    @Override
    public ProductOrderDetailResponse getOrderDetail(Long currentUserId, Long orderId) {
        ProductOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PRODUCT_ORDER_NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ORDER_FORBIDDEN, "无权查看此订单");
        }
        return toOrderDetailResponse(order);
    }

    @Override
    public ProductOrderResponse cancelOrder(Long currentUserId, Long orderId) {
        ProductOrder order = transactionService.cancelOrder(orderId, currentUserId);
        return toOrderResponse(order);
    }

    private ProductOrderResponse toOrderResponse(ProductOrder order) {
        return new ProductOrderResponse(
                order.getId(), order.getOrderNo(), order.getTotalAmount(),
                order.getDeliveryMethod(), order.getAddressSnapshot(),
                order.getPaymentMethod(), order.getPaymentStatus(),
                order.getPickupStatus(), order.getStatus(),
                order.getContactName(), order.getContactPhone(), order.getRemark(),
                order.getCreateTime(), order.getConfirmTime(),
                order.getCompleteTime(), order.getCancelTime());
    }

    private ProductOrderDetailResponse toOrderDetailResponse(ProductOrder order) {
        List<ProductOrderItem> items = orderItemMapper.selectByOrderId(order.getId());
        List<OrderItemResponse> itemResponses = items.stream()
                .map(item -> new OrderItemResponse(
                        item.getId(), item.getProductId(),
                        item.getProductName(), item.getProductCoverUrl(),
                        item.getPrice(), item.getQuantity(), item.getTotalAmount()))
                .toList();

        return new ProductOrderDetailResponse(
                order.getId(), order.getOrderNo(), order.getUserId(),
                order.getStoreId(), order.getTotalAmount(),
                order.getDeliveryMethod(), order.getAddressSnapshot(),
                order.getPaymentMethod(), order.getPaymentStatus(),
                order.getPickupStatus(), order.getStatus(),
                order.getContactName(), order.getContactPhone(),
                order.getRemark(), order.getMerchantRemark(),
                order.getCreateTime(), order.getConfirmTime(),
                order.getCompleteTime(), order.getCancelTime(),
                itemResponses);
    }
}
