package com.petcare.booking.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.petcare.common.entity.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Entity for table `service_booking`.
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("service_booking")
public class ServiceBooking extends BaseEntity {

    @TableField("booking_no")
    private String bookingNo;

    @TableField("user_id")
    private Long userId;

    @TableField("pet_id")
    private Long petId;

    @TableField("store_id")
    private Long storeId;

    @TableField("service_item_id")
    private Long serviceItemId;

    @TableField("staff_id")
    private Long staffId;

    @TableField("service_mode")
    private String serviceMode;

    @TableField("booking_date")
    private LocalDate bookingDate;

    @TableField("start_time")
    private LocalTime startTime;

    @TableField("end_time")
    private LocalTime endTime;

    @TableField("address_id")
    private Long addressId;

    @TableField("distance_km")
    private BigDecimal distanceKm;

    @TableField("contact_name")
    private String contactName;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("price")
    private BigDecimal price;

    @TableField("payment_method")
    private String paymentMethod;

    @TableField("payment_status")
    private String paymentStatus;

    @TableField("status")
    private String status;

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

    @TableField("cancel_reason")
    private String cancelReason;

    /** 操作人审计列，由 MetaObjectHandler 填充（admin:{id}/user:{id}，系统写为 NULL）。 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
