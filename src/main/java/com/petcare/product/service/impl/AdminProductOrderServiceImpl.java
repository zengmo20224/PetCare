package com.petcare.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petcare.admin.entity.AdminOperationLog;
import com.petcare.admin.service.AdminOperationLogService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import com.petcare.product.dto.ProductOrderDetailResponse;
import com.petcare.product.dto.ProductOrderDetailResponse.OrderItemResponse;
import com.petcare.product.dto.ProductOrderResponse;
import com.petcare.product.entity.ProductOrder;
import com.petcare.product.entity.ProductOrderItem;
import com.petcare.product.mapper.ProductOrderItemMapper;
import com.petcare.product.mapper.ProductOrderMapper;
import com.petcare.product.service.AdminProductOrderService;
import com.petcare.product.service.ProductOrderTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin product order application service.
 * No ownership check — admin can access any order.
 *
 * Audit log strategy: log writes use try-catch so that log failures
 * do NOT roll back the main business transaction.
 */
@Service
public class AdminProductOrderServiceImpl implements AdminProductOrderService {

    private static final Logger log = LoggerFactory.getLogger(AdminProductOrderServiceImpl.class);

    private static final String MODULE = "product_order";

    private final ProductOrderMapper orderMapper;
    private final ProductOrderItemMapper orderItemMapper;
    private final ProductOrderTransactionService transactionService;
    private final AdminOperationLogService operationLogService;

    public AdminProductOrderServiceImpl(
            ProductOrderMapper orderMapper,
            ProductOrderItemMapper orderItemMapper,
            ProductOrderTransactionService transactionService,
            AdminOperationLogService operationLogService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.transactionService = transactionService;
        this.operationLogService = operationLogService;
    }

    @Override
    public PageResponse<ProductOrderResponse> listOrders(int page, int size, String status) {
        LambdaQueryWrapper<ProductOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductOrder::getDeleted, 0);
        if (status != null && !status.isBlank()) {
            wrapper.eq(ProductOrder::getStatus, status);
        }
        wrapper.orderByDesc(ProductOrder::getCreateTime);

        IPage<ProductOrder> result = orderMapper.selectPage(new Page<>(page, size), wrapper);

        List<ProductOrderResponse> items = result.getRecords().stream()
                .map(this::toOrderResponse)
                .toList();
        return PageResponse.of(items, result.getTotal(), page, size);
    }

    @Override
    public ProductOrderDetailResponse getOrderDetail(Long orderId) {
        ProductOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PRODUCT_ORDER_NOT_FOUND, "订单不存在");
        }
        return toOrderDetailResponse(order);
    }

    @Override
    public ProductOrderResponse confirmOrder(Long orderId, Long operatorId) {
        String operation = "confirm";
        String url = "/api/v1/admin/product-orders/" + orderId + "/confirm";
        try {
            ProductOrder order = transactionService.confirmOrder(orderId, operatorId);
            saveLog(operatorId, operation, url,
                    "orderNo=" + order.getOrderNo() + ", orderId=" + orderId, "success", null);
            return toOrderResponse(order);
        } catch (BusinessException e) {
            saveLog(operatorId, operation, url, "orderId=" + orderId, "fail", e.getMessage());
            throw e;
        }
    }

    @Override
    public ProductOrderResponse markReadyForPickup(Long orderId, Long operatorId) {
        String operation = "ready";
        String url = "/api/v1/admin/product-orders/" + orderId + "/ready";
        try {
            ProductOrder order = transactionService.markReadyForPickup(orderId, operatorId);
            saveLog(operatorId, operation, url,
                    "orderNo=" + order.getOrderNo() + ", orderId=" + orderId, "success", null);
            return toOrderResponse(order);
        } catch (BusinessException e) {
            saveLog(operatorId, operation, url, "orderId=" + orderId, "fail", e.getMessage());
            throw e;
        }
    }

    @Override
    public ProductOrderResponse confirmPayment(Long orderId, Long operatorId) {
        String operation = "confirm-payment";
        String url = "/api/v1/admin/product-orders/" + orderId + "/confirm-payment";
        try {
            ProductOrder order = transactionService.confirmPayment(orderId, operatorId);
            saveLog(operatorId, operation, url,
                    "orderNo=" + order.getOrderNo() + ", orderId=" + orderId, "success", null);
            return toOrderResponse(order);
        } catch (BusinessException e) {
            saveLog(operatorId, operation, url, "orderId=" + orderId, "fail", e.getMessage());
            throw e;
        }
    }

    @Override
    public ProductOrderResponse completeOrder(Long orderId, Long operatorId) {
        String operation = "complete";
        String url = "/api/v1/admin/product-orders/" + orderId + "/complete";
        try {
            ProductOrder order = transactionService.completeOrder(orderId, operatorId);
            saveLog(operatorId, operation, url,
                    "orderNo=" + order.getOrderNo() + ", orderId=" + orderId, "success", null);
            return toOrderResponse(order);
        } catch (BusinessException e) {
            saveLog(operatorId, operation, url, "orderId=" + orderId, "fail", e.getMessage());
            throw e;
        }
    }

    @Override
    public ProductOrderResponse cancelOrder(Long orderId, String reason, Long operatorId) {
        String operation = "cancel";
        String url = "/api/v1/admin/product-orders/" + orderId + "/cancel";
        try {
            ProductOrder order = transactionService.adminCancelOrder(orderId, reason, operatorId);
            saveLog(operatorId, operation, url,
                    "orderNo=" + order.getOrderNo() + ", orderId=" + orderId, "success", null);
            return toOrderResponse(order);
        } catch (BusinessException e) {
            saveLog(operatorId, operation, url, "orderId=" + orderId, "fail", e.getMessage());
            throw e;
        }
    }

    @Override
    public ProductOrderResponse outOfStock(Long orderId, String reason, Long operatorId) {
        String operation = "out-of-stock";
        String url = "/api/v1/admin/product-orders/" + orderId + "/out-of-stock";
        try {
            ProductOrder order = transactionService.outOfStock(orderId, reason, operatorId);
            saveLog(operatorId, operation, url,
                    "orderNo=" + order.getOrderNo() + ", orderId=" + orderId, "success", null);
            return toOrderResponse(order);
        } catch (BusinessException e) {
            saveLog(operatorId, operation, url, "orderId=" + orderId, "fail", e.getMessage());
            throw e;
        }
    }

    /**
     * Writes an audit log entry. Failures are caught and logged but do NOT
     * propagate — audit logging must not roll back the main business transaction.
     */
    private void saveLog(Long operatorId, String operation, String url, String params,
                         String result, String errorMessage) {
        try {
            AdminOperationLog logEntry = new AdminOperationLog();
            logEntry.setAdminId(operatorId);
            logEntry.setModule(MODULE);
            logEntry.setOperation(operation);
            logEntry.setRequestMethod("POST");
            logEntry.setRequestUrl(url);
            logEntry.setRequestParams(params);
            logEntry.setResult(result);
            logEntry.setErrorMessage(errorMessage);
            logEntry.setCreateTime(LocalDateTime.now());
            operationLogService.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to write admin operation log: operatorId={}, operation={}, error={}",
                    operatorId, operation, e.getMessage());
        }
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
