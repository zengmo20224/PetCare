/**
 * User store — authentication boundary.
 * Phone + password is the primary authentication method.
 *
 * HttpOnly Cookie 双轨改造（2026-08-15）：
 * - H5：不把 accessToken 落 storage（XSS 可窃取）——凭证由后端 Set-Cookie
 *   （HttpOnly，JS 不可读）承载，同源请求自动携带；本地只保存非敏感登录标记
 *   user_auth 驱动 isLoggedIn（约 15 个页面的登录 gate）。
 * - 微信小程序：运行时无 cookie，永久保留 storage + Authorization header 通道。
 * - 真实有效性由接口 401 兜底（清除标记并提示重新登录）。
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  login as apiLogin,
  wechatLogin as apiWechatLogin,
  getUserProfile,
  logout as apiLogout,
  type UserProfile,
} from '@/api/user'

const TOKEN_KEY = 'user_token'
/** H5 非敏感登录标记（值恒为 '1'，不含凭证） */
const AUTH_FLAG_KEY = 'user_auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(loadToken())
  const isLoggedIn = computed(() => !!token.value)
  const profile = ref<UserProfile | null>(null)

  function loadToken(): string | null {
    try {
      // #ifdef H5
      return uni.getStorageSync(AUTH_FLAG_KEY) ? 'cookie-session' : null
      // #endif
      // #ifndef H5
      return uni.getStorageSync(TOKEN_KEY) || null
      // #endif
    } catch {
      return null
    }
  }

  function setToken(newToken: string | null): void {
    if (!newToken) {
      token.value = null
      try {
        uni.removeStorageSync(TOKEN_KEY)
        uni.removeStorageSync(AUTH_FLAG_KEY)
      } catch {
        // ignore
      }
      return
    }
    // #ifdef H5
    token.value = 'cookie-session'
    try {
      uni.setStorageSync(AUTH_FLAG_KEY, '1')
    } catch {
      // storage write failed — non-critical
    }
    // #endif
    // #ifndef H5
    token.value = newToken
    try {
      uni.setStorageSync(TOKEN_KEY, newToken)
    } catch {
      // storage write failed — non-critical
    }
    // #endif
  }

  /** Login with phone + password */
  async function doLogin(phone: string, password: string): Promise<boolean> {
    const res = await apiLogin({ phone, password })
    if (res.success && res.data) {
      setToken(res.data.accessToken)
      return true
    }
    return false
  }

  /**
   * WeChat mini-program login.
   * `uni.login({ provider: 'weixin' })` yields a temporary code, which the backend exchanges
   * for an openid (real mode) or deterministically derives one (mock mode). Either way a JWT
   * is issued and stored exactly like password login. The H5 build never calls this.
   */
  async function doWechatLogin(code: string): Promise<boolean> {
    const res = await apiWechatLogin(code)
    if (res.success && res.data) {
      setToken(res.data.accessToken)
      return true
    }
    return false
  }

  /** Set token directly (used by register flow) */
  function setAuthToken(newToken: string): void {
    setToken(newToken)
  }

  /** Fetch current user profile */
  async function fetchProfile(): Promise<boolean> {
    if (!token.value) return false
    const res = await getUserProfile()
    if (res.success && res.data) {
      profile.value = res.data
      return true
    }
    return false
  }

  function logout(): void {
    // H5：HttpOnly cookie 前端删不掉，必须服务端清；失败不阻塞本地登出
    // #ifdef H5
    apiLogout().catch(() => {})
    // #endif
    setToken(null)
    profile.value = null
  }

  return { token, isLoggedIn, profile, setToken, setAuthToken, doLogin, doWechatLogin, fetchProfile, logout }
})
