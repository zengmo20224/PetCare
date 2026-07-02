package com.petcare.booking.domain;

import com.petcare.booking.enums.BookingStatus;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for BookingStateMachine.
 * Pure logic tests — no Spring context needed.
 */
class BookingStateMachineTest {

    @Nested
    @DisplayName("Allowed transitions")
    class AllowedTransitions {

        @Test
        @DisplayName("null -> PENDING_CONFIRM is allowed (initial creation)")
        void nullToPendingConfirm() {
            assertThatCode(() -> BookingStateMachine.validateTransition(null, BookingStatus.PENDING_CONFIRM.getCode()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null -> CONFIRMED is allowed (auto-confirm on creation)")
        void nullToConfirmed() {
            assertThatCode(() -> BookingStateMachine.validateTransition(null, BookingStatus.CONFIRMED.getCode()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PENDING_CONFIRM -> CONFIRMED is allowed")
        void pendingConfirmToConfirmed() {
            assertThatCode(() -> BookingStateMachine.validateTransition(
                    BookingStatus.PENDING_CONFIRM.getCode(), BookingStatus.CONFIRMED.getCode()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PENDING_CONFIRM -> REJECTED is allowed")
        void pendingConfirmToRejected() {
            assertThatCode(() -> BookingStateMachine.validateTransition(
                    BookingStatus.PENDING_CONFIRM.getCode(), BookingStatus.REJECTED.getCode()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PENDING_CONFIRM -> CANCELLED is allowed")
        void pendingConfirmToCancelled() {
            assertThatCode(() -> BookingStateMachine.validateTransition(
                    BookingStatus.PENDING_CONFIRM.getCode(), BookingStatus.CANCELLED.getCode()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("CONFIRMED -> IN_SERVICE is allowed")
        void confirmedToInService() {
            assertThatCode(() -> BookingStateMachine.validateTransition(
                    BookingStatus.CONFIRMED.getCode(), BookingStatus.IN_SERVICE.getCode()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("CONFIRMED -> CANCELLED is allowed")
        void confirmedToCancelled() {
            assertThatCode(() -> BookingStateMachine.validateTransition(
                    BookingStatus.CONFIRMED.getCode(), BookingStatus.CANCELLED.getCode()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("IN_SERVICE -> COMPLETED is allowed")
        void inServiceToCompleted() {
            assertThatCode(() -> BookingStateMachine.validateTransition(
                    BookingStatus.IN_SERVICE.getCode(), BookingStatus.COMPLETED.getCode()))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Forbidden transitions")
    class ForbiddenTransitions {

        @ParameterizedTest(name = "{0} -> {1} is forbidden after terminal state")
        @CsvSource({
                "COMPLETED, CONFIRMED",
                "COMPLETED, CANCELLED",
                "COMPLETED, IN_SERVICE",
                "CANCELLED, CONFIRMED",
                "CANCELLED, IN_SERVICE",
                "CANCELLED, COMPLETED",
                "REJECTED, CONFIRMED",
                "REJECTED, IN_SERVICE",
                "REJECTED, COMPLETED"
        })
        void terminalStateCannotTransition(String from, String to) {
            assertThatThrownBy(() -> BookingStateMachine.validateTransition(from, to))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.BOOKING_STATUS_INVALID);
        }

        @Test
        @DisplayName("IN_SERVICE -> REJECTED is forbidden")
        void inServiceToRejectedForbidden() {
            assertThatThrownBy(() -> BookingStateMachine.validateTransition(
                    BookingStatus.IN_SERVICE.getCode(), BookingStatus.REJECTED.getCode()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.BOOKING_STATUS_INVALID);
        }

        @Test
        @DisplayName("PENDING_CONFIRM -> IN_SERVICE is forbidden (must confirm first)")
        void pendingConfirmToInServiceForbidden() {
            assertThatThrownBy(() -> BookingStateMachine.validateTransition(
                    BookingStatus.PENDING_CONFIRM.getCode(), BookingStatus.IN_SERVICE.getCode()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.BOOKING_STATUS_INVALID);
        }

        @Test
        @DisplayName("PENDING_CONFIRM -> COMPLETED is forbidden")
        void pendingConfirmToCompletedForbidden() {
            assertThatThrownBy(() -> BookingStateMachine.validateTransition(
                    BookingStatus.PENDING_CONFIRM.getCode(), BookingStatus.COMPLETED.getCode()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.BOOKING_STATUS_INVALID);
        }

        @Test
        @DisplayName("Invalid status string throws BOOKING_STATUS_INVALID")
        void invalidStatusStringThrows() {
            assertThatThrownBy(() -> BookingStateMachine.validateTransition("INVALID_STATUS", "CONFIRMED"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.BOOKING_STATUS_INVALID);
        }

        @Test
        @DisplayName("CONFIRMED -> REJECTED is forbidden")
        void confirmedToRejectedForbidden() {
            assertThatThrownBy(() -> BookingStateMachine.validateTransition(
                    BookingStatus.CONFIRMED.getCode(), BookingStatus.REJECTED.getCode()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.BOOKING_STATUS_INVALID);
        }
    }

    @Nested
    @DisplayName("Terminal status check")
    class TerminalStatus {

        @Test
        @DisplayName("COMPLETED is terminal")
        void completedIsTerminal() {
            assertThat(BookingStateMachine.isTerminalStatus(BookingStatus.COMPLETED.getCode())).isTrue();
        }

        @Test
        @DisplayName("CANCELLED is terminal")
        void cancelledIsTerminal() {
            assertThat(BookingStateMachine.isTerminalStatus(BookingStatus.CANCELLED.getCode())).isTrue();
        }

        @Test
        @DisplayName("REJECTED is terminal")
        void rejectedIsTerminal() {
            assertThat(BookingStateMachine.isTerminalStatus(BookingStatus.REJECTED.getCode())).isTrue();
        }

        @Test
        @DisplayName("PENDING_CONFIRM is NOT terminal")
        void pendingConfirmIsNotTerminal() {
            assertThat(BookingStateMachine.isTerminalStatus(BookingStatus.PENDING_CONFIRM.getCode())).isFalse();
        }

        @Test
        @DisplayName("CONFIRMED is NOT terminal")
        void confirmedIsNotTerminal() {
            assertThat(BookingStateMachine.isTerminalStatus(BookingStatus.CONFIRMED.getCode())).isFalse();
        }

        @Test
        @DisplayName("null is NOT terminal")
        void nullIsNotTerminal() {
            assertThat(BookingStateMachine.isTerminalStatus(null)).isFalse();
        }
    }
}
