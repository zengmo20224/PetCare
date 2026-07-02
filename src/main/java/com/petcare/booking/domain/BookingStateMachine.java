package com.petcare.booking.domain;

import com.petcare.booking.enums.BookingStatus;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Validates booking state transitions.
 * Encapsulates the allowed transition rules as pure logic.
 */
public final class BookingStateMachine {

    /** Map from current status to the set of allowed next statuses. */
    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<BookingStatus, Set<BookingStatus>> map = new EnumMap<>(BookingStatus.class);

        // null -> PENDING_CONFIRM (handled separately as initial creation)
        // PENDING_CONFIRM -> CONFIRMED, REJECTED, CANCELLED
        map.put(BookingStatus.PENDING_CONFIRM, Set.of(
                BookingStatus.CONFIRMED,
                BookingStatus.REJECTED,
                BookingStatus.CANCELLED));

        // CONFIRMED -> IN_SERVICE, CANCELLED
        map.put(BookingStatus.CONFIRMED, Set.of(
                BookingStatus.IN_SERVICE,
                BookingStatus.CANCELLED));

        // IN_SERVICE -> COMPLETED
        map.put(BookingStatus.IN_SERVICE, Set.of(
                BookingStatus.COMPLETED));

        // Terminal states: no transitions allowed
        map.put(BookingStatus.COMPLETED, Collections.emptySet());
        map.put(BookingStatus.CANCELLED, Collections.emptySet());
        map.put(BookingStatus.REJECTED, Collections.emptySet());

        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(map);
    }

    private BookingStateMachine() {
        // utility class
    }

    /**
     * Validates that the transition from currentStatus to targetStatus is allowed.
     *
     * @param currentStatus the current booking status (null for initial creation)
     * @param targetStatus  the desired next status
     * @throws BusinessException if the transition is not allowed
     */
    public static void validateTransition(String currentStatus, String targetStatus) {
        // Initial creation: null -> PENDING_CONFIRM 或 null -> CONFIRMED
        // 业务规则：排班正常有位置时直接创建为 CONFIRMED（自动确认），
        // 仍保留 PENDING_CONFIRM 作为合法初始状态以兼容历史/手动确认场景。
        if (currentStatus == null) {
            if (BookingStatus.PENDING_CONFIRM.getCode().equals(targetStatus)
                    || BookingStatus.CONFIRMED.getCode().equals(targetStatus)) {
                return;
            }
            throw new BusinessException(
                    ErrorCode.BOOKING_STATUS_INVALID,
                    String.format("预约状态不允许从 null 变更为 %s", targetStatus));
        }

        BookingStatus from = parseStatus(currentStatus);
        BookingStatus to = parseStatus(targetStatus);

        Set<BookingStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException(
                    ErrorCode.BOOKING_STATUS_INVALID,
                    String.format("预约状态不允许从 %s 变更为 %s", currentStatus, targetStatus));
        }
    }

    /**
     * Checks if the given status is a terminal state (no further transitions allowed).
     */
    public static boolean isTerminalStatus(String status) {
        if (status == null) return false;
        BookingStatus parsed = parseStatus(status);
        Set<BookingStatus> allowed = ALLOWED_TRANSITIONS.get(parsed);
        return allowed != null && allowed.isEmpty();
    }

    private static BookingStatus parseStatus(String status) {
        for (BookingStatus bs : BookingStatus.values()) {
            if (bs.getCode().equals(status)) {
                return bs;
            }
        }
        throw new BusinessException(
                ErrorCode.BOOKING_STATUS_INVALID,
                "未知的预约状态: " + status);
    }
}
