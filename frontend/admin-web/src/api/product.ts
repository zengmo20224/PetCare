import request from '../utils/request'
import type { PageResponse, PageParams } from '../types/api'

// ─── Types ───

export interface Product {
  id: number
  categoryId: number
  name: string
  coverUrl: string | null
  price: number
  stock: number | null
  salesCount: number | null
  description: string | null
  pickupOnly: boolean
  imageUrls: string[]
  detailImageUrls: string[]
  status: string
  sort: number | null
}

export interface ProductCreateParams {
  categoryId: number
  name: string
  coverUrl?: string
  price: number
  description?: string
  pickupOnly: boolean
  imageUrls?: string[]
  detailImageUrls?: string[]
  sort?: number
}

export interface ProductStockUpdateParams {
  stock: number
}

export interface ProductQueryParams extends PageParams {
  status?: string
}

// ─── API Functions ───

export const getProductList = (params: ProductQueryParams) => {
  return request.get<PageResponse<Product>>('/v1/admin/products', { params })
}

export const createProduct = (data: ProductCreateParams) => {
  return request.post<Product>('/v1/admin/products', data)
}

export const updateProduct = (id: number, data: ProductCreateParams) => {
  return request.put<Product>(`/v1/admin/products/${id}`, data)
}

export const disableProduct = (id: number) => {
  return request.post<Product>(`/v1/admin/products/${id}/disable`)
}

export const enableProduct = (id: number) => {
  return request.post<Product>(`/v1/admin/products/${id}/enable`)
}

export const updateProductStock = (id: number, data: ProductStockUpdateParams) => {
  return request.put<Product>(`/v1/admin/products/${id}/stock`, data)
}
