import request from '../utils/request'
// Types from api.ts are used indirectly through request wrapper return types

// ─── Request Types ───

export interface LoginParams {
  username: string
  password: string
}

// ─── Response Types (match backend DTOs exactly) ───

export interface AdminLoginResult {
  tokenType: string
  accessToken: string
  expiresInSeconds: number
  admin: {
    id: number
    username: string
    nickname: string | null
    role: string | null
  }
}

export interface AdminUserInfo {
  id: number
  username: string
  nickname: string | null
  role: string | null
  permissions: string[] | null
}

// ─── API Functions ───

/** Admin login — POST /api/v1/admin/auth/login */
export const login = (data: LoginParams) => {
  return request.post<AdminLoginResult>('/v1/admin/auth/login', data)
}

/** Admin logout — POST /api/v1/admin/auth/logout（服务端清 HttpOnly cookie） */
export const logout = () => {
  return request.post<null>('/v1/admin/auth/logout')
}

/** Get current admin info — GET /api/v1/admin/auth/me */
export const getUserInfo = () => {
  return request.get<AdminUserInfo>('/v1/admin/auth/me')
}
