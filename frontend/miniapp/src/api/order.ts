/**
 * Product order API. Requires authentication.
 */

import { http } from './request'
import type { ApiResponse, PageResponse, PageParams } from '@/types/api'
import type { OrderItem, OrderDetail } from '@/types/product'

/** Create order from checked cart items.
 *  deliveryMethod PICKUP requires storeId; EXPRESS requires addressId.
 *  paymentMethod defaults to OFFLINE_STORE when omitted (backward compatible).
 *  WALLET deducts the order total from the user's wallet in the same transaction
 *  as stock deduction (CR-20260718-003 / D-012). */
export function createOrder(data: {
  deliveryMethod: 'PICKUP' | 'EXPRESS'
  storeId?: string
  addressId?: string
  contactName: string
  contactPhone: string
  remark?: string
  paymentMethod?: 'OFFLINE_STORE' | 'WALLET'
}): Promise<ApiResponse<OrderItem>> {
  return http.post<OrderItem>('/api/v1/product-orders', data as any)
}

/** List current user's orders */
export function getMyOrders(params?: PageParams): Promise<ApiResponse<PageResponse<OrderItem>>> {
  return http.get<PageResponse<OrderItem>>('/api/v1/product-orders/my', params as Record<string, unknown>)
}

/** Get order detail */
export function getOrderDetail(id: string): Promise<ApiResponse<OrderDetail>> {
  return http.get<OrderDetail>(`/api/v1/product-orders/${id}`)
}

/** Cancel an order */
export function cancelOrder(id: string): Promise<ApiResponse<OrderItem>> {
  return http.post<OrderItem>(`/api/v1/product-orders/${id}/cancel`)
}
