import request from '../utils/request'
import type { PageResponse, PageParams } from '../types/api'

// ─── Types ───

export interface OperationLog {
  id: string | number
  adminId: string | number
  module: string
  operation: string
  requestMethod: string
  requestUrl: string
  result: string
  /** Operation target details, e.g. "targetName=张三, targetId=3001, phone=139****1234". */
  requestParams: string | null
  errorMessage: string | null
  createTime: string
}

export interface OperationLogQueryParams extends PageParams {
  module?: string
}

// ─── API Functions ───

export const getOperationLogs = (params: OperationLogQueryParams) => {
  return request.get<PageResponse<OperationLog>>('/v1/admin/operation-logs', { params })
}
