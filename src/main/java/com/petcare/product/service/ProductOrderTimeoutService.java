package com.petcare.product.service;

import java.time.LocalDateTime;

/**
 * 超时未支付商品订单的自动取消。
 */
public interface ProductOrderTimeoutService {

    /**
     * 取消「创建时间已过宽限期且仍未支付」的待确认订单（PENDING_CONFIRM + UNPAID），
     * 同事务恢复库存并退款（若钱包支付）。单条失败只跳过不中断批次。
     *
     * @param now 当前时间（由调用方传入，便于测试与统一时钟）
     * @return 实际取消的订单数量
     */
    int cancelOverdueUnpaidOrders(LocalDateTime now);
}
