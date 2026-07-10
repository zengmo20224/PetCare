package com.petcare.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.product.entity.ProductOrder;
import com.petcare.product.mapper.ProductOrderMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 幂等订单回查辅助 bean（H1 安全修复）。
 *
 * <p>独立 bean，避免 {@link ProductOrderTransactionServiceImpl} 自调用代理失效。
 * 用 {@link Propagation#REQUIRES_NEW} 在新事务中回查，确保即使主下单事务
 * 即将回滚，也能看到已提交的首次订单（并发兜底）。
 */
@Component
public class ProductOrderIdempotencyHelper {

    private final ProductOrderMapper orderMapper;

    public ProductOrderIdempotencyHelper(ProductOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * 在新事务中按 (userId, idempotencyKey) 回查未删除的首次订单。
     * REQUIRES_NEW 保证读取到其他事务已提交的订单，不受主事务回滚影响。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ProductOrder findByIdempotencyKey(Long userId, String idempotencyKey) {
        LambdaQueryWrapper<ProductOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductOrder::getUserId, userId)
               .eq(ProductOrder::getIdempotencyKey, idempotencyKey)
               .eq(ProductOrder::getDeleted, 0)
               .last("LIMIT 1");
        return orderMapper.selectOne(wrapper);
    }
}
