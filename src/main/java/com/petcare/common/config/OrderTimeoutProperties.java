package com.petcare.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 订单/预约超时自动治理配置。
 * <p>
 * 绑定 {@code petcare.order-timeout.*}。enabled/cron 同样挂在该前缀下：
 * enabled 由 @ConditionalOnProperty 门控定时任务 bean 的创建，cron 供 @Scheduled 占位符读取。
 * 该配置只写入 dev/prod profile——测试 profile 不加载，任务 bean 不创建，
 * 避免长跑的集成测试套件被定时器改动夹具数据。
 */
@ConfigurationProperties(prefix = "petcare.order-timeout")
public record OrderTimeoutProperties(
        Integer bookingUnpaidGraceHours,
        Integer productUnpaidMinutes
) {

    /**
     * 预约服务结束时间超过该小时数仍未支付即视为过期（线下到店付合法存在，
     * 不能按下单时间计超时，只能按预约时间本身）。
     */
    public int effectiveBookingUnpaidGraceHours() {
        return bookingUnpaidGraceHours == null || bookingUnpaidGraceHours <= 0
                ? 2 : bookingUnpaidGraceHours;
    }

    /**
     * 商品订单创建超过该分钟数仍未支付即视为过期（默认 24 小时，
     * 为到店自提+线下付款保留当日有效窗口，只清理隔夜遗弃单）。
     */
    public int effectiveProductUnpaidMinutes() {
        return productUnpaidMinutes == null || productUnpaidMinutes <= 0
                ? 1440 : productUnpaidMinutes;
    }
}
