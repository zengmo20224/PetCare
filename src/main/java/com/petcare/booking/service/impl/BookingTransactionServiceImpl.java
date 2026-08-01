package com.petcare.booking.service.impl;

import com.petcare.booking.domain.BookingStateMachine;
import com.petcare.booking.entity.BookingStatusLog;
import com.petcare.booking.entity.ServiceBooking;
import com.petcare.booking.entity.StaffBookingLock;
import com.petcare.booking.mapper.ServiceBookingMapper;
import com.petcare.booking.mapper.StaffBookingLockMapper;
import com.petcare.booking.service.BookingStatusLogService;
import com.petcare.booking.service.BookingTransactionService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.service.entity.ServiceItem;
import com.petcare.service.service.ServiceItemService;
import com.petcare.staff.entity.Staff;
import com.petcare.staff.entity.StaffSkill;
import com.petcare.staff.mapper.StaffMapper;
import com.petcare.staff.mapper.StaffSkillMapper;
import com.petcare.wallet.service.WalletService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Transactional booking operations.
 * Each public method is @Transactional on a separate bean
 * to avoid self-invocation proxy issues.
 */
@Service
public class BookingTransactionServiceImpl implements BookingTransactionService {

    private static final Logger log = LoggerFactory.getLogger(BookingTransactionServiceImpl.class);

    private final StaffBookingLockMapper staffBookingLockMapper;
    private final ServiceBookingMapper serviceBookingMapper;
    private final BookingStatusLogService bookingStatusLogService;
    private final StaffMapper staffMapper;
    private final StaffSkillMapper staffSkillMapper;
    private final ServiceItemService serviceItemService;
    private final WalletService walletService;

    public BookingTransactionServiceImpl(StaffBookingLockMapper staffBookingLockMapper,
                                         ServiceBookingMapper serviceBookingMapper,
                                         BookingStatusLogService bookingStatusLogService,
                                         StaffMapper staffMapper,
                                         StaffSkillMapper staffSkillMapper,
                                         ServiceItemService serviceItemService,
                                         WalletService walletService) {
        this.staffBookingLockMapper = staffBookingLockMapper;
        this.serviceBookingMapper = serviceBookingMapper;
        this.bookingStatusLogService = bookingStatusLogService;
        this.staffMapper = staffMapper;
        this.staffSkillMapper = staffSkillMapper;
        this.serviceItemService = serviceItemService;
        this.walletService = walletService;
    }

    @Override
    @Transactional
    public ServiceBooking createBookingOnce(ServiceBooking booking) {
        Long staffId = booking.getStaffId();
        LocalDate bookingDate = booking.getBookingDate();
        boolean payByWallet = "WALLET".equals(booking.getPaymentMethod());

        // Step 0 (wallet): lock the wallet row FIRST to fix global lock order
        // (wallet → staff_booking_lock). Lazy-creates the wallet on first use.
        // Final deduction happens in Step 4b after the booking is persisted (so the ledger
        // can reference bookingId), still inside this same transaction (D-012).
        if (payByWallet) {
            walletService.lockWalletForUpdate(booking.getUserId());
        }

        // Step 1: Ensure lock point exists with a snowflake id
        long lockId = generateLockId();
        staffBookingLockMapper.upsertStaffBookingLock(lockId, staffId, bookingDate);

        // Step 2: Lock the staff-date row
        StaffBookingLock lock = staffBookingLockMapper.selectStaffBookingLockForUpdate(staffId, bookingDate);
        if (lock == null) {
            throw new BusinessException(ErrorCode.BOOKING_STAFF_UNAVAILABLE,
                    "无法锁定员工排班，请重试");
        }

        // Step 3: Check for time conflicts
        List<ServiceBooking> conflicts = serviceBookingMapper.selectConflictingBookings(
                staffId, bookingDate, booking.getStartTime(), booking.getEndTime(), null);
        if (!conflicts.isEmpty()) {
            log.info("Booking time conflict: staffId={}, date={}, {}-{}, conflicts={}",
                    staffId, bookingDate, booking.getStartTime(), booking.getEndTime(), conflicts.size());
            throw new BusinessException(ErrorCode.BOOKING_TIME_CONFLICT,
                    "预约时间与已有预约冲突，请选择其他时间");
        }

        // Step 4: Insert booking (assigns booking.id via snowflake)
        // 钱包支付即时到账：paymentStatus 直接置 WALLET_PAID；线下支付保持 UNPAID。
        if (payByWallet) {
            booking.setPaymentStatus("WALLET_PAID");
        }
        serviceBookingMapper.insert(booking);

        // Step 4b (wallet): deduct the booking price from the wallet in this same transaction.
        // If balance is insufficient, the thrown BusinessException rolls back the booking insert
        // (D-012: booking creation + wallet deduction atomic).
        if (payByWallet && booking.getPrice() != null) {
            String walletIdemKey = "wallet-booking-" + booking.getId();
            walletService.deductForPayment(
                    booking.getUserId(), booking.getPrice(), "SERVICE_BOOKING",
                    booking.getId(), walletIdemKey);
        }

        // Step 5: Write status log（初始状态由调用方决定，支持 PENDING_CONFIRM 或 CONFIRMED）
        writeStatusLog(booking.getId(), null, booking.getStatus(), "USER", booking.getUserId(), "创建预约");

        return booking;
    }

    @Override
    @Transactional
    public ServiceBooking reassignBookingOnce(Long bookingId, Long newStaffId,
                                              LocalDate bookingDate,
                                              LocalTime startTime, LocalTime endTime,
                                              Long operatorId) {
        // Step 1: Lock the booking row FIRST (fixed lock order: booking → staff-date)
        ServiceBooking booking = serviceBookingMapper.selectBookingForUpdate(bookingId);
        if (booking == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "预约不存在");
        }

        // Step 2: Validate booking status is reassignable (using locked data)
        String status = booking.getStatus();
        if (!"PENDING_CONFIRM".equals(status) && !"CONFIRMED".equals(status)) {
            throw new BusinessException(ErrorCode.BOOKING_STATUS_INVALID,
                    "只有待确认或已确认的预约可以改派员工");
        }

        // Step 2b: Re-validate new staff status and skill INSIDE transaction with row locks
        // (pre-validation in ApplicationService is outside the transaction)
        // Lock order: booking → staff → staff_skill → staff_booking_lock
        Staff newStaff = staffMapper.selectStaffForUpdate(newStaffId);
        if (newStaff == null || !"ACTIVE".equals(newStaff.getStatus())) {
            throw new BusinessException(ErrorCode.BOOKING_STAFF_UNAVAILABLE,
                    "该员工不存在或已停用");
        }
        ServiceItem item = serviceItemService.getById(booking.getServiceItemId());
        StaffSkill skill = staffSkillMapper.selectStaffSkillForUpdate(newStaffId, item.getCategoryId());
        if (skill == null) {
            throw new BusinessException(ErrorCode.BOOKING_STAFF_UNAVAILABLE,
                    "该员工不具备此服务技能");
        }

        // Step 3: Read actual date/time from the locked booking (ignore stale parameters)
        Long oldStaffId = booking.getStaffId();
        LocalDate actualDate = booking.getBookingDate();
        LocalTime actualStart = booking.getStartTime();
        LocalTime actualEnd = booking.getEndTime();

        // Step 4: Ensure lock point for new staff using actual date
        long lockId = generateLockId();
        staffBookingLockMapper.upsertStaffBookingLock(lockId, newStaffId, actualDate);

        // Step 5: Lock new staff-date row
        StaffBookingLock lock = staffBookingLockMapper.selectStaffBookingLockForUpdate(newStaffId, actualDate);
        if (lock == null) {
            throw new BusinessException(ErrorCode.BOOKING_STAFF_UNAVAILABLE,
                    "无法锁定新员工排班，请重试");
        }

        // Step 6: Check for conflicts with new staff using actual times (exclude current booking)
        List<ServiceBooking> conflicts = serviceBookingMapper.selectConflictingBookings(
                newStaffId, actualDate, actualStart, actualEnd, bookingId);
        if (!conflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.BOOKING_TIME_CONFLICT,
                    "新员工在该时间段已有预约冲突，请选择其他员工或时间");
        }

        // Step 7: Update booking staff
        booking.setStaffId(newStaffId);
        serviceBookingMapper.updateById(booking);

        // Step 8: Write status log
        writeStatusLog(bookingId, status, status,
                "ADMIN", operatorId,
                String.format("改派员工：从员工%d改派到员工%d", oldStaffId, newStaffId));

        return booking;
    }

    @Override
    @Transactional
    public ServiceBooking transitionStatusOnce(Long bookingId, String targetStatus,
                                               String operatorType, Long operatorId, String remark,
                                               String cancelReason, String merchantRemark) {
        // Step 1: Lock the booking row
        ServiceBooking booking = serviceBookingMapper.selectBookingForUpdate(bookingId);
        if (booking == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "预约不存在");
        }

        // Step 2: Capture REAL old status before any mutation
        String oldStatus = booking.getStatus();

        // Step 3: Validate state transition
        BookingStateMachine.validateTransition(oldStatus, targetStatus);

        // Step 4: Update status and status-specific fields
        booking.setStatus(targetStatus);
        LocalDateTime now = LocalDateTime.now();
        switch (targetStatus) {
            case "CONFIRMED" -> {
                booking.setConfirmTime(now);
                if (merchantRemark != null) {
                    booking.setMerchantRemark(merchantRemark);
                }
            }
            case "REJECTED" -> {
                if (cancelReason != null) {
                    booking.setCancelReason(cancelReason);
                }
            }
            case "CANCELLED" -> {
                booking.setCancelTime(now);
                if (cancelReason != null) {
                    booking.setCancelReason(cancelReason);
                }
                // Wallet refund: if the booking was paid by wallet, refund the original price
                // back to the user's wallet in this same transaction (D-012). The refund runs
                // BEFORE the status update is flushed, so any refund failure rolls back the
                // whole cancellation — we never end up with a CANCELLED booking and no refund.
                refundWalletIfPaidByWallet(booking);
            }
            case "COMPLETED" -> booking.setCompleteTime(now);
            default -> { /* IN_SERVICE has no extra fields */ }
        }
        serviceBookingMapper.updateById(booking);

        // Step 5: Write status log with correct old → new
        writeStatusLog(bookingId, oldStatus, targetStatus, operatorType, operatorId, remark);

        return booking;
    }

    private void writeStatusLog(Long bookingId, String oldStatus, String newStatus,
                                String operatorType, Long operatorId, String remark) {
        BookingStatusLog statusLog = new BookingStatusLog();
        statusLog.setBookingId(bookingId);
        statusLog.setOldStatus(oldStatus);
        statusLog.setNewStatus(newStatus);
        statusLog.setOperatorType(operatorType);
        statusLog.setOperatorId(operatorId);
        statusLog.setRemark(remark);
        bookingStatusLogService.save(statusLog);
    }

    /**
     * 如果预约是用钱包支付的（paymentMethod=WALLET），在同事务内把原价格退回用户钱包。
     * 与状态翻转同处一个事务，保证"预约取消了钱没退"不会发生（D-012）。
     * 非钱包支付预约（OFFLINE_STORE/OFFLINE_HOME）不触发钱包动作。
     */
    private void refundWalletIfPaidByWallet(ServiceBooking booking) {
        if (!"WALLET".equals(booking.getPaymentMethod())) {
            return;
        }
        if (booking.getPrice() == null) {
            return;
        }
        // 退款幂等键：基于预约 ID，保证同一预约取消多次只退一次。
        String refundIdemKey = "wallet-refund-booking-" + booking.getId();
        walletService.refundForCancellation(
                booking.getUserId(), booking.getPrice(), "SERVICE_BOOKING",
                booking.getId(), refundIdemKey);
    }

    /**
     * Generates a unique ID for the lock row using the project's snowflake ID generator.
     * Each call produces a unique, positive ID regardless of staff ID or date.
     * The actual primary key is preserved by the upsert (ON DUPLICATE KEY UPDATE / MERGE INTO)
     * on repeated calls for the same (staff_id, booking_date).
     */
    private long generateLockId() {
        return IdWorker.getId();
    }
}
