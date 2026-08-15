/**
 * User / authentication API.
 * Phone + password login is the primary authentication method.
 */

import { getPrimaryApiBaseUrl, http } from './request'
import type { ApiResponse } from '@/types/api'

/** Login / register response */
export interface AuthResult {
  tokenType: string
  accessToken: string
  expiresInSeconds: number
  user: { id: string; nickname: string }
}

/** User profile */
export interface UserProfile {
  id: string
  nickname: string
  avatarUrl: string | null
  phone: string | null
  gender: number | null
  status: string
  realName: string | null
  idCardNo: string | null
  idCardImageUrl: string | null
}

/** Security question for password recovery */
export interface SecurityQuestion {
  id: string
  question: string
}

/** Get preset security questions for registration */
export function getPresetSecurityQuestions(): Promise<ApiResponse<string[]>> {
  return http.get<string[]>('/api/v1/auth/security-questions')
}

/** Register with phone + password + security questions */
export function register(data: {
  phone: string
  password: string
  nickname: string
  securityQuestions: { questionIndex: number; answer: string }[]
}): Promise<ApiResponse<AuthResult>> {
  return http.post<AuthResult>('/api/v1/auth/register', data as any)
}

/** Login with phone + password */
export function login(data: { phone: string; password: string }): Promise<ApiResponse<AuthResult>> {
  return http.post<AuthResult>('/api/v1/auth/login', data as any)
}

/** Logout — POST /api/v1/auth/logout（服务端清 HttpOnly cookie，H5 登出必须调用） */
export function logout(): Promise<ApiResponse<null>> {
  return http.post<null>('/api/v1/auth/logout')
}

/** Get security questions for password recovery */
export function getSecurityQuestions(phone: string): Promise<ApiResponse<SecurityQuestion[]>> {
  return http.post<SecurityQuestion[]>('/api/v1/auth/forgot-password/questions', { phone })
}

/** Reset password by answering security questions */
export function resetPassword(data: {
  phone: string
  answers: { questionId: string; answer: string }[]
  newPassword: string
}): Promise<ApiResponse<void>> {
  return http.post<void>('/api/v1/auth/forgot-password/reset', data as any)
}

/**
 * WeChat mini-program login.
 * Sends the temporary authorization code (from `uni.login`) to the backend, which resolves
 * an openid and issues a JWT. The response shape matches password login (AuthResult), so the
 * same token-storage flow can be reused. Backend availability is controlled by
 * `petcare.wechat.mode` (disabled | mock | real); when disabled the backend returns 422 and
 * the request wrapper surfaces a sanitized error message.
 */
export function wechatLogin(code: string): Promise<ApiResponse<AuthResult>> {
  return http.post<AuthResult>('/api/v1/auth/wechat-login', { code } as any)
}

/** Get current user profile */
export function getUserProfile(): Promise<ApiResponse<UserProfile>> {
  return http.get<UserProfile>('/api/v1/user/profile')
}

/** Update current user profile */
export function updateUserProfile(data: {
  nickname?: string
  avatarUrl?: string
  gender?: number
  realName?: string
  idCardNo?: string
  idCardImageUrl?: string
}): Promise<ApiResponse<UserProfile>> {
  return http.put<UserProfile>('/api/v1/user/profile', data as any)
}

/** Upload a file (avatar, ID card image, etc.) */
export function uploadFile(filePath: string): Promise<ApiResponse<{ url: string }>> {
  return new Promise((resolve) => {
    const token = uni.getStorageSync('user_token') || ''
    const baseUrl = getPrimaryApiBaseUrl()
    uni.uploadFile({
      url: `${baseUrl}/api/v1/upload`,
      filePath,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        try {
          const body = JSON.parse(res.data) as ApiResponse<{ url: string }>
          resolve(body)
        } catch {
          resolve({ success: false, error: { code: 'PARSE_ERROR', message: '解析上传响应失败' } })
        }
      },
      fail: () => {
        resolve({ success: false, error: { code: 'NETWORK_ERROR', message: '上传失败' } })
      },
    })
  })
}

// ---- Pet profile ----

/** Pet profile as returned by backend */
export interface PetItem {
  petId: string
  name: string
  type: string
  breed: string | null
  gender: number | null
  age: number | null
  weight: number | null
  size: string | null
  sterilized: number | null
  avatarUrl: string | null
  remark: string | null
}

/** List current user's pets (requires auth) */
export function getMyPets(): Promise<ApiResponse<PetItem[]>> {
  return http.get<PetItem[]>('/api/v1/user/pets')
}

// ---- User address ----

/** Address as returned by backend */
export interface AddressItem {
  addressId: string
  contactName: string
  contactPhone: string
  province: string | null
  city: string | null
  district: string | null
  detailAddress: string | null
  longitude: number | null
  latitude: number | null
  isDefault: boolean
}

/** List current user's addresses (requires auth) */
export function getMyAddresses(): Promise<ApiResponse<AddressItem[]>> {
  return http.get<AddressItem[]>('/api/v1/user/addresses')
}

/** Create a new address (requires auth) */
export function createAddress(data: {
  contactName: string
  contactPhone: string
  province: string
  city: string
  district?: string
  detailAddress: string
  longitude?: number
  latitude?: number
  isDefault?: boolean
}): Promise<ApiResponse<AddressItem>> {
  return http.post<AddressItem>('/api/v1/user/addresses', data as any)
}

/** Update an address (requires auth) */
export function updateAddress(addressId: string, data: {
  contactName: string
  contactPhone: string
  province: string
  city: string
  district?: string
  detailAddress: string
  longitude?: number
  latitude?: number
  isDefault?: boolean
}): Promise<ApiResponse<AddressItem>> {
  return http.put<AddressItem>(`/api/v1/user/addresses/${addressId}`, data as any)
}

/** Delete an address (requires auth) */
export function deleteAddress(addressId: string): Promise<ApiResponse<void>> {
  return http.delete<void>(`/api/v1/user/addresses/${addressId}`)
}

// ---- Pet CRUD ----

/** Create a new pet (requires auth) */
export function createPet(data: {
  name: string
  type: string
  breed?: string
  gender?: number
  age?: number
  weight?: number
  size?: string
  sterilized?: number
  avatarUrl?: string
  remark?: string
}): Promise<ApiResponse<PetItem>> {
  return http.post<PetItem>('/api/v1/user/pets', data as any)
}

/** Update a pet (requires auth) */
export function updatePet(petId: string, data: {
  name: string
  type: string
  breed?: string
  gender?: number
  age?: number
  weight?: number
  size?: string
  sterilized?: number
  avatarUrl?: string
  remark?: string
}): Promise<ApiResponse<PetItem>> {
  return http.put<PetItem>(`/api/v1/user/pets/${petId}`, data as any)
}

/** Delete a pet (requires auth) */
export function deletePet(petId: string): Promise<ApiResponse<void>> {
  return http.delete<void>(`/api/v1/user/pets/${petId}`)
}

// ---- Security question management (requires auth) ----

/** 当前用户已设置的安全问题（仅题目，不回答案） */
export interface MySecurityQuestion {
  id: string
  question: string
  sort: number
}

/** 查看当前用户已设置的安全问题（我的-密保管理） */
export function getMySecurityQuestions(): Promise<ApiResponse<MySecurityQuestion[]>> {
  return http.get<MySecurityQuestion[]>('/api/v1/user/security-questions')
}

/** 整体更新当前用户的安全问题（强制 2 个有效密保） */
export function updateSecurityQuestions(data: {
  securityQuestions: { questionIndex: number; answer: string }[]
}): Promise<ApiResponse<void>> {
  return http.put<void>('/api/v1/user/security-questions', data as any)
}

// ---- Change password (requires auth) ----

/** 修改密码（旧密码路径，密保重置路径见 resetPassword） */
export function changePassword(data: {
  oldPassword: string
  newPassword: string
}): Promise<ApiResponse<void>> {
  return http.put<void>('/api/v1/user/password', data as any)
}
