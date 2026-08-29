package com.petcare.product.task;

import com.petcare.product.service.ProductOrderTimeoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 超时未支付商品订单的自动取消定时任务（隔夜遗弃单清理）。
 * <p>
 * 与 {@code BookingTimeoutTask} 共用 {@code petcare.order-timeout.enabled/cron} 开关，
 * 仅在 dev/prod profile 创建 bean；多实例部署需先引入分布式锁。
 */
@Component
@ConditionalOnProperty(prefix = "petcare.order-timeout", name = "enabled", havingValue = "true")
public class ProductOrderTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(ProductOrderTimeoutTask.class);

    private final ProductOrderTimeoutService productOrderTimeoutService;

    public ProductOrderTimeoutTask(ProductOrderTimeoutService productOrderTimeoutService) {
        this.productOrderTimeoutService = productOrderTimeoutService;
    }

    @Scheduled(cron = "${petcare.order-timeout.cron:0 */10 * * * *}")
    public void autoCancelOverdueUnpaidOrders() {
        try {
            int cancelled = productOrderTimeoutService.cancelOverdueUnpaidOrders(LocalDateTime.now());
            if (cancelled > 0) {
                log.info("Order timeout task: cancelled {} overdue unpaid orders", cancelled);
            }
        } catch (Exception e) {
            log.error("Order timeout task failed", e);
        }
    }
}
