package com.petcare.product.service;

import com.petcare.common.config.OrderTimeoutProperties;
import com.petcare.product.entity.ProductOrder;
import com.petcare.product.mapper.ProductOrderMapper;
import com.petcare.product.service.impl.ProductOrderTimeoutServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超时取消批次的容错与参数传递（纯 Mockito 单元）。
 */
@ExtendWith(MockitoExtension.class)
class ProductOrderTimeoutServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 29, 15, 0);

    @Mock
    private ProductOrderMapper productOrderMapper;
    @Mock
    private ProductOrderTransactionService productOrderTransactionService;

    private ProductOrderTimeoutServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductOrderTimeoutServiceImpl(productOrderMapper,
                productOrderTransactionService, new OrderTimeoutProperties(2, 1440));
    }

    @Test
    @DisplayName("批次中单条取消失败只跳过该条，不中断其余")
    void singleFailureDoesNotBreakBatch() {
        ProductOrder first = overdueOrder(1L);
        ProductOrder second = overdueOrder(2L);
        when(productOrderMapper.selectList(any())).thenReturn(List.of(first, second));
        when(productOrderTransactionService.adminCancelOrder(eq(1L), anyString(), any()))
                .thenThrow(new RuntimeException("simulated lock conflict"));
        when(productOrderTransactionService.adminCancelOrder(eq(2L), anyString(), any()))
                .thenReturn(second);

        int cancelled = service.cancelOverdueUnpaidOrders(NOW);

        assertThat(cancelled).isEqualTo(1);
        verify(productOrderTransactionService, times(2)).adminCancelOrder(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("系统取消统一以「超时未支付」为原因落 merchant_remark")
    void cancelsWithTimeoutReason() {
        ProductOrder order = overdueOrder(7L);
        when(productOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(productOrderTransactionService.adminCancelOrder(anyLong(), anyString(), any()))
                .thenReturn(order);

        service.cancelOverdueUnpaidOrders(NOW);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(productOrderTransactionService).adminCancelOrder(eq(7L), reason.capture(), eq(null));
        assertThat(reason.getValue()).isEqualTo("超时未支付，系统自动取消");
    }

    private ProductOrder overdueOrder(long id) {
        ProductOrder order = new ProductOrder();
        order.setId(id);
        order.setStatus("PENDING_CONFIRM");
        order.setPaymentStatus("UNPAID");
        order.setCreateTime(NOW.minusHours(48));
        return order;
    }
}
