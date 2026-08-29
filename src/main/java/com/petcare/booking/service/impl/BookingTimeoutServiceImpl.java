package com.petcare.booking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.booking.entity.ServiceBooking;
import com.petcare.booking.enums.BookingStatus;
import com.petcare.booking.enums.PaymentStatus;
import com.petcare.booking.mapper.ServiceBookingMapper;
import com.petcare.booking.service.BookingTimeoutService;
import com.petcare.booking.service.BookingTransactionService;
import com.petcare.common.config.OrderTimeoutProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 超时未支付预约的自动取消实现。
 * <p>
 * 预约不强制在线支付（线下到店付合法存在），因此过期基准不是下单时间，
 * 而是「服务结束时间 + 宽限期」；且只清理仍未支付的活跃单——已支付单
 * 即使逾期也不动（是否核销属于门店经营动作，不归定时器管）。
 */
@Service
public class BookingTimeoutServiceImpl implements BookingTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(BookingTimeoutServiceImpl.class);

    static final String TIMEOUT_CANCEL_REASON = "超时未支付，系统自动取消";

    private final ServiceBookingMapper serviceBookingMapper;
    private final BookingTransactionService bookingTransactionService;
    private final OrderTimeoutProperties properties;

    public BookingTimeoutServiceImpl(ServiceBookingMapper serviceBookingMapper,
                                     BookingTransactionService bookingTransactionService,
                                     OrderTimeoutProperties properties) {
        this.serviceBookingMapper = serviceBookingMapper;
        this.bookingTransactionService = bookingTransactionService;
        this.properties = properties;
    }

    @Override
    public int cancelOverdueUnpaidBookings(LocalDateTime now) {
        LocalDateTime cutoff = now.minusHours(properties.effectiveBookingUnpaidGraceHours());
        List<ServiceBooking> candidates = serviceBookingMapper.selectList(
                new LambdaQueryWrapper<ServiceBooking>()
                        .in(ServiceBooking::getStatus,
                                BookingStatus.PENDING_CONFIRM.getCode(), BookingStatus.CONFIRMED.getCode())
                        .eq(ServiceBooking::getPaymentStatus, PaymentStatus.UNPAID.getCode())
                        .le(ServiceBooking::getBookingDate, cutoff.toLocalDate()));

        int cancelled = 0;
        for (ServiceBooking booking : candidates) {
            if (!isOverdue(booking, cutoff)) {
                continue;
            }
            try {
                // 复用与用户/管理员取消相同的原子路径：行锁 + 状态机守卫 + 钱包退款 + SYSTEM 日志。
                // 竞态下（定时器读到后、取消前被人工处理）状态机会抛非法流转，捕获后跳过该单。
                bookingTransactionService.transitionStatusOnce(booking.getId(),
                        BookingStatus.CANCELLED.getCode(), "SYSTEM", null,
                        TIMEOUT_CANCEL_REASON, TIMEOUT_CANCEL_REASON, null);
                cancelled++;
            } catch (Exception e) {
                log.warn("Auto-cancel overdue booking skipped: bookingId={}, reason={}",
                        booking.getId(), e.getMessage());
            }
        }
        return cancelled;
    }

    private boolean isOverdue(ServiceBooking booking, LocalDateTime cutoff) {
        LocalTime end = booking.getEndTime() != null ? booking.getEndTime() : booking.getStartTime();
        if (booking.getBookingDate() == null || end == null) {
            return false;
        }
        return LocalDateTime.of(booking.getBookingDate(), end).isBefore(cutoff);
    }
}
