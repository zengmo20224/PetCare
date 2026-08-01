/**
 * Wallet admin API (CR-20260718-003).
 * BaseURL is /api (configured in utils/request.ts); paths below start with /v1/admin/wallet.
 */
import request from '../utils/request'
import type { PageResponse, PageParams } from '../types/api'

// ─── Types ───

export interface WalletAccount {
  walletId: string
  userId: string
  userNickname: string | null
  userPhone: string | null
  balance: number
  frozenAmount: number
  version: number
  createTime: string
  updateTime: string
}

export interface WalletTransaction {
  id: string
  /** User whose wallet this transaction belongs to. Required for admin audit ("who was paid"). */
  userId: string
  /** User's display nickname (batch-joined by backend for the admin list). */
  userNickname: string | null
  /** User's phone (batch-joined by backend for the admin list). */
  userPhone: string | null
  direction: 'DEBIT' | 'CREDIT'
  sourceType: string
  amount: number
  balanceBefore: number
  balanceAfter: number
  relatedOrderType: string | null
  relatedOrderId: string | null
  operatorType: 'USER' | 'ADMIN' | 'SYSTEM'
  operatorId: string | null
  reason: string | null
  createTime: string
}

export interface WalletAccountQueryParams extends PageParams {
  phone?: string
  /** Optional user ID fragment (matched as a string LIKE against the snowflake ID). */
  userId?: string
}

export interface WalletTransactionQueryParams extends PageParams {
  userId?: string
  sourceType?: string
  direction?: string
}

export interface WalletRechargeParams {
  amount: number
  reason: string
}

export interface WalletAdjustParams {
  amount: number
  /** 'CREDIT' (increase) or 'DEBIT' (decrease). */
  direction: 'CREDIT' | 'DEBIT'
  reason: string
}

// ─── Display dictionaries ───

export const WALLET_SOURCE_LABELS: Record<string, string> = {
  RECHARGE: '充值',
  PAY: '消费',
  REFUND: '退款',
  ADMIN_ADJUST: '管理员调整',
  BONUS: '赠送',
}

export const WALLET_DIRECTION_LABELS: Record<string, string> = {
  DEBIT: '支出',
  CREDIT: '收入',
}

// ─── API Functions ───

export const getWalletAccounts = (params: WalletAccountQueryParams) =>
  request.get<PageResponse<WalletAccount>>('/v1/admin/wallet/accounts', { params })

export const getWalletTransactions = (params: WalletTransactionQueryParams) =>
  request.get<PageResponse<WalletTransaction>>('/v1/admin/wallet/transactions', { params })

export const rechargeWallet = (userId: string, data: WalletRechargeParams) =>
  request.post<WalletAccount>(`/v1/admin/wallet/accounts/${userId}/recharge`, data)

export const adjustWallet = (userId: string, data: WalletAdjustParams) =>
  request.post<WalletAccount>(`/v1/admin/wallet/accounts/${userId}/adjust`, data)
