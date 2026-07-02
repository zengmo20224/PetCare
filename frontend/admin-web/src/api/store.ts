import request from '../utils/request'
// Types from api.ts used indirectly through request wrapper return types

// ─── Types (match backend DTOs exactly) ───

export interface StoreInfo {
  id: number
  storeName: string
  phone: string | null
  address: string | null
  longitude: number | null
  latitude: number | null
  businessHours: string | null
  status: string // OPEN | CLOSED
  description: string | null
}

// storeName and status are required (backend @NotBlank)
export interface StoreUpdateParams {
  storeName: string
  phone?: string
  address?: string
  longitude?: number
  latitude?: number
  businessHours?: string
  status: string
  description?: string
}

export interface StoreConfig {
  id: number
  storeId: number
  homeServiceRadiusKm: number
  bookingAdvanceDays: number
  bookingCancelHours: number
  timeSlotMinutes: number
  autoConfirmBooking: boolean
  contentAutoPublish: boolean
}

export interface StoreConfigUpdateParams {
  homeServiceRadiusKm: number
  bookingAdvanceDays: number
  bookingCancelHours: number
  timeSlotMinutes: number
  autoConfirmBooking: boolean
  contentAutoPublish: boolean
}

// V1 single store — fixed store ID. Must match the seed row in data-dev.sql
// (currently id=1001, the 萌宠家园上海徐汇店). Mismatch causes 404 "门店不存在".
// Exported so staff/other pages share the single source of truth.
export const STORE_ID = 1001

// ─── API Functions ───

/** GET /api/v1/admin/stores/{id} */
export const getStoreInfo = () => {
  return request.get<StoreInfo>(`/v1/admin/stores/${STORE_ID}`)
}

/** PATCH /api/v1/admin/stores/{id} */
export const updateStoreInfo = (data: StoreUpdateParams) => {
  return request.patch<StoreInfo>(`/v1/admin/stores/${STORE_ID}`, data)
}

/** GET /api/v1/admin/stores/{id}/config */
export const getStoreConfig = () => {
  return request.get<StoreConfig>(`/v1/admin/stores/${STORE_ID}/config`)
}

/** PUT /api/v1/admin/stores/{id}/config */
export const updateStoreConfig = (data: StoreConfigUpdateParams) => {
  return request.put<StoreConfig>(`/v1/admin/stores/${STORE_ID}/config`, data)
}
