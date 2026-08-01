package com.petcare.product.domain;

import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.product.enums.PickupStatus;
import com.petcare.product.enums.ProductOrderStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Validates product order state transitions.
 * Encapsulates all allowed and forbidden transitions as pure logic.
 *
 * Allowed transitions:
 *   null → PENDING_CONFIRM
 *   PENDING_CONFIRM → PREPARING, CANCELLED, OUT_OF_STOCK
 *   PREPARING → READY_FOR_PICKUP, CANCELLED
 *   READY_FOR_PICKUP → CANCELLED, COMPLETED
 *
 * Terminal states: COMPLETED, CANCELLED, OUT_OF_STOCK
 */
public final class ProductOrderStateMachine {

    private static final Map<ProductOrderStatus, Set<ProductOrderStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<ProductOrderStatus, Set<ProductOrderStatus>> map = new EnumMap<>(ProductOrderStatus.class);

        map.put(ProductOrderStatus.PENDING_CONFIRM, Set.of(
                ProductOrderStatus.PREPARING,
                ProductOrderStatus.CANCELLED,
                ProductOrderStatus.OUT_OF_STOCK));

        map.put(ProductOrderStatus.PREPARING, Set.of(
                ProductOrderStatus.READY_FOR_PICKUP,
                ProductOrderStatus.CANCELLED));

        map.put(ProductOrderStatus.READY_FOR_PICKUP, Set.of(
                ProductOrderStatus.CANCELLED,
                ProductOrderStatus.COMPLETED));

        // Terminal states
        map.put(ProductOrderStatus.COMPLETED, Collections.emptySet());
        map.put(ProductOrderStatus.CANCELLED, Collections.emptySet());
        map.put(ProductOrderStatus.OUT_OF_STOCK, Collections.emptySet());

        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(map);
    }

    private ProductOrderStateMachine() {
        // utility class
    }

    /**
     * Validates that the transition from currentStatus to targetStatus is allowed.
     *
     * @param currentStatus the current order status (null for initial creation)
     * @param targetStatus  the desired next status
     * @throws BusinessException if the transition is not allowed
     */
    public static void validateTransition(String currentStatus, String targetStatus) {
        if (currentStatus == null) {
            if (ProductOrderStatus.PENDING_CONFIRM.getCode().equals(targetStatus)) {
                return;
            }
            throw new BusinessException(
                    ErrorCode.PRODUCT_ORDER_STATUS_INVALID,
                    String.format("订单状态不允许从 null 变更为 %s", targetStatus));
        }

        ProductOrderStatus from = parseStatus(currentStatus);
        ProductOrderStatus to = parseStatus(targetStatus);

        Set<ProductOrderStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_ORDER_STATUS_INVALID,
                    String.format("订单状态不允许从 %s 变更为 %s", currentStatus, targetStatus));
        }
    }

    /**
     * Checks if the given status is a terminal state.
     */
    public static boolean isTerminalStatus(String status) {
        if (status == null) return false;
        ProductOrderStatus parsed = parseStatus(status);
        Set<ProductOrderStatus> allowed = ALLOWED_TRANSITIONS.get(parsed);
        return allowed != null && allowed.isEmpty();
    }

    /**
     * Validates that a READY_FOR_PICKUP order can have its payment confirmed.
     * Prevents duplicate payment confirmation by checking:
     * - Order status must be READY_FOR_PICKUP
     * - Payment status must still be UNPAID
     * - Pickup status must not already be PICKED_UP
     */
    public static void validateCanConfirmPayment(String status, String paymentStatus, String pickupStatus) {
        if (!ProductOrderStatus.READY_FOR_PICKUP.getCode().equals(status)) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_ORDER_STATUS_INVALID,
                    "只有待自提状态的订单才能确认收款");
        }
        if (isPaid(paymentStatus)) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_ORDER_STATUS_INVALID,
                    "订单已确认收款，不能重复确认");
        }
        if (PickupStatus.PICKED_UP.getCode().equals(pickupStatus)) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_ORDER_STATUS_INVALID,
                    "订单已自提，不能重复确认收款");
        }
    }

    /**
     * Validates that an order can be completed.
     * Must be READY_FOR_PICKUP, payment must be paid (OFFLINE_PAID or WALLET_PAID),
     * pickup must be PICKED_UP.
     */
    public static void validateCanComplete(String status, String paymentStatus, String pickupStatus) {
        if (!ProductOrderStatus.READY_FOR_PICKUP.getCode().equals(status)) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_ORDER_STATUS_INVALID,
                    "只有待自提状态的订单才能完成");
        }
        if (!isPaid(paymentStatus)) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_ORDER_PAYMENT_REQUIRED,
                    "订单尚未确认收款，无法完成");
        }
        if (!PickupStatus.PICKED_UP.getCode().equals(pickupStatus)) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_ORDER_PICKUP_REQUIRED,
                    "订单尚未确认自提，无法完成");
        }
    }

    /**
     * Validates that an order can be cancelled via normal cancellation.
     * Already-paid or already-picked-up orders cannot be normally cancelled.
     */
    public static void validateCanCancel(String paymentStatus, String pickupStatus) {
        // 线下已收款的订单不能普通取消（需门店线下退款）；钱包支付订单可以取消，
        // 因为 cancelOrder/adminCancelOrder 会在同事务内自动退款到钱包（D-012）。
        if ("OFFLINE_PAID".equals(paymentStatus)) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_ORDER_STATUS_INVALID,
                    "已付款订单不能通过普通取消操作取消");
        }
        if (PickupStatus.PICKED_UP.getCode().equals(pickupStatus)) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_ORDER_STATUS_INVALID,
                    "已自提订单不能取消");
        }
    }

    /**
     * 判断订单是否已付款。线下收款（OFFLINE_PAID）和钱包支付（WALLET_PAID）都视为已付款，
     * 二者在 confirmPayment/complete 校验中等价（CR-20260718-003）。
     */
    private static boolean isPaid(String paymentStatus) {
        return "OFFLINE_PAID".equals(paymentStatus) || "WALLET_PAID".equals(paymentStatus);
    }

    private static ProductOrderStatus parseStatus(String status) {
        for (ProductOrderStatus s : ProductOrderStatus.values()) {
            if (s.getCode().equals(status)) {
                return s;
            }
        }
        throw new BusinessException(
                ErrorCode.PRODUCT_ORDER_STATUS_INVALID,
                "未知的订单状态: " + status);
    }
}
