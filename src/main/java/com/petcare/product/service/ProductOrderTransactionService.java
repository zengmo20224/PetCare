package com.petcare.product.service;

import com.petcare.product.dto.ProductOrderCreateRequest;
import com.petcare.product.entity.ProductOrder;

/**
 * Single-transaction product order operations.
 * Each method runs within a @Transactional boundary.
 * Must be called from a separate bean to avoid self-invocation proxy issues.
 */
public interface ProductOrderTransactionService {

    /**
     * Creates an order within a single transaction:
     * 1. (可选) 若 idempotencyKey 非空，先按 (userId, key) 查现存订单，命中则直接返回
     * 2. Load checked cart items
     * 3. Load products by ID ascending (deadlock prevention)
     * 4. Atomically deduct stock for each product
     * 5. Insert order + order items with price snapshots
     * 6. Delete settled cart items
     *
     * <p>Supports two fulfillment modes per {@link ProductOrderCreateRequest#deliveryMethod()}:
     * PICKUP requires a storeId; EXPRESS requires an addressId (the address is
     * snapshotted so later edits/deletes do not affect historical orders).
     *
     * <p>幂等：当 {@code idempotencyKey} 非空时，(user_id, idempotency_key) 唯一约束
     * 保证同一用户同一 key 只创建一次订单；竞态下捕获 DuplicateKeyException 兜底返回首次订单。
     *
     * @param currentUserId   the authenticated user ID
     * @param request         validated order request (deliveryMethod, storeId/addressId, contact, remark)
     * @param idempotencyKey  客户端幂等键，null 表示不启用幂等
     * @return the created order (首次创建或命中现存订单)
     */
    ProductOrder createOrder(Long currentUserId, ProductOrderCreateRequest request, String idempotencyKey);

    /**
     * Cancels an order and restores stock within a transaction.
     * Locks the order row with SELECT FOR UPDATE.
     *
     * @param orderId       the order to cancel
     * @param currentUserId the user who owns the order
     * @return the updated order
     */
    ProductOrder cancelOrder(Long orderId, Long currentUserId);

    /**
     * Confirms an order (PENDING_CONFIRM → PREPARING).
     * Locks the order row with SELECT FOR UPDATE.
     *
     * @param orderId    the order to confirm
     * @param operatorId the admin performing the action
     * @return the updated order
     */
    ProductOrder confirmOrder(Long orderId, Long operatorId);

    /**
     * Marks order as ready for pickup (PREPARING → READY_FOR_PICKUP).
     *
     * @param orderId    the order to update
     * @param operatorId the admin performing the action
     * @return the updated order
     */
    ProductOrder markReadyForPickup(Long orderId, Long operatorId);

    /**
     * Confirms offline payment (READY_FOR_PICKUP only).
     *
     * @param orderId    the order to confirm payment for
     * @param operatorId the admin performing the action
     * @return the updated order
     */
    ProductOrder confirmPayment(Long orderId, Long operatorId);

    /**
     * Completes an order. Must be READY_FOR_PICKUP + OFFLINE_PAID + PICKED_UP.
     * Increases product sales_count.
     *
     * @param orderId    the order to complete
     * @param operatorId the admin performing the action
     * @return the updated order
     */
    ProductOrder completeOrder(Long orderId, Long operatorId);

    /**
     * Admin cancels an order. Restores stock (unless OUT_OF_STOCK).
     *
     * @param orderId    the order to cancel
     * @param reason     optional cancel reason
     * @param operatorId the admin performing the action
     * @return the updated order
     */
    ProductOrder adminCancelOrder(Long orderId, String reason, Long operatorId);

    /**
     * Marks order as out-of-stock (PENDING_CONFIRM → OUT_OF_STOCK).
     * Does NOT restore stock (physical stock was never actually deducted correctly).
     *
     * @param orderId    the order to mark
     * @param reason     optional reason
     * @param operatorId the admin performing the action
     * @return the updated order
     */
    ProductOrder outOfStock(Long orderId, String reason, Long operatorId);
}
