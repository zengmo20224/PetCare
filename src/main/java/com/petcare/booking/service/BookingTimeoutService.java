package com.petcare.booking.service;

import java.time.LocalDateTime;

/**
 * 超时未支付预约的自动取消。
 */
public interface BookingTimeoutService {

    /**
     * 取消「服务结束时间已过宽限期且仍未支付」的活跃预约（PENDING_CONFIRM/CONFIRMED）。
     * 每条预约走独立的取消事务（状态机守卫 + 行锁 + 钱包退款 + SYSTEM 操作日志），
     * 单条失败只跳过不中断批次。
     *
     * @param now 当前时间（由调用方传入，便于测试与统一时钟）
     * @return 实际取消的预约数量
     */
    int cancelOverdueUnpaidBookings(LocalDateTime now);
}
