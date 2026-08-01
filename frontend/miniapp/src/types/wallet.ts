/**
 * Wallet domain types (CR-20260718-003).
 * Mirrors backend WalletDtos records. IDs are string because the backend serializes
 * snowflake IDs as strings to preserve precision in JSON.
 */

/** User wallet summary. */
export interface WalletInfo {
  id: string
  userId: string
  balance: number
  frozenAmount: number
  updateTime?: string
}

/** Append-only ledger row. */
export interface WalletTransaction {
  id: string
  /** 'DEBIT' (outflow) or 'CREDIT' (inflow). */
  direction: 'DEBIT' | 'CREDIT'
  /** 'RECHARGE' | 'PAY' | 'REFUND' | 'ADMIN_ADJUST' | 'BONUS'. */
  sourceType: string
  amount: number
  balanceBefore: number
  balanceAfter: number
  /** 'PRODUCT_ORDER' | 'SERVICE_BOOKING' | null. */
  relatedOrderType?: string | null
  relatedOrderId?: string | null
  /** 'USER' | 'ADMIN' | 'SYSTEM'. */
  operatorType: string
  operatorId?: string | null
  reason?: string | null
  createTime: string
}

/** Human-readable labels for source types (for UI display). */
export const WALLET_SOURCE_LABELS: Record<string, string> = {
  RECHARGE: '充值',
  PAY: '消费',
  REFUND: '退款',
  ADMIN_ADJUST: '管理员调整',
  BONUS: '赠送',
}

/** Human-readable labels for directions. */
export const WALLET_DIRECTION_LABELS: Record<string, string> = {
  DEBIT: '支出',
  CREDIT: '收入',
}
