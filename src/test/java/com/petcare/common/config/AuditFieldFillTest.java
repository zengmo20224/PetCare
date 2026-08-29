package com.petcare.common.config;

import com.petcare.admin.security.AdminPrincipal;
import com.petcare.booking.entity.ServiceBooking;
import com.petcare.booking.mapper.ServiceBookingMapper;
import com.petcare.product.entity.ProductOrder;
import com.petcare.product.mapper.ProductOrderMapper;
import com.petcare.user.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 操作人审计列自动填充（H2 集成）：登录态按 admin:/user: 前缀填充并随操作切换，
 * 无登录态（定时任务等系统写入）保持 NULL。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditFieldFillTest {

    @Autowired
    private ServiceBookingMapper serviceBookingMapper;
    @Autowired
    private ProductOrderMapper productOrderMapper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("用户创建填充 user:{id}，管理员后续更新改写 update_by 为 admin:{id}")
    void fillsActorAndRewritesOnAdminUpdate() {
        authenticateAsUser(9001L);
        ServiceBooking booking = insertBooking();

        assertThat(booking.getCreateBy()).isEqualTo("user:9001");
        assertThat(booking.getUpdateBy()).isEqualTo("user:9001");

        authenticateAsAdmin(1L);
        booking.setMerchantRemark("管理员备注");
        serviceBookingMapper.updateById(booking);

        ServiceBooking reloaded = serviceBookingMapper.selectById(booking.getId());
        assertThat(reloaded.getCreateBy()).isEqualTo("user:9001");
        assertThat(reloaded.getUpdateBy()).isEqualTo("admin:1");
    }

    @Test
    @DisplayName("无登录态（系统写入）审计列保持 NULL")
    void leavesAuditColumnsNullWithoutAuthentication() {
        SecurityContextHolder.clearContext();
        ProductOrder order = insertOrder();

        assertThat(order.getCreateBy()).isNull();
        assertThat(order.getUpdateBy()).isNull();

        order.setMerchantRemark("系统自动取消");
        productOrderMapper.updateById(order);
        ProductOrder reloaded = productOrderMapper.selectById(order.getId());

        assertThat(reloaded.getCreateBy()).isNull();
        assertThat(reloaded.getUpdateBy()).isNull();
    }

    private void authenticateAsUser(Long userId) {
        UserPrincipal principal = new UserPrincipal(userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void authenticateAsAdmin(Long adminId) {
        AdminPrincipal principal = new AdminPrincipal(adminId, "audit_admin", "ADMIN", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private ServiceBooking insertBooking() {
        ServiceBooking booking = new ServiceBooking();
        booking.setBookingNo("TB-AUDIT-" + System.nanoTime());
        booking.setUserId(9001L);
        booking.setStoreId(1L);
        booking.setServiceItemId(1L);
        booking.setServiceMode("STORE");
        booking.setBookingDate(LocalDate.now());
        booking.setStartTime(LocalTime.of(10, 0));
        booking.setEndTime(LocalTime.of(11, 0));
        booking.setPrice(new BigDecimal("88.00"));
        booking.setPaymentMethod("OFFLINE_STORE");
        booking.setPaymentStatus("UNPAID");
        booking.setStatus("CONFIRMED");
        serviceBookingMapper.insert(booking);
        return booking;
    }

    private ProductOrder insertOrder() {
        ProductOrder order = new ProductOrder();
        order.setOrderNo("PO-AUDIT-" + System.nanoTime());
        order.setUserId(9001L);
        order.setStoreId(1L);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setDeliveryMethod("PICKUP");
        order.setPaymentMethod("OFFLINE_STORE");
        order.setPaymentStatus("UNPAID");
        order.setPickupStatus("WAIT_PREPARE");
        order.setStatus("PENDING_CONFIRM");
        order.setContactName("审计测试用户");
        order.setContactPhone("13800000002");
        productOrderMapper.insert(order);
        return order;
    }
}
