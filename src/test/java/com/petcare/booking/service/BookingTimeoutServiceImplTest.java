package com.petcare.booking.service;

import com.petcare.booking.entity.ServiceBooking;
import com.petcare.booking.enums.BookingStatus;
import com.petcare.booking.mapper.ServiceBookingMapper;
import com.petcare.booking.service.impl.BookingTimeoutServiceImpl;
import com.petcare.common.config.OrderTimeoutProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
class BookingTimeoutServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 29, 15, 0);
    private static final LocalDate YESTERDAY = NOW.toLocalDate().minusDays(1);

    @Mock
    private ServiceBookingMapper serviceBookingMapper;
    @Mock
    private BookingTransactionService bookingTransactionService;

    private BookingTimeoutServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookingTimeoutServiceImpl(serviceBookingMapper,
                bookingTransactionService, new OrderTimeoutProperties(2, 1440));
    }

    @Test
    @DisplayName("批次中单条取消失败（如行被并发占用）只跳过该条，不中断其余")
    void singleFailureDoesNotBreakBatch() {
        ServiceBooking first = overdueBooking(1L);
        ServiceBooking second = overdueBooking(2L);
        when(serviceBookingMapper.selectList(any())).thenReturn(List.of(first, second));
        when(bookingTransactionService.transitionStatusOnce(eq(1L), anyString(), anyString(),
                any(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("simulated lock conflict"));
        when(bookingTransactionService.transitionStatusOnce(eq(2L), anyString(), anyString(),
                any(), anyString(), anyString(), any()))
                .thenReturn(second);

        int cancelled = service.cancelOverdueUnpaidBookings(NOW);

        assertThat(cancelled).isEqualTo(1);
        verify(bookingTransactionService, times(2)).transitionStatusOnce(anyLong(), anyString(),
                anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("取消操作以 SYSTEM 身份执行且携带统一超时原因")
    void cancelsWithSystemOperatorAndTimeoutReason() {
        ServiceBooking booking = overdueBooking(7L);
        when(serviceBookingMapper.selectList(any())).thenReturn(List.of(booking));
        when(bookingTransactionService.transitionStatusOnce(anyLong(), anyString(), anyString(),
                any(), anyString(), anyString(), any())).thenReturn(booking);

        service.cancelOverdueUnpaidBookings(NOW);

        ArgumentCaptor<String> operatorType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> remark = ArgumentCaptor.forClass(String.class);
        verify(bookingTransactionService).transitionStatusOnce(eq(7L), eq(BookingStatus.CANCELLED.getCode()),
                operatorType.capture(), eq(null), remark.capture(), remark.capture(), eq(null));
        assertThat(operatorType.getValue()).isEqualTo("SYSTEM");
        assertThat(remark.getAllValues()).allSatisfy(v ->
                assertThat(v).isEqualTo("超时未支付，系统自动取消"));
    }

    @Test
    @DisplayName("endTime 缺失时以 startTime 判定过期")
    void fallsBackToStartTimeWhenEndTimeMissing() {
        ServiceBooking booking = overdueBooking(9L);
        booking.setEndTime(null);
        when(serviceBookingMapper.selectList(any())).thenReturn(List.of(booking));
        when(bookingTransactionService.transitionStatusOnce(anyLong(), anyString(), anyString(),
                any(), anyString(), anyString(), any())).thenReturn(booking);

        int cancelled = service.cancelOverdueUnpaidBookings(NOW);

        assertThat(cancelled).isEqualTo(1);
    }

    private ServiceBooking overdueBooking(long id) {
        ServiceBooking booking = new ServiceBooking();
        booking.setId(id);
        booking.setBookingDate(YESTERDAY);
        booking.setStartTime(LocalTime.of(9, 0));
        booking.setEndTime(LocalTime.of(10, 0));
        return booking;
    }
}
