import request from '../utils/request'
import type { PageResponse, PageParams } from '../types/api'

// ─── Types ───

export interface AiUsageLog {
  id: string
  userId: string | null
  adminId: string | null
  apiType: string
  modelName: string | null
  promptTokens: number | null
  completionTokens: number | null
  totalTokens: number | null
  success: boolean
  errorMessage: string | null
  createTime: string
}

export interface AiUsageQueryParams extends PageParams {
  apiType?: string | ''
  success?: boolean | ''
  startDate?: string
  endDate?: string
}

// ─── API Functions ───

/** List AI usage logs. Requires ai:usage:read permission. */
export function listUsage(params: AiUsageQueryParams) {
  return request.get<PageResponse<AiUsageLog>>('/v1/admin/ai/usage', {
    params,
  })
}
