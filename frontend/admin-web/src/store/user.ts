import { defineStore } from 'pinia'
import { login, logout as logoutApi, getUserInfo } from '../api/auth'
import type { LoginParams, AdminUserInfo } from '../api/auth'
import { ref } from 'vue'

/**
 * 登录态存储（HttpOnly Cookie 双轨改造，2026-08-15）。
 *
 * 管理端不再把 accessToken 存 localStorage（XSS 可窃取）——凭证由后端
 * Set-Cookie（HttpOnly+SameSite=Strict，JS 不可读）承载，同源请求自动携带。
 * 这里只保存非敏感的登录标记 admin_auth，驱动路由守卫与 UI 状态；
 * 真实有效性由每次 /me（getInfoAction）与 401 兜底保证。
 */
const AUTH_FLAG_KEY = 'admin_auth'

export const useUserStore = defineStore('user', () => {
  // 登录标记：存在即视为已登录（router guard 只做布尔判断）。
  // 命名保留 token 以兼容守卫/页面既有引用，值不再是 JWT。
  const token = ref<string | null>(localStorage.getItem(AUTH_FLAG_KEY) ? 'cookie-session' : null)
  const userInfo = ref<AdminUserInfo | null>(null)

  const loginAction = async (data: LoginParams) => {
    const res = await login(data)
    // 后端已 Set-Cookie（HttpOnly），这里仅登记非敏感标记
    if (res.data?.accessToken) {
      localStorage.setItem(AUTH_FLAG_KEY, '1')
      token.value = 'cookie-session'
    }
  }

  const getInfoAction = async () => {
    const res = await getUserInfo()
    if (res.data) {
      userInfo.value = res.data
    }
  }

  const logoutAction = () => {
    // HttpOnly cookie 前端删不掉，必须服务端清；失败不阻塞本地登出
    logoutApi().catch(() => {})
    token.value = null
    userInfo.value = null
    localStorage.removeItem(AUTH_FLAG_KEY)
  }

  /** Check if current user has a specific permission code */
  const hasPermission = (perm: string): boolean => {
    return userInfo.value?.permissions?.includes(perm) ?? false
  }

  return { token, userInfo, loginAction, getInfoAction, logoutAction, hasPermission }
})
