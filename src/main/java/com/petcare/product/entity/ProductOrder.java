package com.petcare.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.petcare.common.entity.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for table `product_order`.
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("product_order")
public class ProductOrder extends BaseEntity {

    @TableField("order_no")
    private String orderNo;

    @TableField("user_id")
    private Long userId;

    @TableField("store_id")
    private Long storeId;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("delivery_method")
    private String deliveryMethod;

    @TableField("address_id")
    private Long addressId;

    @TableField("address_snapshot")
    private String addressSnapshot;

    @TableField("payment_method")
    private String paymentMethod;

    @TableField("payment_status")
    private String paymentStatus;

    @TableField("pickup_status")
    private String pickupStatus;

    @TableField("status")
    private String status;

    @TableField("contact_name")
    private String contactName;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("remark")
    private String remark;

    @TableField("merchant_remark")
    private String merchantRemark;

    @TableField("confirm_time")
    private LocalDateTime confirmTime;

    @TableField("complete_time")
    private LocalDateTime completeTime;

    @TableField("cancel_time")
    private LocalDateTime cancelTime;

    /**
     * 客户端幂等键。与 user_id 共同构成唯一约束，防止双击/重放下重复下单。
     * NULL 表示该订单未启用幂等（历史数据/非幂等调用方）。
     */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /** 操作人审计列，由 MetaObjectHandler 填充（admin:{id}/user:{id}，系统写为 NULL）。 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
