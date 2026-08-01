/**
 * Status Contract Regression Tests (10F-R1)
 *
 * These tests verify that frontend status constants match the backend
 * enum values EXACTLY. They also test action guard pure functions
 * that determine which operations are available for each status.
 *
 * Backend source of truth:
 *   - BookingStatus:  com.petcare.booking.enums.BookingStatus
 *   - PaymentStatus:  com.petcare.booking.enums.PaymentStatus
 *   - ServiceItemStatus: com.petcare.common.enums.ServiceItemStatus
 *   - StaffStatus:    com.petcare.common.enums.StaffStatus
 *   - ProductOrderStatus: com.petcare.product.enums.ProductOrderStatus
 *   - PickupStatus:   com.petcare.product.enums.PickupStatus
 *   - StoreStatus:    com.petcare.common.enums.StoreStatus
 *   - ScheduleStatus: com.petcare.booking.enums.ScheduleStatus
 *   - ContentStatus:  com.petcare.community.enums.ContentStatus
 *
 * RED phase: These tests MUST fail with the current frontend code,
 * proving that the wrong values (PENDING, IN_PROGRESS, ACTIVE, etc.)
 * cause the contract assertions to fail.
 */

import { describe, it, expect } from 'vitest'
import {
  BOOKING_STATUS,
  PAYMENT_STATUS,
  SERVICE_STATUS,
  STAFF_STATUS,
  PRODUCT_STATUS,
  PRODUCT_ORDER_STATUS,
  PICKUP_STATUS,
  STORE_STATUS,
  SCHEDULE_STATUS,
  POST_STATUS,
  REPORT_HANDLE_RESULT,
  getBookingActions,
  getProductOrderActions,
  isServiceOnSale,
  isProductOnSale,
  canDisableStaff,
} from '../types/status'

// ─── Booking Status Contract ───

describe('BookingStatus contract', () => {
  it('must contain PENDING_CONFIRM (not PENDING)', () => {
    expect(BOOKING_STATUS).toHaveProperty('PENDING_CONFIRM')
  })

  it('must NOT contain wrong key PENDING', () => {
    expect(BOOKING_STATUS).not.toHaveProperty('PENDING')
  })

  it('must contain CONFIRMED', () => {
    expect(BOOKING_STATUS).toHaveProperty('CONFIRMED')
  })

  it('must contain IN_SERVICE (not IN_PROGRESS)', () => {
    expect(BOOKING_STATUS).toHaveProperty('IN_SERVICE')
  })

  it('must NOT contain wrong key IN_PROGRESS', () => {
    expect(BOOKING_STATUS).not.toHaveProperty('IN_PROGRESS')
  })

  it('must contain COMPLETED, CANCELLED, REJECTED', () => {
    expect(BOOKING_STATUS).toHaveProperty('COMPLETED')
    expect(BOOKING_STATUS).toHaveProperty('CANCELLED')
    expect(BOOKING_STATUS).toHaveProperty('REJECTED')
  })

  it('has exactly 6 status values', () => {
    expect(Object.keys(BOOKING_STATUS)).toHaveLength(6)
  })
})

// ─── Payment Status Contract ───

describe('PaymentStatus contract', () => {
  it('must contain UNPAID (not PENDING)', () => {
    expect(PAYMENT_STATUS).toHaveProperty('UNPAID')
  })

  it('must NOT contain wrong key PENDING', () => {
    expect(PAYMENT_STATUS).not.toHaveProperty('PENDING')
  })

  it('must contain OFFLINE_PAID (not PAID)', () => {
    expect(PAYMENT_STATUS).toHaveProperty('OFFLINE_PAID')
  })

  it('must NOT contain wrong key PAID', () => {
    expect(PAYMENT_STATUS).not.toHaveProperty('PAID')
  })

  it('must contain REFUNDED', () => {
    expect(PAYMENT_STATUS).toHaveProperty('REFUNDED')
  })

  it('has exactly 3 status values', () => {
    expect(Object.keys(PAYMENT_STATUS)).toHaveLength(3)
  })
})

// ─── Service Item Status Contract ───

describe('ServiceStatus contract', () => {
  it('must contain ON_SALE (not ACTIVE)', () => {
    expect(SERVICE_STATUS).toHaveProperty('ON_SALE')
  })

  it('must NOT contain wrong key ACTIVE', () => {
    expect(SERVICE_STATUS).not.toHaveProperty('ACTIVE')
  })

  it('must contain OFF_SALE (not DISABLED)', () => {
    expect(SERVICE_STATUS).toHaveProperty('OFF_SALE')
  })

  it('must NOT contain wrong key DISABLED', () => {
    expect(SERVICE_STATUS).not.toHaveProperty('DISABLED')
  })

  it('has exactly 2 status values', () => {
    expect(Object.keys(SERVICE_STATUS)).toHaveLength(2)
  })
})

// ─── Staff Status Contract ───

describe('StaffStatus contract', () => {
  it('must contain ACTIVE', () => {
    expect(STAFF_STATUS).toHaveProperty('ACTIVE')
  })

  it('must contain INACTIVE (not DISABLED)', () => {
    expect(STAFF_STATUS).toHaveProperty('INACTIVE')
  })

  it('must NOT contain wrong key DISABLED', () => {
    expect(STAFF_STATUS).not.toHaveProperty('DISABLED')
  })

  it('has exactly 2 status values', () => {
    expect(Object.keys(STAFF_STATUS)).toHaveLength(2)
  })
})

// ─── Product Status Contract ───

describe('ProductStatus contract', () => {
  it('must contain ON_SALE (not ACTIVE)', () => {
    expect(PRODUCT_STATUS).toHaveProperty('ON_SALE')
  })

  it('must NOT contain wrong key ACTIVE', () => {
    expect(PRODUCT_STATUS).not.toHaveProperty('ACTIVE')
  })

  it('must contain OFF_SALE (not DISABLED)', () => {
    expect(PRODUCT_STATUS).toHaveProperty('OFF_SALE')
  })

  it('must NOT contain wrong key DISABLED', () => {
    expect(PRODUCT_STATUS).not.toHaveProperty('DISABLED')
  })

  it('has exactly 2 status values', () => {
    expect(Object.keys(PRODUCT_STATUS)).toHaveLength(2)
  })
})

// ─── Product Order Status Contract ───

describe('ProductOrderStatus contract', () => {
  it('must contain PENDING_CONFIRM (not PENDING)', () => {
    expect(PRODUCT_ORDER_STATUS).toHaveProperty('PENDING_CONFIRM')
  })

  it('must NOT contain wrong key PENDING', () => {
    expect(PRODUCT_ORDER_STATUS).not.toHaveProperty('PENDING')
  })

  it('must contain PREPARING (not CONFIRMED)', () => {
    expect(PRODUCT_ORDER_STATUS).toHaveProperty('PREPARING')
  })

  it('must NOT contain wrong key CONFIRMED', () => {
    expect(PRODUCT_ORDER_STATUS).not.toHaveProperty('CONFIRMED')
  })

  it('must contain READY_FOR_PICKUP (not READY)', () => {
    expect(PRODUCT_ORDER_STATUS).toHaveProperty('READY_FOR_PICKUP')
  })

  it('must NOT contain wrong key READY', () => {
    expect(PRODUCT_ORDER_STATUS).not.toHaveProperty('READY')
  })

  it('must contain COMPLETED, CANCELLED', () => {
    expect(PRODUCT_ORDER_STATUS).toHaveProperty('COMPLETED')
    expect(PRODUCT_ORDER_STATUS).toHaveProperty('CANCELLED')
  })

  it('must contain OUT_OF_STOCK', () => {
    expect(PRODUCT_ORDER_STATUS).toHaveProperty('OUT_OF_STOCK')
  })

  it('has exactly 6 status values', () => {
    expect(Object.keys(PRODUCT_ORDER_STATUS)).toHaveLength(6)
  })
})

// ─── Pickup Status Contract ───

describe('PickupStatus contract', () => {
  it('must contain WAIT_PREPARE (not PENDING)', () => {
    expect(PICKUP_STATUS).toHaveProperty('WAIT_PREPARE')
  })

  it('must NOT contain wrong key PENDING', () => {
    expect(PICKUP_STATUS).not.toHaveProperty('PENDING')
  })

  it('must contain READY_FOR_PICKUP', () => {
    expect(PICKUP_STATUS).toHaveProperty('READY_FOR_PICKUP')
  })

  it('must contain PICKED_UP', () => {
    expect(PICKUP_STATUS).toHaveProperty('PICKED_UP')
  })

  it('has exactly 3 status values', () => {
    expect(Object.keys(PICKUP_STATUS)).toHaveLength(3)
  })
})

// ─── Booking Action Guards ───
// Backend state machine: BookingStateMachine
// PENDING_CONFIRM → CONFIRMED, REJECTED, CANCELLED
// CONFIRMED → IN_SERVICE, CANCELLED
// IN_SERVICE → COMPLETED
// COMPLETED/CANCELLED/REJECTED → terminal
// 注：前端把"拒绝"与"取消"合并为单一"取消"入口（cancel=reject/作废），统一走
// cancel 接口。后端状态机仍允许 PENDING_CONFIRM→REJECTED，但前端不再暴露 reject 动作。

describe('getBookingActions', () => {
  it('PENDING_CONFIRM allows confirm, cancel (reject merged into cancel)', () => {
    const actions = getBookingActions('PENDING_CONFIRM')
    expect(actions).toContain('confirm')
    expect(actions).toContain('cancel')
    // reject 已合并进 cancel，不再作为独立前端动作
    expect(actions).not.toContain('reject')
    expect(actions).toHaveLength(2)
  })

  it('CONFIRMED allows start, cancel', () => {
    const actions = getBookingActions('CONFIRMED')
    expect(actions).toContain('start')
    expect(actions).toContain('cancel')
    expect(actions).toHaveLength(2)
  })

  it('IN_SERVICE allows complete', () => {
    const actions = getBookingActions('IN_SERVICE')
    expect(actions).toContain('complete')
    expect(actions).toHaveLength(1)
  })

  it('COMPLETED has no actions', () => {
    expect(getBookingActions('COMPLETED')).toEqual([])
  })

  it('CANCELLED has no actions', () => {
    expect(getBookingActions('CANCELLED')).toEqual([])
  })

  it('REJECTED has no actions', () => {
    expect(getBookingActions('REJECTED')).toEqual([])
  })

  it('unknown status returns empty (safe fallback)', () => {
    expect(getBookingActions('UNKNOWN')).toEqual([])
  })
})

// ─── Product Order Action Guards ───
// Backend state machine: ProductOrderStateMachine
// PENDING_CONFIRM → PREPARING, CANCELLED, OUT_OF_STOCK
// PREPARING → READY_FOR_PICKUP, CANCELLED
// READY_FOR_PICKUP → CANCELLED, COMPLETED
// COMPLETED/CANCELLED/OUT_OF_STOCK → terminal
// Backend completeOrder requires READY_FOR_PICKUP + paymentStatus=OFFLINE_PAID + pickupStatus=PICKED_UP

describe('getProductOrderActions', () => {
  it('PENDING_CONFIRM allows confirm, cancel, out-of-stock', () => {
    const actions = getProductOrderActions('PENDING_CONFIRM')
    expect(actions).toContain('confirm')
    expect(actions).toContain('cancel')
    expect(actions).toContain('out-of-stock')
    expect(actions).toHaveLength(3)
  })

  it('PREPARING allows ready, cancel', () => {
    const actions = getProductOrderActions('PREPARING')
    expect(actions).toContain('ready')
    expect(actions).toContain('cancel')
    expect(actions).toHaveLength(2)
  })

  it('READY_FOR_PICKUP with unknown payment shows confirm-payment and cancel only', () => {
    const actions = getProductOrderActions('READY_FOR_PICKUP')
    expect(actions).toContain('confirm-payment')
    expect(actions).toContain('cancel')
    expect(actions).not.toContain('complete')
  })

  it('READY_FOR_PICKUP unpaid allows confirm-payment and cancel', () => {
    const actions = getProductOrderActions({ status: 'READY_FOR_PICKUP', paymentStatus: 'UNPAID' })
    expect(actions).toContain('confirm-payment')
    expect(actions).toContain('cancel')
    expect(actions).not.toContain('complete')
  })

  it('READY_FOR_PICKUP paid but not picked up has no actions', () => {
    const actions = getProductOrderActions({ status: 'READY_FOR_PICKUP', paymentStatus: 'OFFLINE_PAID', pickupStatus: 'READY_FOR_PICKUP' })
    expect(actions).toEqual([])
  })

  it('READY_FOR_PICKUP paid and picked up allows complete', () => {
    const actions = getProductOrderActions({ status: 'READY_FOR_PICKUP', paymentStatus: 'OFFLINE_PAID', pickupStatus: 'PICKED_UP' })
    expect(actions).toContain('complete')
    expect(actions).not.toContain('confirm-payment')
    expect(actions).not.toContain('cancel')
    expect(actions).toHaveLength(1)
  })

  it('COMPLETED has no actions', () => {
    expect(getProductOrderActions('COMPLETED')).toEqual([])
  })

  it('CANCELLED has no actions', () => {
    expect(getProductOrderActions('CANCELLED')).toEqual([])
  })

  it('OUT_OF_STOCK has no actions', () => {
    expect(getProductOrderActions('OUT_OF_STOCK')).toEqual([])
  })

  it('unknown status returns empty (safe fallback)', () => {
    expect(getProductOrderActions('UNKNOWN')).toEqual([])
  })
})

// ─── Service/Product On-Sale Helpers ───

describe('isServiceOnSale', () => {
  it('returns true for ON_SALE', () => {
    expect(isServiceOnSale('ON_SALE')).toBe(true)
  })

  it('returns false for OFF_SALE', () => {
    expect(isServiceOnSale('OFF_SALE')).toBe(false)
  })

  it('returns false for wrong value ACTIVE', () => {
    expect(isServiceOnSale('ACTIVE')).toBe(false)
  })

  it('returns false for unknown values', () => {
    expect(isServiceOnSale('UNKNOWN')).toBe(false)
  })
})

describe('isProductOnSale', () => {
  it('returns true for ON_SALE', () => {
    expect(isProductOnSale('ON_SALE')).toBe(true)
  })

  it('returns false for OFF_SALE', () => {
    expect(isProductOnSale('OFF_SALE')).toBe(false)
  })

  it('returns false for wrong value ACTIVE', () => {
    expect(isProductOnSale('ACTIVE')).toBe(false)
  })

  it('returns false for unknown values', () => {
    expect(isProductOnSale('UNKNOWN')).toBe(false)
  })
})

// ─── Staff Disable Helper ───

describe('canDisableStaff', () => {
  it('returns true for ACTIVE', () => {
    expect(canDisableStaff('ACTIVE')).toBe(true)
  })

  it('returns false for INACTIVE', () => {
    expect(canDisableStaff('INACTIVE')).toBe(false)
  })

  it('returns false for wrong value DISABLED', () => {
    expect(canDisableStaff('DISABLED')).toBe(false)
  })

  it('returns false for unknown values', () => {
    expect(canDisableStaff('UNKNOWN')).toBe(false)
  })
})

// ─── Stable Statuses (should not change) ───

describe('StoreStatus contract (unchanged)', () => {
  it('has OPEN and CLOSED', () => {
    expect(STORE_STATUS).toHaveProperty('OPEN')
    expect(STORE_STATUS).toHaveProperty('CLOSED')
    expect(Object.keys(STORE_STATUS)).toHaveLength(2)
  })
})

describe('ScheduleStatus contract (unchanged)', () => {
  it('has AVAILABLE and UNAVAILABLE', () => {
    expect(SCHEDULE_STATUS).toHaveProperty('AVAILABLE')
    expect(SCHEDULE_STATUS).toHaveProperty('UNAVAILABLE')
    expect(Object.keys(SCHEDULE_STATUS)).toHaveLength(2)
  })
})

describe('PostStatus contract (unchanged)', () => {
  it('has PENDING_REVIEW, PUBLISHED, REJECTED, HIDDEN, DELETED', () => {
    expect(POST_STATUS).toHaveProperty('PENDING_REVIEW')
    expect(POST_STATUS).toHaveProperty('PUBLISHED')
    expect(POST_STATUS).toHaveProperty('REJECTED')
    expect(POST_STATUS).toHaveProperty('HIDDEN')
    expect(POST_STATUS).toHaveProperty('DELETED')
  })

  it('must NOT have DRAFT (backend ContentStatus does not have DRAFT)', () => {
    expect(POST_STATUS).not.toHaveProperty('DRAFT')
  })
})

// ─── Status Filter Values ───
// When pages send status filter to backend, values must be real backend values

describe('Status filter values match backend', () => {
  it('booking status filter keys are valid backend values', () => {
    const validBookingStatuses = ['PENDING_CONFIRM', 'CONFIRMED', 'IN_SERVICE', 'COMPLETED', 'CANCELLED', 'REJECTED']
    Object.keys(BOOKING_STATUS).forEach((key) => {
      expect(validBookingStatuses).toContain(key)
    })
  })

  it('payment status filter keys are valid backend values', () => {
    const validPaymentStatuses = ['UNPAID', 'OFFLINE_PAID', 'REFUNDED']
    Object.keys(PAYMENT_STATUS).forEach((key) => {
      expect(validPaymentStatuses).toContain(key)
    })
  })

  it('product order status filter keys are valid backend values', () => {
    const validOrderStatuses = ['PENDING_CONFIRM', 'PREPARING', 'READY_FOR_PICKUP', 'COMPLETED', 'CANCELLED', 'OUT_OF_STOCK']
    Object.keys(PRODUCT_ORDER_STATUS).forEach((key) => {
      expect(validOrderStatuses).toContain(key)
    })
  })
})

// ─── Report Status Contract ───
// Backend: PostReport.status = PENDING | PROCESSED | IGNORED
// Backend: AdminReportHandleRequest.handleResult = PROCESSED | IGNORED

describe('Report status contract', () => {
  it('REPORT_HANDLE_RESULT must contain PROCESSED and IGNORED (not HANDLED)', () => {
    expect(REPORT_HANDLE_RESULT).toHaveProperty('PROCESSED')
    expect(REPORT_HANDLE_RESULT).toHaveProperty('IGNORED')
    expect(REPORT_HANDLE_RESULT).not.toHaveProperty('HANDLED')
  })
})
