package com.petcare.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.common.config.OrderTimeoutProperties;
import com.petcare.product.entity.ProductOrder;
import com.petcare.product.enums.ProductOrderStatus;
import com.petcare.product.mapper.ProductOrderMapper;
import com.petcare.product.service.ProductOrderTimeoutService;
import com.petcare.product.service.ProductOrderTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时未支付商品订单的自动取消实现。
 * <p>
 * 只清理 PENDING_CONFIRM + UNPAID 的隔夜遗弃单：一旦管理员确认接单
 * （进入 PREPARING/READY_FOR_PICKUP），门店已实际承接，是否继续履约
 * 交还人工处理，定时器不越权。宽限期默认 24 小时，为
 * 「到店自提 + 到店付款」保留当日有效窗口。
 */
@Service
public class ProductOrderTimeoutServiceImpl implements ProductOrderTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(ProductOrderTimeoutServiceImpl.class);

    static final String TIMEOUT_CANCEL_REASON = "超时未支付，系统自动取消";

    private final ProductOrderMapper productOrderMapper;
    private final ProductOrderTransactionService productOrderTransactionService;
    private final OrderTimeoutProperties properties;

    public ProductOrderTimeoutServiceImpl(ProductOrderMapper productOrderMapper,
                                          ProductOrderTransactionService productOrderTransactionService,
                                          OrderTimeoutProperties properties) {
        this.productOrderMapper = productOrderMapper;
        this.productOrderTransactionService = productOrderTransactionService;
        this.properties = properties;
    }

    @Override
    public int cancelOverdueUnpaidOrders(LocalDateTime now) {
        LocalDateTime cutoff = now.minusMinutes(properties.effectiveProductUnpaidMinutes());
        List<ProductOrder> candidates = productOrderMapper.selectList(
                new LambdaQueryWrapper<ProductOrder>()
                        .eq(ProductOrder::getStatus, ProductOrderStatus.PENDING_CONFIRM.getCode())
                        .eq(ProductOrder::getPaymentStatus, "UNPAID")
                        .le(ProductOrder::getCreateTime, cutoff));

        int cancelled = 0;
        for (ProductOrder order : candidates) {
            try {
                // 复用管理员取消的原子路径：行锁 + 状态机/可取消守卫 + 钱包退款 + 库存回补，
                // 取消原因落 merchant_remark。竞态下（定时器读到后、取消前被人工流转）
                // 守卫抛错，捕获后跳过该单。
                productOrderTransactionService.adminCancelOrder(order.getId(),
                        TIMEOUT_CANCEL_REASON, null);
                cancelled++;
            } catch (Exception e) {
                log.warn("Auto-cancel overdue order skipped: orderId={}, reason={}",
                        order.getId(), e.getMessage());
            }
        }
        return cancelled;
    }
}
