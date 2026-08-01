/**
 * User store — authentication boundary.
 * Phone + password is the primary authentication method.
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, wechatLogin as apiWechatLogin, getUserProfile, type UserProfile } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(loadToken())
  const isLoggedIn = computed(() => !!token.value)
  const profile = ref<UserProfile | null>(null)

  function loadToken(): string | null {
    try {
      return uni.getStorageSync('user_token') || null
    } catch {
      return null
    }
  }

  function setToken(newToken: string | null): void {
    token.value = newToken
    if (newToken) {
      try {
        uni.setStorageSync('user_token', newToken)
      } catch {
        // storage write failed — non-critical
      }
    } else {
      try {
        uni.removeStorageSync('user_token')
      } catch {
        // ignore
      }
    }
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
    setToken(null)
    profile.value = null
  }

  return { token, isLoggedIn, profile, setToken, setAuthToken, doLogin, doWechatLogin, fetchProfile, logout }
})
