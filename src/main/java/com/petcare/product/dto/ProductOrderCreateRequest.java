package com.petcare.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for creating a product order.
 * Does NOT contain userId, price, or amount — those come from server-side.
 *
 * <p>Supports two fulfillment modes:
 * <ul>
 *   <li><b>PICKUP</b> — customer picks up at a store; {@code storeId} required.</li>
 *   <li><b>EXPRESS</b> — delivered to an address; {@code addressId} required.</li>
 * </ul>
 * The cross-field rule is enforced by {@link #isValidDelivery()}.
 *
 * <p>Payment method (CR-20260718-003 / D-010): {@code OFFLINE_STORE} (default, pay at store)
 * or {@code WALLET} (deduct from wallet balance in the same transaction as stock deduction).
 * Omitted/null falls back to {@code OFFLINE_STORE} for backward compatibility with existing
 * clients and tests.
 */
public record ProductOrderCreateRequest(
        Long storeId,
        @NotNull(message = "配送方式不能为空")
        @Pattern(regexp = "PICKUP|EXPRESS", message = "配送方式只能是 PICKUP 或 EXPRESS")
        String deliveryMethod,
        Long addressId,
        @NotBlank(message = "联系人姓名不能为空")
        String contactName,
        @NotBlank(message = "联系电话不能为空")
        String contactPhone,
        String remark,
        /** 客户端幂等键，由请求头 Idempotency-Key 注入；null 表示不启用幂等。 */
        @Pattern(regexp = "^.{1,64}$", message = "幂等键长度需在 1-64 之间") String idempotencyKey,
        /** 付款方式：OFFLINE_STORE（默认）或 WALLET。null 视为 OFFLINE_STORE。 */
        @Pattern(regexp = "OFFLINE_STORE|WALLET", message = "付款方式只能是 OFFLINE_STORE 或 WALLET")
        String paymentMethod
) {
    /**
     * 向后兼容的 6 参数构造器：不启用幂等，付款方式默认 OFFLINE_STORE。
     * 保留给未升级的调用方与既有测试使用。
     */
    public ProductOrderCreateRequest(Long storeId, String deliveryMethod, Long addressId,
                                     String contactName, String contactPhone, String remark) {
        this(storeId, deliveryMethod, addressId, contactName, contactPhone, remark, null, "OFFLINE_STORE");
    }

    /**
     * 向后兼容的 7 参数构造器（含幂等键）：付款方式默认 OFFLINE_STORE。
     */
    public ProductOrderCreateRequest(Long storeId, String deliveryMethod, Long addressId,
                                     String contactName, String contactPhone, String remark,
                                     String idempotencyKey) {
        this(storeId, deliveryMethod, addressId, contactName, contactPhone, remark, idempotencyKey, "OFFLINE_STORE");
    }

    /**
     * 解析后的付款方式，null/blank 归一化为 OFFLINE_STORE。
     */
    public String effectivePaymentMethod() {
        return (paymentMethod == null || paymentMethod.isBlank()) ? "OFFLINE_STORE" : paymentMethod;
    }
    /**
     * Cross-field validation: pickup requires storeId, express requires addressId.
     * Bean Validation invokes methods named {@code is...} annotated with
     * {@link jakarta.validation.constraints.AssertTrue @AssertTrue}.
     */
    @jakarta.validation.constraints.AssertTrue(message = "自提需选择门店，快递需选择收货地址")
    public boolean isValidDelivery() {
        if (deliveryMethod == null) {
            return true; // @NotNull on deliveryMethod handles the null case
        }
        if ("PICKUP".equals(deliveryMethod)) {
            return storeId != null;
        }
        if ("EXPRESS".equals(deliveryMethod)) {
            return addressId != null;
        }
        return false;
    }
}
