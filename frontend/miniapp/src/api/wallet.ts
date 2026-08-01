/**
 * Wallet API (user-facing, read-only). Requires USER authentication.
 * Recharge is admin-only; users cannot self-recharge (D-010/D-012).
 *
 * CR-20260718-003.
 */

import { http } from './request'
import type { ApiResponse, PageResponse, PageParams } from '@/types/api'
import type { WalletInfo, WalletTransaction } from '@/types/wallet'

/** Get the current user's wallet (zero-balance view if none exists yet). */
export function getMyWallet(): Promise<ApiResponse<WalletInfo>> {
  return http.get<WalletInfo>('/api/v1/user/wallet')
}

/** List the current user's transactions (newest first). */
export function getMyWalletTransactions(
  params?: PageParams
): Promise<ApiResponse<PageResponse<WalletTransaction>>> {
  return http.get<PageResponse<WalletTransaction>>(
    '/api/v1/user/wallet/transactions',
    params as Record<string, unknown>
  )
}
