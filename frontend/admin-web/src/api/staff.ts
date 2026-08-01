import request from '../utils/request'
import type { PageResponse, PageParams } from '../types/api'

// ─── Types ───

export interface StaffMember {
  id: number
  storeId: number
  name: string
  phone: string | null
  avatarUrl: string | null
  // role 为自由文本（自 efd8632 起由枚举改为 @Size(max=32)，店主可自定义岗位）。
  role: string
  status: string
  description: string | null
}

export interface StaffCreateParams {
  storeId: number
  name: string
  phone?: string
  avatarUrl?: string
  role: string
  description?: string
  skillCategoryIds?: number[]
}

export interface StaffSkillView {
  staffId: number
  serviceCategoryIds: number[]
}

export interface StaffSkillUpdateParams {
  serviceCategoryIds: number[]
}

export interface StaffSchedule {
  id: number
  staffId: number
  storeId: number
  workDate: string
  startTime: string
  endTime: string
  status: string // AVAILABLE | UNAVAILABLE
  remark: string | null
}

export interface StaffScheduleCreateParams {
  storeId: number
  workDate: string
  startTime: string
  endTime: string
  status: string
  remark?: string
}

export interface StaffQueryParams extends PageParams {
  status?: string
}

// ─── Staff CRUD ───

export const getStaffList = (params: StaffQueryParams) => {
  return request.get<PageResponse<StaffMember>>('/v1/admin/staff', { params })
}

export const createStaff = (data: StaffCreateParams) => {
  return request.post<StaffMember>('/v1/admin/staff', data)
}

export const updateStaff = (id: number, data: StaffCreateParams) => {
  return request.put<StaffMember>(`/v1/admin/staff/${id}`, data)
}

export const disableStaff = (id: number) => {
  return request.post<StaffMember>(`/v1/admin/staff/${id}/disable`)
}

export const enableStaff = (id: number) => {
  return request.post<StaffMember>(`/v1/admin/staff/${id}/enable`)
}

// ─── Staff Skills ───
// 岗位自由化后（commit efd8632），员工的可服务类别完全由 staff_skill 关联表决定，
// role 字段退化为自由文本。两个端点都需要 staff:skill:manage 权限。

/** PUT /api/v1/admin/staff/{id}/skills — 整体替换该员工的技能；@PreAuthorize('staff:skill:manage') */
export const updateStaffSkills = (staffId: number, data: StaffSkillUpdateParams) => {
  return request.put<StaffSkillView>(`/v1/admin/staff/${staffId}/skills`, data)
}

/** GET /api/v1/admin/staff/{id}/skills — @PreAuthorize('staff:skill:manage') */
export const getStaffSkills = (staffId: number) => {
  return request.get<StaffSkillView>(`/v1/admin/staff/${staffId}/skills`)
}

// ─── Staff Schedules ───

export const getStaffSchedules = (staffId: number, params?: PageParams) => {
  return request.get<PageResponse<StaffSchedule>>(`/v1/admin/staff/${staffId}/schedules`, { params })
}

export const createStaffSchedule = (staffId: number, data: StaffScheduleCreateParams) => {
  return request.post<StaffSchedule>(`/v1/admin/staff/${staffId}/schedules`, data)
}

export const updateStaffSchedule = (staffId: number, scheduleId: number, data: StaffScheduleCreateParams) => {
  return request.put<StaffSchedule>(`/v1/admin/staff/${staffId}/schedules/${scheduleId}`, data)
}
