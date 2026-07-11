/**
 * Centralized status dictionaries for display labels, colors, and allowed actions.
 * These values MUST match backend enum constants exactly.
 *
 * Backend source of truth:
 *   - BookingStatus:     com.petcare.booking.enums.BookingStatus
 *   - PaymentStatus:     com.petcare.booking.enums.PaymentStatus
 *   - ServiceItemStatus: com.petcare.common.enums.ServiceItemStatus
 *   - StaffStatus:       com.petcare.common.enums.StaffStatus
 *   - StoreStatus:       com.petcare.common.enums.StoreStatus
 *   - ScheduleStatus:    com.petcare.booking.enums.ScheduleStatus
 *   - ProductOrderStatus: com.petcare.product.enums.ProductOrderStatus
 *   - PickupStatus:      com.petcare.product.enums.PickupStatus
 *   - ContentStatus:     com.petcare.community.enums.ContentStatus
 *
 * Frontend only controls display; backend controls actual state transitions.
 */

// ─── Store Status ───

export const STORE_STATUS = {
  OPEN: { label: '营业中', color: 'success' },
  CLOSED: { label: '已休息', color: 'info' },
} as const

export type StoreStatus = keyof typeof STORE_STATUS

// ─── Service Item ───

export const SERVICE_MODE = {
  STORE: { label: '到店', color: 'success' },
  HOME: { label: '上门', color: 'warning' },
  BOTH: { label: '到店/上门', color: 'primary' },
} as const

export type ServiceMode = keyof typeof SERVICE_MODE

export const SERVICE_STATUS = {
  ON_SALE: { label: '启用', color: 'success' },
  OFF_SALE: { label: '已禁用', color: 'info' },
} as const

export type ServiceStatus = keyof typeof SERVICE_STATUS

export const PET_TYPE = {
  DOG: '犬类',
  CAT: '猫类',
  ALL: '不限',
} as const

export type PetType = keyof typeof PET_TYPE

export const PET_SIZE = {
  SMALL: '小型',
  MEDIUM: '中型',
  LARGE: '大型',
  ALL: '不限',
} as const

export type PetSize = keyof typeof PET_SIZE

// ─── Staff ───

export const STAFF_ROLE = {
  GROOMER: { label: '美容师', color: 'primary' },
  WALKER: { label: '遛狗员', color: 'success' },
  FEEDER: { label: '喂养员', color: 'warning' },
  MANAGER: { label: '店长', color: 'danger' },
} as const

export type StaffRole = keyof typeof STAFF_ROLE

export const STAFF_STATUS = {
  ACTIVE: { label: '在职', color: 'success' },
  INACTIVE: { label: '已停用', color: 'info' },
} as const

export type StaffStatus = keyof typeof STAFF_STATUS

// ─── App User ───

export const USER_STATUS = {
  ACTIVE: { label: '正常', color: 'success' },
  BANNED: { label: '已封禁', color: 'danger' },
} as const

export type UserStatus = keyof typeof USER_STATUS

export const SCHEDULE_STATUS = {
  AVAILABLE: { label: '可用', color: 'success' },
  UNAVAILABLE: { label: '不可用', color: 'danger' },
} as const

export type ScheduleStatus = keyof typeof SCHEDULE_STATUS

// ─── Booking ───
// Backend: BookingStateMachine transitions:
//   null → PENDING_CONFIRM | CONFIRMED（新预约默认直接 CONFIRMED，自动确认）
//   PENDING_CONFIRM → CONFIRMED, REJECTED, CANCELLED（历史/手动确认场景保留）
//   CONFIRMED → IN_SERVICE, CANCELLED
//   IN_SERVICE → COMPLETED
//   COMPLETED / CANCELLED / REJECTED → terminal

export const BOOKING_STATUS = {
  PENDING_CONFIRM: { label: '待确认', color: 'warning' },
  CONFIRMED: { label: '已确认', color: 'primary' },
  IN_SERVICE: { label: '进行中', color: 'success' },
  COMPLETED: { label: '已完成', color: 'info' },
  CANCELLED: { label: '已取消', color: 'danger' },
  REJECTED: { label: '已拒绝', color: 'danger' },
} as const

export type BookingStatus = keyof typeof BOOKING_STATUS

// Backend: PaymentStatus enum
export const PAYMENT_STATUS = {
  UNPAID: { label: '待支付', color: 'warning' },
  OFFLINE_PAID: { label: '已支付', color: 'success' },
  REFUNDED: { label: '已退款', color: 'info' },
} as const

export type PaymentStatus = keyof typeof PAYMENT_STATUS

// ─── Product ───

export const PRODUCT_STATUS = {
  ON_SALE: { label: '上架', color: 'success' },
  OFF_SALE: { label: '已下架', color: 'info' },
} as const

export type ProductStatus = keyof typeof PRODUCT_STATUS

// Backend: ProductOrderStateMachine transitions:
//   PENDING_CONFIRM → PREPARING, CANCELLED, OUT_OF_STOCK
//   PREPARING → READY_FOR_PICKUP, CANCELLED
//   READY_FOR_PICKUP → CANCELLED, COMPLETED
//   COMPLETED / CANCELLED / OUT_OF_STOCK → terminal
export const PRODUCT_ORDER_STATUS = {
  PENDING_CONFIRM: { label: '待确认', color: 'warning' },
  PREPARING: { label: '备货中', color: 'primary' },
  READY_FOR_PICKUP: { label: '待自提', color: 'success' },
  COMPLETED: { label: '已完成', color: 'info' },
  CANCELLED: { label: '已取消', color: 'danger' },
  OUT_OF_STOCK: { label: '已缺货', color: 'warning' },
} as const

export type ProductOrderStatus = keyof typeof PRODUCT_ORDER_STATUS

export const PICKUP_STATUS = {
  WAIT_PREPARE: { label: '待备货', color: 'warning' },
  READY_FOR_PICKUP: { label: '待自提', color: 'primary' },
  PICKED_UP: { label: '已自提', color: 'success' },
} as const

export type PickupStatus = keyof typeof PICKUP_STATUS

// ─── Community ───
// Backend: ContentStatus enum (no DRAFT in backend)

export const POST_STATUS = {
  PENDING_REVIEW: { label: '待审核', color: 'warning' },
  PUBLISHED: { label: '已发布', color: 'success' },
  REJECTED: { label: '已拒绝', color: 'danger' },
  HIDDEN: { label: '已隐藏', color: 'info' },
  DELETED: { label: '已删除', color: 'danger' },
} as const

export type PostStatus = keyof typeof POST_STATUS

export const REPORT_HANDLE_RESULT = {
  PROCESSED: { label: '已处理', color: 'success' },
  IGNORED: { label: '已忽略', color: 'info' },
} as const

export type ReportHandleResult = keyof typeof REPORT_HANDLE_RESULT

export const REPORT_STATUS = {
  PENDING: { label: '待处理', color: 'warning' },
  PROCESSED: { label: '已处理', color: 'success' },
  IGNORED: { label: '已忽略', color: 'info' },
} as const

export type ReportStatus = keyof typeof REPORT_STATUS

// ─── Sensitive Word ───

export const SENSITIVE_WORD_STATUS = {
  ACTIVE: { label: '启用', color: 'success' },
  DISABLED: { label: '已禁用', color: 'info' },
} as const

export type SensitiveWordStatus = keyof typeof SENSITIVE_WORD_STATUS

// ─── Action Guard Functions ───
// Pure functions that determine which operations are available for a given status.
// These must match the backend state machines exactly.
// Unknown status returns empty array (safe: no write operations enabled).

/**
 * Returns available booking actions for the given status.
 * Backend: BookingStateMachine
 */
export function getBookingActions(status: string): string[] {
  const actions: Record<string, string[]> = {
    PENDING_CONFIRM: ['confirm', 'reject', 'cancel'],
    CONFIRMED: ['start', 'cancel'],
    IN_SERVICE: ['complete'],
    COMPLETED: [],
    CANCELLED: [],
    REJECTED: [],
  }
  return actions[status] ?? []
}

/**
 * Context for determining product order actions.
 * Backend completeOrder requires READY_FOR_PICKUP + OFFLINE_PAID + PICKED_UP.
 */
export interface OrderActionContext {
  status: string
  paymentStatus?: string
  pickupStatus?: string
}

/**
 * Returns available product order actions for the given order context.
 * Backend: ProductOrderStateMachine
 * - complete requires READY_FOR_PICKUP + paymentStatus=OFFLINE_PAID + pickupStatus=PICKED_UP
 * - cancel blocked when already paid or picked up
 * - out-of-stock available from PENDING_CONFIRM
 */
export function getProductOrderActions(statusOrOrder: string | OrderActionContext): string[] {
  const status = typeof statusOrOrder === 'string' ? statusOrOrder : statusOrOrder.status
  const paymentStatus = typeof statusOrOrder === 'string' ? undefined : statusOrOrder.paymentStatus
  const pickupStatus = typeof statusOrOrder === 'string' ? undefined : statusOrOrder.pickupStatus

  // READY_FOR_PICKUP has conditional actions based on payment and pickup
  if (status === 'READY_FOR_PICKUP') {
    const result: string[] = []
    if (paymentStatus !== 'OFFLINE_PAID') {
      result.push('confirm-payment')
      result.push('cancel')
    }
    if (paymentStatus === 'OFFLINE_PAID' && pickupStatus === 'PICKED_UP') {
      result.push('complete')
    }
    return result
  }

  const actions: Record<string, string[]> = {
    PENDING_CONFIRM: ['confirm', 'cancel', 'out-of-stock'],
    PREPARING: ['ready', 'cancel'],
    COMPLETED: [],
    CANCELLED: [],
    OUT_OF_STOCK: [],
  }
  return actions[status] ?? []
}

/**
 * Returns true if the service is currently on sale (ON_SALE).
 * Backend: ServiceItemStatus.ON_SALE
 */
export function isServiceOnSale(status: string): boolean {
  return status === 'ON_SALE'
}

/**
 * Returns true if the product is currently on sale (ON_SALE).
 * Backend: Product status uses ON_SALE / OFF_SALE (schema.sql)
 */
export function isProductOnSale(status: string): boolean {
  return status === 'ON_SALE'
}

/**
 * Returns true if the staff member can be disabled (currently ACTIVE).
 * Backend: StaffStatus.ACTIVE → INACTIVE
 */
export function canDisableStaff(status: string): boolean {
  return status === 'ACTIVE'
}

/**
 * Returns true if the staff member can be enabled (currently INACTIVE).
 * Backend: StaffStatus.INACTIVE → ACTIVE
 */
export function canEnableStaff(status: string): boolean {
  return status === 'INACTIVE'
}

/**
 * Returns true if the user can be banned (currently ACTIVE).
 * Backend: User status ACTIVE → BANNED
 */
export function canBanUser(status: string): boolean {
  return status === 'ACTIVE'
}

/**
 * Returns true if the user can be unbanned (currently BANNED).
 */
export function canUnbanUser(status: string): boolean {
  return status === 'BANNED'
}

// ─── File Upload Limits ───

/** Maximum file upload size: 10 MB */
export const MAX_UPLOAD_SIZE = 10 * 1024 * 1024

/** Maximum photos per service/product detail gallery. */
export const CATALOG_DETAIL_IMAGE_LIMIT = 20

/** Maximum photos per product detail page top carousel. */
export const PRODUCT_DETAIL_CAROUSEL_LIMIT = 5
