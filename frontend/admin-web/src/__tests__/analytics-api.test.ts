import { describe, it, expect, vi, beforeEach } from 'vitest'

// request 拦截器在单测里被 mock 掉，只验证 api 模块与后端路由的 URL/参数契约
vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn(),
  },
}))

import request from '../utils/request'
import {
  getAnalyticsOverview,
  getDailyTrend,
  getTopItems,
  exportAnalyticsReport,
  downloadReportBlob,
} from '../api/analytics'

const mockedGet = request.get as unknown as ReturnType<typeof vi.fn>

describe('analytics api 契约', () => {
  beforeEach(() => {
    mockedGet.mockReset()
  })

  it('overview 请求 GET /v1/admin/analytics/overview 并携带日期参数', async () => {
    mockedGet.mockResolvedValue({ success: true, data: {} })
    await getAnalyticsOverview('2026-08-01', '2026-08-29')
    expect(mockedGet).toHaveBeenCalledWith('/v1/admin/analytics/overview', {
      params: { startDate: '2026-08-01', endDate: '2026-08-29' },
    })
  })

  it('daily-trend 请求 GET /v1/admin/analytics/daily-trend', async () => {
    mockedGet.mockResolvedValue({ success: true, data: [] })
    await getDailyTrend('2026-08-01', '2026-08-29')
    expect(mockedGet).toHaveBeenCalledWith('/v1/admin/analytics/daily-trend', {
      params: { startDate: '2026-08-01', endDate: '2026-08-29' },
    })
  })

  it('top-items 默认 limit=10 且显式 limit 生效', async () => {
    mockedGet.mockResolvedValue({ success: true, data: { services: [], products: [] } })
    await getTopItems('2026-08-01', '2026-08-29')
    expect(mockedGet).toHaveBeenLastCalledWith('/v1/admin/analytics/top-items', {
      params: { startDate: '2026-08-01', endDate: '2026-08-29', limit: 10 },
    })
    await getTopItems('2026-08-01', '2026-08-29', 5)
    expect(mockedGet).toHaveBeenLastCalledWith('/v1/admin/analytics/top-items', {
      params: { startDate: '2026-08-01', endDate: '2026-08-29', limit: 5 },
    })
  })

  it('export 走 fetch 且携带同源 Cookie', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, blob: async () => 'BLOB' })
    vi.stubGlobal('fetch', fetchMock)
    const blob = await exportAnalyticsReport('2026-08-01', '2026-08-29')
    expect(blob).toBe('BLOB')
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/admin/analytics/export?startDate=2026-08-01&endDate=2026-08-29',
      { credentials: 'same-origin' },
    )
    vi.unstubAllGlobals()
  })

  it('export 非 2xx 时抛错', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }))
    await expect(exportAnalyticsReport('2026-08-01', '2026-08-29')).rejects.toThrow('报表导出失败')
    vi.unstubAllGlobals()
  })

  it('downloadReportBlob 创建临时链接并触发下载', () => {
    const revokeSpy = vi.fn()
    const createSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(revokeSpy)
    const clickSpy = vi.fn()
    const linkSpy = vi
      .spyOn(document, 'createElement')
      .mockReturnValue({ click: clickSpy, href: '', download: '' } as unknown as HTMLAnchorElement)

    downloadReportBlob(new Blob(['x']), 'report.xlsx')

    expect(createSpy).toHaveBeenCalled()
    expect(linkSpy).toHaveBeenCalledWith('a')
    expect(clickSpy).toHaveBeenCalled()
    expect(revokeSpy).toHaveBeenCalledWith('blob:mock')
    vi.restoreAllMocks()
  })
})
