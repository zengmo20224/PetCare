package com.petcare.booking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petcare.booking.domain.BookingAvailabilityCalculator;
import com.petcare.booking.domain.BookingDistanceCalculator;
import com.petcare.booking.domain.StaffAssignmentPolicy;
import com.petcare.booking.dto.BookingAvailabilityRequest;
import com.petcare.booking.dto.BookingAvailabilityResponse;
import com.petcare.booking.dto.BookingAvailabilityResponse.SlotInfo;
import com.petcare.booking.dto.BookingCancelRequest;
import com.petcare.booking.dto.BookingCreateRequest;
import com.petcare.booking.dto.BookingReassignRequest;
import com.petcare.booking.dto.BookingResponse;
import com.petcare.booking.entity.ServiceBooking;
import com.petcare.booking.entity.StaffSchedule;
import com.petcare.booking.entity.StaffUnavailableTime;
import com.petcare.booking.service.BookingApplicationService;
import com.petcare.booking.service.BookingRetryService;
import com.petcare.booking.service.BookingTransactionService;
import com.petcare.booking.service.BookingStatusLogService;
import com.petcare.booking.service.ServiceBookingService;
import com.petcare.booking.service.StaffScheduleService;
import com.petcare.booking.service.StaffUnavailableTimeService;
import com.petcare.admin.entity.AdminOperationLog;
import com.petcare.admin.service.AdminOperationLogService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import com.petcare.service.entity.ServiceItem;
import com.petcare.service.service.ServiceItemService;
import com.petcare.staff.entity.Staff;
import com.petcare.staff.entity.StaffSkill;
import com.petcare.staff.service.StaffService;
import com.petcare.staff.service.StaffSkillService;
import com.petcare.store.entity.Store;
import com.petcare.store.entity.StoreConfig;
import com.petcare.store.service.StoreConfigService;
import com.petcare.store.service.StoreService;
import com.petcare.user.entity.UserAddress;
import com.petcare.user.service.UserAddressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core booking application service.
 * Orchestrates domain rules, data loading, and transaction delegation.
 */
@Service
public class BookingApplicationServiceImpl implements BookingApplicationService {

    private static final Logger log = LoggerFactory.getLogger(BookingApplicationServiceImpl.class);

    private final ServiceItemService serviceItemService;
    private final StoreConfigService storeConfigService;
    private final StoreService storeService;
    private final StaffSkillService staffSkillService;
    private final StaffService staffService;
    private final StaffScheduleService staffScheduleService;
    private final StaffUnavailableTimeService staffUnavailableTimeService;
    private final ServiceBookingService serviceBookingService;
    private final BookingStatusLogService bookingStatusLogService;
    private final UserAddressService userAddressService;
    private final BookingRetryService bookingRetryService;
    private final BookingTransactionService bookingTransactionService;
    private final AdminOperationLogService operationLogService;

    public BookingApplicationServiceImpl(ServiceItemService serviceItemService,
                                         StoreConfigService storeConfigService,
                                         StoreService storeService,
                                         StaffSkillService staffSkillService,
                                         StaffService staffService,
                                         StaffScheduleService staffScheduleService,
                                         StaffUnavailableTimeService staffUnavailableTimeService,
                                         ServiceBookingService serviceBookingService,
                                         BookingStatusLogService bookingStatusLogService,
                                         UserAddressService userAddressService,
                                         BookingRetryService bookingRetryService,
                                         BookingTransactionService bookingTransactionService,
                                         AdminOperationLogService operationLogService) {
        this.serviceItemService = serviceItemService;
        this.storeConfigService = storeConfigService;
        this.storeService = storeService;
        this.staffSkillService = staffSkillService;
        this.staffService = staffService;
        this.staffScheduleService = staffScheduleService;
        this.staffUnavailableTimeService = staffUnavailableTimeService;
        this.serviceBookingService = serviceBookingService;
        this.bookingStatusLogService = bookingStatusLogService;
        this.userAddressService = userAddressService;
        this.bookingRetryService = bookingRetryService;
        this.bookingTransactionService = bookingTransactionService;
        this.operationLogService = operationLogService;
    }

    // ========== Availability ==========

    @Override
    public BookingAvailabilityResponse getAvailability(BookingAvailabilityRequest request) {
        // 1. Load and validate service item
        ServiceItem item = serviceItemService.getOnSaleItem(request.serviceItemId());

        // 2. Validate service mode compatibility
        validateServiceMode(item, request.serviceMode());

        // 3. Load store config and validate date range
        StoreConfig config = getStoreConfig(request.storeId());
        validateBookingDate(request.bookingDate(), config);

        // 4. Find staff with matching skill
        List<StaffSkill> skills = staffSkillService.list(new LambdaQueryWrapper<StaffSkill>()
                .eq(StaffSkill::getServiceCategoryId, item.getCategoryId()));
        List<Long> skilledStaffIds = skills.stream().map(StaffSkill::getStaffId).distinct().toList();

        // Filter active staff
        List<Staff> activeStaff = staffService.list(new LambdaQueryWrapper<Staff>()
                .in(Staff::getId, skilledStaffIds)
                .eq(Staff::getStatus, "ACTIVE"));
        List<Long> activeStaffIds = activeStaff.stream().map(Staff::getId).toList();

        if (activeStaffIds.isEmpty()) {
            return new BookingAvailabilityResponse(request.storeId(), request.serviceItemId(),
                    request.bookingDate(), request.serviceMode(),
                    item.getDurationMinutes(), config.getTimeSlotMinutes(), List.of());
        }

        // 5. Load schedules
        Map<Long, List<StaffSchedule>> schedulesByStaff = new HashMap<>();
        List<StaffSchedule> allSchedules = staffScheduleService.list(new LambdaQueryWrapper<StaffSchedule>()
                .in(StaffSchedule::getStaffId, activeStaffIds)
                .eq(StaffSchedule::getWorkDate, request.bookingDate())
                .eq(StaffSchedule::getStatus, "AVAILABLE"));
        for (StaffSchedule s : allSchedules) {
            schedulesByStaff.computeIfAbsent(s.getStaffId(), k -> new java.util.ArrayList<>()).add(s);
        }

        // 6. Load unavailable times
        Map<Long, List<StaffUnavailableTime>> unavailableByStaff = new HashMap<>();
        List<StaffUnavailableTime> allUnavailable = staffUnavailableTimeService.list(
                new LambdaQueryWrapper<StaffUnavailableTime>()
                        .in(StaffUnavailableTime::getStaffId, activeStaffIds)
                        .eq(StaffUnavailableTime::getUnavailableDate, request.bookingDate()));
        for (StaffUnavailableTime u : allUnavailable) {
            unavailableByStaff.computeIfAbsent(u.getStaffId(), k -> new java.util.ArrayList<>()).add(u);
        }

        // 7. Load active bookings
        Map<Long, List<ServiceBooking>> bookingsByStaff = new HashMap<>();
        List<ServiceBooking> activeBookings = serviceBookingService.list(new LambdaQueryWrapper<ServiceBooking>()
                .in(ServiceBooking::getStaffId, activeStaffIds)
                .eq(ServiceBooking::getBookingDate, request.bookingDate())
                .in(ServiceBooking::getStatus, "PENDING_CONFIRM", "CONFIRMED", "IN_SERVICE"));
        for (ServiceBooking b : activeBookings) {
            bookingsByStaff.computeIfAbsent(b.getStaffId(), k -> new java.util.ArrayList<>()).add(b);
        }

        // 8. Calculate available slots
        Map<LocalTime, Integer> slotCounts = BookingAvailabilityCalculator.calculateAvailableSlots(
                schedulesByStaff, unavailableByStaff, bookingsByStaff,
                config.getTimeSlotMinutes(), item.getDurationMinutes());

        // 8b. 过滤当天已过时段：若预约日期是今天，剔除开始时间已不晚于"现在"的时段。
        // 例如现在 10:00，则 10:00 及更早的时段不可选，11:00 起仍可选。
        if (request.bookingDate().isEqual(LocalDate.now())) {
            LocalTime cutoff = LocalTime.now();
            slotCounts.entrySet().removeIf(e -> !e.getKey().isAfter(cutoff));
        }

        // 9. Build response (no staffId exposed)
        List<SlotInfo> slots = slotCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new SlotInfo(e.getKey(),
                        e.getKey().plusMinutes(item.getDurationMinutes()),
                        e.getValue()))
                .toList();

        return new BookingAvailabilityResponse(request.storeId(), request.serviceItemId(),
                request.bookingDate(), request.serviceMode(),
                item.getDurationMinutes(), config.getTimeSlotMinutes(), slots);
    }

    // ========== User Operations ==========

    @Override
    public BookingResponse createBooking(Long currentUserId, BookingCreateRequest request) {
        // 1. Load and validate service item
        ServiceItem item = serviceItemService.getOnSaleItem(request.serviceItemId());

        // 2. Validate service mode
        validateServiceMode(item, request.serviceMode());

        // 3. Load store config and validate date
        StoreConfig config = getStoreConfig(request.storeId());
        validateBookingDate(request.bookingDate(), config);

        // 4. Validate HOME mode requirements
        BigDecimal distanceKm = null;
        if ("HOME".equals(request.serviceMode())) {
            if (request.addressId() == null) {
                throw new BusinessException(ErrorCode.BOOKING_ADDRESS_REQUIRED, "上门服务必须选择地址");
            }
            UserAddress address = userAddressService.getOne(new LambdaQueryWrapper<UserAddress>()
                    .eq(UserAddress::getId, request.addressId())
                    .eq(UserAddress::getUserId, currentUserId));
            if (address == null) {
                throw new BusinessException(ErrorCode.BOOKING_ADDRESS_NOT_FOUND, "地址不存在或不属于当前用户");
            }
            if (address.getLongitude() == null || address.getLatitude() == null) {
                throw new BusinessException(ErrorCode.BOOKING_ADDRESS_REQUIRED, "地址缺少经纬度信息");
            }

            Store store = storeService.getById(request.storeId());
            if (store.getLongitude() == null || store.getLatitude() == null) {
                throw new BusinessException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE, "门店缺少经纬度信息");
            }

            distanceKm = BookingDistanceCalculator.calculateDistance(
                    address.getLatitude(), address.getLongitude(),
                    store.getLatitude(), store.getLongitude());
            BookingDistanceCalculator.validateHomeServiceDistance(distanceKm, config.getHomeServiceRadiusKm());
        }

        // 5. Find available staff for the requested time slot
        LocalTime endTime = request.startTime().plusMinutes(item.getDurationMinutes());
        List<StaffSkill> skills = staffSkillService.list(new LambdaQueryWrapper<StaffSkill>()
                .eq(StaffSkill::getServiceCategoryId, item.getCategoryId()));
        List<Long> skilledStaffIds = skills.stream().map(StaffSkill::getStaffId).distinct().toList();

        List<Staff> activeStaff = staffService.list(new LambdaQueryWrapper<Staff>()
                .in(Staff::getId, skilledStaffIds)
                .eq(Staff::getStatus, "ACTIVE"));
        List<Long> activeStaffIds = activeStaff.stream().map(Staff::getId).toList();

        if (activeStaffIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BOOKING_STAFF_UNAVAILABLE, "当前没有具备该服务技能的可用员工");
        }

        // Count existing bookings per staff for this date
        Map<Long, Integer> bookingCounts = new HashMap<>();
        List<ServiceBooking> existingBookings = serviceBookingService.list(new LambdaQueryWrapper<ServiceBooking>()
                .in(ServiceBooking::getStaffId, activeStaffIds)
                .eq(ServiceBooking::getBookingDate, request.bookingDate())
                .in(ServiceBooking::getStatus, "PENDING_CONFIRM", "CONFIRMED", "IN_SERVICE"));
        for (ServiceBooking b : existingBookings) {
            bookingCounts.merge(b.getStaffId(), 1, Integer::sum);
        }

        // 6. Select staff via policy
        Long selectedStaffId = StaffAssignmentPolicy.selectStaff(activeStaffIds, bookingCounts);

        // 7. Build booking entity
        ServiceBooking booking = new ServiceBooking();
        booking.setBookingNo("BK" + System.nanoTime()); // Simplified; snowflake ID based
        booking.setUserId(currentUserId);
        booking.setPetId(request.petId());
        booking.setStoreId(request.storeId());
        booking.setServiceItemId(request.serviceItemId());
        booking.setStaffId(selectedStaffId);
        booking.setServiceMode(request.serviceMode());
        booking.setBookingDate(request.bookingDate());
        booking.setStartTime(request.startTime());
        booking.setEndTime(endTime);
        booking.setAddressId(request.addressId());
        booking.setDistanceKm(distanceKm);
        booking.setContactName(request.contactName());
        booking.setContactPhone(request.contactPhone());
        booking.setPrice(item.getPrice());
        booking.setPaymentMethod(request.paymentMethod());
        booking.setPaymentStatus("UNPAID");
        // 排班正常有位置即直接确认（去掉待确认环节，符合"预约即生效"的业务预期）
        booking.setStatus("CONFIRMED");
        booking.setConfirmTime(LocalDateTime.now());
        booking.setRemark(request.remark());

        // 8. Delegate to retry service (handles concurrency)
        ServiceBooking saved = bookingRetryService.createBookingWithRetry(booking);

        return toResponse(saved);
    }

    @Override
    public PageResponse<BookingResponse> getMyBookings(Long currentUserId, int page, int size) {
        int effectiveSize = Math.min(Math.max(size, 1), 100);
        Page<ServiceBooking> pageParam = new Page<>(page, effectiveSize);
        IPage<ServiceBooking> result = serviceBookingService.page(pageParam,
                new LambdaQueryWrapper<ServiceBooking>()
                        .eq(ServiceBooking::getUserId, currentUserId)
                        .orderByDesc(ServiceBooking::getCreateTime));
        List<BookingResponse> items = result.getRecords().stream().map(this::toResponse).toList();
        return PageResponse.of(items, result.getTotal(), page, effectiveSize);
    }

    @Override
    public BookingResponse getBooking(Long currentUserId, Long bookingId) {
        ServiceBooking booking = getBookingOrThrow(bookingId);
        if (!booking.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "预约不存在");
        }
        return toResponse(booking);
    }

    @Override
    public BookingResponse cancelBooking(Long currentUserId, Long bookingId, BookingCancelRequest request) {
        ServiceBooking booking = getBookingOrThrow(bookingId);
        if (!booking.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "预约不存在");
        }

        // Check cancel time window (business rule, not state machine)
        StoreConfig config = getStoreConfig(booking.getStoreId());
        validateCancelTimeWindow(booking, config);

        // Delegate atomic transition (locks row, validates, updates, writes log)
        ServiceBooking updated = bookingTransactionService.transitionStatusOnce(
                bookingId, "CANCELLED", "USER", currentUserId, "用户取消预约",
                request.reason(), null);

        return toResponse(updated);
    }

    // ========== Admin Operations ==========

    @Override
    public PageResponse<BookingResponse> listAdminBookings(int page, int size, String status, String bookingDate) {
        int effectiveSize = Math.min(Math.max(size, 1), 100);
        Page<ServiceBooking> pageParam = new Page<>(page, effectiveSize);

        LambdaQueryWrapper<ServiceBooking> wrapper = new LambdaQueryWrapper<ServiceBooking>()
                .orderByDesc(ServiceBooking::getCreateTime);
        if (status != null && !status.isBlank()) {
            wrapper.eq(ServiceBooking::getStatus, status);
        }
        if (bookingDate != null && !bookingDate.isBlank()) {
            wrapper.eq(ServiceBooking::getBookingDate, LocalDate.parse(bookingDate));
        }

        IPage<ServiceBooking> result = serviceBookingService.page(pageParam, wrapper);
        List<BookingResponse> items = result.getRecords().stream().map(this::toResponse).toList();
        return PageResponse.of(items, result.getTotal(), page, effectiveSize);
    }

    @Override
    public BookingResponse getAdminBooking(Long bookingId) {
        return toResponse(getBookingOrThrow(bookingId));
    }

    @Override
    @Transactional
    public BookingResponse confirmBooking(Long bookingId, String merchantRemark, Long operatorId) {
        String url = "/api/v1/admin/bookings/" + bookingId + "/confirm";
        String params = "bookingId=" + bookingId;
        try {
            ServiceBooking updated = bookingTransactionService.transitionStatusOnce(
                    bookingId, "CONFIRMED", "ADMIN", operatorId, "管理员确认预约",
                    null, merchantRemark);
            auditSuccess(operatorId, "confirm-booking", url, params);
            return toResponse(updated);
        } catch (RuntimeException e) {
            auditFail(operatorId, "confirm-booking", url, params, e);
            throw e;
        }
    }

    @Override
    @Transactional
    public BookingResponse rejectBooking(Long bookingId, String reason, Long operatorId) {
        String url = "/api/v1/admin/bookings/" + bookingId + "/reject";
        String params = "bookingId=" + bookingId;
        try {
            ServiceBooking updated = bookingTransactionService.transitionStatusOnce(
                    bookingId, "REJECTED", "ADMIN", operatorId, "管理员拒绝预约：" + reason,
                    reason, null);
            auditSuccess(operatorId, "reject-booking", url, params);
            return toResponse(updated);
        } catch (RuntimeException e) {
            auditFail(operatorId, "reject-booking", url, params, e);
            throw e;
        }
    }

    @Override
    @Transactional
    public BookingResponse startBooking(Long bookingId, Long operatorId) {
        String url = "/api/v1/admin/bookings/" + bookingId + "/start";
        String params = "bookingId=" + bookingId;
        try {
            ServiceBooking updated = bookingTransactionService.transitionStatusOnce(
                    bookingId, "IN_SERVICE", "ADMIN", operatorId, "管理员开始服务",
                    null, null);
            auditSuccess(operatorId, "start-booking", url, params);
            return toResponse(updated);
        } catch (RuntimeException e) {
            auditFail(operatorId, "start-booking", url, params, e);
            throw e;
        }
    }

    @Override
    @Transactional
    public BookingResponse completeBooking(Long bookingId, Long operatorId) {
        String url = "/api/v1/admin/bookings/" + bookingId + "/complete";
        String params = "bookingId=" + bookingId;
        try {
            ServiceBooking updated = bookingTransactionService.transitionStatusOnce(
                    bookingId, "COMPLETED", "ADMIN", operatorId, "管理员完成服务",
                    null, null);
            auditSuccess(operatorId, "complete-booking", url, params);
            return toResponse(updated);
        } catch (RuntimeException e) {
            auditFail(operatorId, "complete-booking", url, params, e);
            throw e;
        }
    }

    @Override
    @Transactional
    public BookingResponse cancelBookingAdmin(Long bookingId, String reason, Long operatorId) {
        String url = "/api/v1/admin/bookings/" + bookingId + "/cancel";
        String params = "bookingId=" + bookingId;
        try {
            ServiceBooking updated = bookingTransactionService.transitionStatusOnce(
                    bookingId, "CANCELLED", "ADMIN", operatorId, "管理员取消预约：" + reason,
                    reason, null);
            auditSuccess(operatorId, "cancel-booking", url, params);
            return toResponse(updated);
        } catch (RuntimeException e) {
            auditFail(operatorId, "cancel-booking", url, params, e);
            throw e;
        }
    }

    @Override
    @Transactional
    public BookingResponse reassignBooking(Long bookingId, BookingReassignRequest request, Long operatorId) {
        String url = "/api/v1/admin/bookings/" + bookingId + "/reassign";
        String params = "bookingId=" + bookingId + ",newStaffId=" + request.newStaffId();
        try {
            // Pre-validate staff skill and status (optimistic checks before transaction)
            ServiceItem item = serviceItemService.getById(
                    getBookingOrThrow(bookingId).getServiceItemId());
            StaffSkill skill = staffSkillService.getOne(new LambdaQueryWrapper<StaffSkill>()
                    .eq(StaffSkill::getStaffId, request.newStaffId())
                    .eq(StaffSkill::getServiceCategoryId, item.getCategoryId()));
            if (skill == null) {
                throw new BusinessException(ErrorCode.BOOKING_STAFF_UNAVAILABLE, "该员工不具备此服务技能");
            }

            Staff newStaff = staffService.getById(request.newStaffId());
            if (newStaff == null || !"ACTIVE".equals(newStaff.getStatus())) {
                throw new BusinessException(ErrorCode.BOOKING_STAFF_UNAVAILABLE, "该员工不存在或已停用");
            }

            // Delegate to transaction service (locks booking, validates status, uses locked snapshot)
            ServiceBooking booking = getBookingOrThrow(bookingId);
            bookingTransactionService.reassignBookingOnce(bookingId, request.newStaffId(),
                    booking.getBookingDate(), booking.getStartTime(), booking.getEndTime(), operatorId);

            auditSuccess(operatorId, "reassign-booking", url, params);
            return toResponse(serviceBookingService.getById(bookingId));
        } catch (RuntimeException e) {
            auditFail(operatorId, "reassign-booking", url, params, e);
            throw e;
        }
    }

    // ========== Helper Methods ==========

    private void auditSuccess(Long operatorId, String operation, String url, String params) {
        AdminOperationLog entry = buildAuditEntry(operatorId, operation, url, params);
        entry.setResult("SUCCESS");
        if (!operationLogService.save(entry)) {
            throw new IllegalStateException("Failed to persist required admin operation log");
        }
    }

    private void auditFail(Long operatorId, String operation, String url,
                           String params, RuntimeException cause) {
        AdminOperationLog entry = buildAuditEntry(operatorId, operation, url, params);
        entry.setResult("FAIL");
        entry.setErrorMessage(sanitizeErrorMessage(cause));
        try {
            if (!operationLogService.saveFailLog(entry)) {
                log.warn("Failed to write FAIL admin operation log: operatorId={}, operation={}",
                        operatorId, operation);
            }
        } catch (RuntimeException auditException) {
            log.warn("Failed to write FAIL admin operation log: operatorId={}, operation={}",
                    operatorId, operation);
        }
    }

    private AdminOperationLog buildAuditEntry(Long operatorId, String operation,
                                               String url, String params) {
        AdminOperationLog entry = new AdminOperationLog();
        entry.setAdminId(operatorId);
        entry.setModule("booking");
        entry.setOperation(operation);
        entry.setRequestMethod("POST");
        entry.setRequestUrl(url);
        entry.setRequestParams(params);
        entry.setCreateTime(LocalDateTime.now());
        return entry;
    }

    private String sanitizeErrorMessage(RuntimeException exception) {
        if (exception instanceof BusinessException && exception.getMessage() != null) {
            return exception.getMessage().substring(0, Math.min(exception.getMessage().length(), 1000));
        }
        return "unexpected_error";
    }

    private void validateServiceMode(ServiceItem item, String requestedMode) {
        String itemMode = item.getServiceMode();
        if ("STORE".equals(itemMode) && "HOME".equals(requestedMode)) {
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE,
                    "该服务仅支持到店服务");
        }
        if ("HOME".equals(itemMode) && "STORE".equals(requestedMode)) {
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE,
                    "该服务仅支持上门服务");
        }
    }

    private StoreConfig getStoreConfig(Long storeId) {
        StoreConfig config = storeConfigService.getOne(new LambdaQueryWrapper<StoreConfig>()
                .eq(StoreConfig::getStoreId, storeId));
        if (config == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "门店配置不存在");
        }
        return config;
    }

    private void validateBookingDate(LocalDate bookingDate, StoreConfig config) {
        LocalDate today = LocalDate.now();
        if (bookingDate.isBefore(today)) {
            throw new BusinessException(ErrorCode.BOOKING_DATE_OUT_OF_RANGE, "预约日期不能早于今天");
        }
        LocalDate maxDate = today.plusDays(config.getBookingAdvanceDays());
        if (bookingDate.isAfter(maxDate)) {
            throw new BusinessException(ErrorCode.BOOKING_DATE_OUT_OF_RANGE,
                    String.format("预约日期不能超过%d天后", config.getBookingAdvanceDays()));
        }
    }

    private void validateCancelTimeWindow(ServiceBooking booking, StoreConfig config) {
        if (booking.getBookingDate().isBefore(LocalDate.now())) {
            return; // Past date bookings can always be cancelled
        }
        if (booking.getBookingDate().isEqual(LocalDate.now())) {
            // Check if cancel is within allowed hours before service start
            LocalDateTime serviceStart = LocalDateTime.of(booking.getBookingDate(), booking.getStartTime());
            LocalDateTime cancelDeadline = serviceStart.minusHours(config.getBookingCancelHours());
            if (LocalDateTime.now().isAfter(cancelDeadline)) {
                throw new BusinessException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE,
                        String.format("需要在服务开始前%d小时取消预约", config.getBookingCancelHours()));
            }
        }
    }

    private ServiceBooking getBookingOrThrow(Long bookingId) {
        ServiceBooking booking = serviceBookingService.getById(bookingId);
        if (booking == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "预约不存在");
        }
        return booking;
    }

    private BookingResponse toResponse(ServiceBooking b) {
        String serviceItemName = null;
        if (b.getServiceItemId() != null) {
            ServiceItem item = serviceItemService.getById(b.getServiceItemId());
            serviceItemName = item != null ? item.getName() : null;
        }
        return new BookingResponse(
                b.getId(), b.getBookingNo(), b.getUserId(), b.getPetId(),
                b.getStoreId(), b.getServiceItemId(), serviceItemName, b.getStaffId(),
                b.getServiceMode(), b.getBookingDate(), b.getStartTime(), b.getEndTime(),
                b.getAddressId(), b.getDistanceKm(), b.getContactName(), b.getContactPhone(),
                b.getPrice(), b.getPaymentMethod(), b.getPaymentStatus(),
                b.getStatus(), b.getRemark(), b.getMerchantRemark(), b.getCreateTime());
    }
}
