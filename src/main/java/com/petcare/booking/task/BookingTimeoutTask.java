package com.petcare.booking.task;

import com.petcare.booking.service.BookingTimeoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 超时未支付预约的自动取消定时任务。
 * <p>
 * 仅当 {@code petcare.order-timeout.enabled=true}（dev/prod profile）时创建 bean；
 * 测试 profile 不加载该配置，任务不注册，避免定时器改动测试夹具。
 * 单机部署下 Spring 默认单线程调度器保证同方法不重叠；多实例部署需先引入分布式锁。
 */
@Component
@ConditionalOnProperty(prefix = "petcare.order-timeout", name = "enabled", havingValue = "true")
public class BookingTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(BookingTimeoutTask.class);

    private final BookingTimeoutService bookingTimeoutService;

    public BookingTimeoutTask(BookingTimeoutService bookingTimeoutService) {
        this.bookingTimeoutService = bookingTimeoutService;
    }

    @Scheduled(cron = "${petcare.order-timeout.cron:0 */10 * * * *}")
    public void autoCancelOverdueUnpaidBookings() {
        try {
            int cancelled = bookingTimeoutService.cancelOverdueUnpaidBookings(LocalDateTime.now());
            if (cancelled > 0) {
                log.info("Booking timeout task: cancelled {} overdue unpaid bookings", cancelled);
            }
        } catch (Exception e) {
            log.error("Booking timeout task failed", e);
        }
    }
}
