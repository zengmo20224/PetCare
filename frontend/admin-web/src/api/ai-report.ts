import request from '../utils/request'
import type { PageResponse, PageParams } from '../types/api'

// ─── Types ───

export type AiReportType = 'BUSINESS' | 'COMMUNITY' | 'SALES' | 'ACTIVITY'

export interface AiAnalysisReport {
  id: string
  reportType: AiReportType
  startDate: string
  endDate: string
  aiSummary: string
  suggestions: string
  createdBy: string
  createTime: string
}

export interface AiAnalysisCreateRequest {
  reportType: AiReportType
  startDate: string
  endDate: string
}

export interface AiReportQueryParams extends PageParams {
  reportType?: AiReportType | ''
}

// ─── API Functions ───

export const generateReport = (payload: AiAnalysisCreateRequest) => {
  return request.post<AiAnalysisReport>('/v1/admin/ai/analysis-reports', payload)
}

export const listReports = (params: AiReportQueryParams) => {
  return request.get<PageResponse<AiAnalysisReport>>('/v1/admin/ai/analysis-reports', { params })
}

export const getReport = (id: string) => {
  return request.get<AiAnalysisReport>(`/v1/admin/ai/analysis-reports/${id}`)
}
