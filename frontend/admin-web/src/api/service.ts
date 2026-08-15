import request from '../utils/request'
import type { PageResponse, PageParams } from '../types/api'

// ─── Types (match backend DTOs exactly) ───

export interface ServiceCategory {
  id: number
  name: string
  iconUrl: string | null
  sort: number
}

export interface ServiceItem {
  id: number
  categoryId: number
  name: string
  serviceMode: string // STORE | HOME | BOTH
  price: number
  durationMinutes: number
  petType: string | null // DOG | CAT | ALL
  petSize: string | null // SMALL | MEDIUM | LARGE | ALL
  needAddress: boolean
  needPet: boolean
  description: string | null
  coverUrl: string | null
  imageUrls: string[]
  status: string // ON_SALE | OFF_SALE
  sort: number | null
}

export interface ServiceItemCreateParams {
  categoryId: number
  name: string
  serviceMode: string
  price: number
  durationMinutes: number
  petType?: string
  petSize?: string
  needAddress: boolean
  needPet: boolean
  description?: string
  coverUrl?: string
  imageUrls?: string[]
  sort?: number
}

// Backend only accepts: page, size, status (no name filter)
export interface ServiceItemQueryParams extends PageParams {
  status?: string
}

// ─── API Functions ───

/** GET /api/v1/service-categories — lists active service categories */
export const getServiceCategories = () => {
  return request.get<ServiceCategory[]>('/v1/service-categories')
}

/** GET /api/v1/admin/service-items */
export const getServiceItems = (params: ServiceItemQueryParams) => {
  return request.get<PageResponse<ServiceItem>>('/v1/admin/service-items', { params })
}

/** POST /api/v1/admin/service-items */
export const createServiceItem = (data: ServiceItemCreateParams) => {
  return request.post<ServiceItem>('/v1/admin/service-items', data)
}

/** PUT /api/v1/admin/service-items/{id} */
export const updateServiceItem = (id: number, data: ServiceItemCreateParams) => {
  return request.put<ServiceItem>(`/v1/admin/service-items/${id}`, data)
}

/** POST /api/v1/admin/service-items/{id}/disable */
export const disableServiceItem = (id: number) => {
  return request.post<ServiceItem>(`/v1/admin/service-items/${id}/disable`)
}

/** POST /api/v1/admin/service-items/{id}/enable */
export const enableServiceItem = (id: number) => {
  return request.post<ServiceItem>(`/v1/admin/service-items/${id}/enable`)
}
