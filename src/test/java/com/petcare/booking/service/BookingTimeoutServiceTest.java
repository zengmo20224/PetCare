package com.petcare.booking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.booking.entity.BookingStatusLog;
import com.petcare.booking.entity.ServiceBooking;
import com.petcare.booking.enums.BookingStatus;
import com.petcare.booking.mapper.ServiceBookingMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 超时未支付预约自动取消（H2 集成）。
 * 统一时钟 NOW=2026-08-29 15:00，宽限期默认 2 小时 → 过期线 13:00。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingTimeoutServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 29, 15, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final String TIMEOUT_REASON = "超时未支付，系统自动取消";

    @Autowired
    private BookingTimeoutService bookingTimeoutService;
    @Autowired
    private ServiceBookingMapper serviceBookingMapper;
    @Autowired
    private BookingStatusLogService bookingStatusLogService;

    @Test
    @DisplayName("过期未支付的已确认预约被自动取消，写入 SYSTEM 状态日志")
    void cancelsOverdueUnpaidConfirmedBooking() {
        ServiceBooking booking = insertBooking(BookingStatus.CONFIRMED.getCode(),
                "UNPAID", "OFFLINE_STORE", YESTERDAY, LocalTime.NOON);

        int cancelled = bookingTimeoutService.cancelOverdueUnpaidBookings(NOW);

        assertThat(cancelled).isEqualTo(1);
        ServiceBooking reloaded = serviceBookingMapper.selectById(booking.getId());
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.CANCELLED.getCode());
        assertThat(reloaded.getCancelTime()).isNotNull();
        assertThat(reloaded.getCancelReason()).isEqualTo(TIMEOUT_REASON);

        List<BookingStatusLog> logs = bookingStatusLogService.list(
                new LambdaQueryWrapper<BookingStatusLog>()
                        .eq(BookingStatusLog::getBookingId, booking.getId()));
        assertThat(logs).anySatisfy(l -> {
            assertThat(l.getOperatorType()).isEqualTo("SYSTEM");
            assertThat(l.getNewStatus()).isEqualTo(BookingStatus.CANCELLED.getCode());
        });
    }

    @Test
    @DisplayName("过期待确认（PENDING_CONFIRM）未支付预约同样被取消")
    void cancelsOverdueUnpaidPendingConfirmBooking() {
        insertBooking(BookingStatus.PENDING_CONFIRM.getCode(),
                "UNPAID", "OFFLINE_STORE", YESTERDAY, LocalTime.NOON);

        int cancelled = bookingTimeoutService.cancelOverdueUnpaidBookings(NOW);

        assertThat(cancelled).isEqualTo(1);
    }

    @Test
    @DisplayName("已支付（钱包）的过期预约不被取消")
    void keepsWalletPaidOverdueBooking() {
        ServiceBooking booking = insertBooking(BookingStatus.CONFIRMED.getCode(),
                "WALLET_PAID", "WALLET", YESTERDAY, LocalTime.NOON);

        int cancelled = bookingTimeoutService.cancelOverdueUnpaidBookings(NOW);

        assertThat(cancelled).isZero();
        assertThat(serviceBookingMapper.selectById(booking.getId()).getStatus())
                .isEqualTo(BookingStatus.CONFIRMED.getCode());
    }

    @Test
    @DisplayName("未来时间的未支付预约不被取消")
    void keepsFutureUnpaidBooking() {
        ServiceBooking booking = insertBooking(BookingStatus.CONFIRMED.getCode(),
                "UNPAID", "OFFLINE_STORE", TODAY.plusDays(3), LocalTime.NOON);

        int cancelled = bookingTimeoutService.cancelOverdueUnpaidBookings(NOW);

        assertThat(cancelled).isZero();
        assertThat(serviceBookingMapper.selectById(booking.getId()).getStatus())
                .isEqualTo(BookingStatus.CONFIRMED.getCode());
    }

    @Test
    @DisplayName("当天预约按结束时间+宽限期判定：12:00 结束已过期取消，14:00 结束保留")
    void graceWindowAppliesToSameDayBookings() {
        ServiceBooking overdueToday = insertBooking(BookingStatus.CONFIRMED.getCode(),
                "UNPAID", "OFFLINE_STORE", TODAY, LocalTime.of(12, 0));
        ServiceBooking stillInWindow = insertBooking(BookingStatus.CONFIRMED.getCode(),
                "UNPAID", "OFFLINE_STORE", TODAY, LocalTime.of(14, 0));

        int cancelled = bookingTimeoutService.cancelOverdueUnpaidBookings(NOW);

        assertThat(cancelled).isEqualTo(1);
        assertThat(serviceBookingMapper.selectById(overdueToday.getId()).getStatus())
                .isEqualTo(BookingStatus.CANCELLED.getCode());
        assertThat(serviceBookingMapper.selectById(stillInWindow.getId()).getStatus())
                .isEqualTo(BookingStatus.CONFIRMED.getCode());
    }

    @Test
    @DisplayName("已取消的预约是终态，不会被定时任务重复处理")
    void keepsAlreadyCancelledBooking() {
        ServiceBooking booking = insertBooking(BookingStatus.CANCELLED.getCode(),
                "UNPAID", "OFFLINE_STORE", YESTERDAY, LocalTime.NOON);

        int cancelled = bookingTimeoutService.cancelOverdueUnpaidBookings(NOW);

        assertThat(cancelled).isZero();
        assertThat(serviceBookingMapper.selectById(booking.getId()).getStatus())
                .isEqualTo(BookingStatus.CANCELLED.getCode());
    }

    private ServiceBooking insertBooking(String status, String paymentStatus,
                                         String paymentMethod, LocalDate date, LocalTime end) {
        ServiceBooking booking = new ServiceBooking();
        booking.setBookingNo("TB-TIMEOUT-" + System.nanoTime());
        booking.setUserId(9001L);
        booking.setStoreId(1L);
        booking.setServiceItemId(1L);
        booking.setServiceMode("STORE");
        booking.setBookingDate(date);
        booking.setStartTime(end.minusMinutes(60));
        booking.setEndTime(end);
        booking.setPrice(new BigDecimal("88.00"));
        booking.setPaymentMethod(paymentMethod);
        booking.setPaymentStatus(paymentStatus);
        booking.setStatus(status);
        serviceBookingMapper.insert(booking);
        return booking;
    }
}
