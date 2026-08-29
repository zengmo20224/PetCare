import request from '../utils/request'

// ─── Types ───

export interface OverviewReport {
  startDate: string
  endDate: string
  totalRevenue: number
  bookingRevenue: number
  productRevenue: number
  totalValidOrders: number
  validBookingOrders: number
  validProductOrders: number
  bookingTotal: number
  bookingCompletionRate: number
  productOrderTotal: number
  productCompletionRate: number
}

export interface DailyTrendPoint {
  statDate: string
  bookingCount: number
  bookingAmount: number
  productCount: number
  productAmount: number
}

export interface TopItemStat {
  itemId: number | null
  itemName: string | null
  quantity: number
  amount: number
}

export interface TopItemsReport {
  services: TopItemStat[]
  products: TopItemStat[]
}

// ─── API Functions ───

export const getAnalyticsOverview = (startDate: string, endDate: string) => {
  return request.get<OverviewReport>('/v1/admin/analytics/overview', {
    params: { startDate, endDate },
  })
}

export const getDailyTrend = (startDate: string, endDate: string) => {
  return request.get<DailyTrendPoint[]>('/v1/admin/analytics/daily-trend', {
    params: { startDate, endDate },
  })
}

export const getTopItems = (startDate: string, endDate: string, limit = 10) => {
  return request.get<TopItemsReport>('/v1/admin/analytics/top-items', {
    params: { startDate, endDate, limit },
  })
}

/**
 * Excel 导出。blob 响应不经过 axios 拦截器的 ApiResponse 解包，直接走 fetch
 * （同源请求自动携带 HttpOnly Cookie），文件名由后端 Content-Disposition 提供。
 */
export async function exportAnalyticsReport(startDate: string, endDate: string): Promise<Blob> {
  const query = `startDate=${encodeURIComponent(startDate)}&endDate=${encodeURIComponent(endDate)}`
  const resp = await fetch(`/api/v1/admin/analytics/export?${query}`, {
    credentials: 'same-origin',
  })
  if (!resp.ok) {
    throw new Error('报表导出失败')
  }
  return resp.blob()
}

/** 触发浏览器下载已生成的报表 blob。 */
export function downloadReportBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
