/**
 * HTTP request layer using uni.request.
 * Adapts admin-web Axios patterns to uni-app runtime.
 */

import type { ApiResponse } from '@/types/api'
import { sanitizeErrorMessage } from '@/utils/error-sanitizer'

const DEFAULT_MP_API_BASE_URL = 'http://127.0.0.1:8080'
const REQUEST_TIMEOUT_MS = 3000

function normalizeBaseUrl(url: string | undefined): string {
  return (url || '').trim().replace(/\/+$/, '')
}

function uniqueUrls(urls: string[]): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const url of urls) {
    const normalized = normalizeBaseUrl(url)
    if (!normalized && seen.has('')) continue
    if (normalized && seen.has(normalized)) continue
    seen.add(normalized)
    result.push(normalized)
  }
  return result
}

function configuredMpFallbackUrls(): string[] {
  return (import.meta.env.VITE_MP_API_BASE_FALLBACK_URLS || '')
    .split(',')
    .map((url: string) => normalizeBaseUrl(url))
    .filter(Boolean)
}

/**
 * 真机调试自检：微信小程序真机上 localhost/127.0.0.1 指代手机自己，
 * 不可能连到电脑后端。检测到主地址仍是回环地址时，弹一次明显提示，
 * 避免后续每次都闷头排查。仅触发一次。
 */
let mpLoopbackWarned = false
function warnIfMpLoopback(baseUrl: string): void {
  // #ifdef MP-WEIXIN
  if (mpLoopbackWarned) return
  const lowered = baseUrl.toLowerCase()
  if (/^(https?:\/\/)(localhost|127\.0\.0\.1)(:\d+)?(\/|$)/.test(lowered)) {
    mpLoopbackWarned = true
    uni.showToast({
      title: '请在 .env 配置电脑局域网 IP，localhost 在真机指手机自己',
      icon: 'none',
      duration: 4000,
    })
  }
  // #endif
}

export function getRequestBaseUrls(): string[] {
  const apiBaseUrl = normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL)

  // #ifdef MP-WEIXIN
  const mpBaseUrl = normalizeBaseUrl(import.meta.env.VITE_MP_API_BASE_URL)
  const mpUrls = uniqueUrls([
    mpBaseUrl,
    ...configuredMpFallbackUrls(),
    apiBaseUrl,
    DEFAULT_MP_API_BASE_URL,
  ]).filter(Boolean)
  const result = mpUrls.length > 0 ? mpUrls : [DEFAULT_MP_API_BASE_URL]
  warnIfMpLoopback(result[0])
  return result
  // #endif

  return uniqueUrls([apiBaseUrl])
}

export function getPrimaryApiBaseUrl(): string {
  return getRequestBaseUrls()[0] || ''
}

type RequestData = Exclude<UniNamespace.RequestOptions['data'], undefined>
type RequestMethod = Exclude<UniNamespace.RequestOptions['method'], undefined>

interface RequestOptions {
  url: string
  method?: RequestMethod
  data?: RequestData
  params?: Record<string, unknown>
  header?: Record<string, string>
}

/** Build query string from params object */
function buildQueryString(params: Record<string, unknown>): string {
  const parts: string[] = []
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null) {
      parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    }
  }
  return parts.length > 0 ? `?${parts.join('&')}` : ''
}

/**
 * Get stored user token.
 * HttpOnly Cookie 双轨（2026-08-15）：H5 凭证由 HttpOnly cookie 承载（同源自动携带），
 * 不再读 storage 注入 Authorization——返回 null 让请求层省略该头；
 * 微信小程序运行时无 cookie，永久保留 storage + header 通道。
 */
function getToken(): string | null {
  // #ifdef H5
  return null
  // #endif
  // #ifndef H5
  try {
    return uni.getStorageSync('user_token') || null
  } catch {
    return null
  }
  // #endif
}

/** Clear stored credentials on auth failure（两个 key 都清，跨端安全） */
function clearToken(): void {
  try {
    uni.removeStorageSync('user_token')
    uni.removeStorageSync('user_auth')
  } catch {
    // ignore storage errors
  }
}

/** Show a toast message */
function showToast(title: string, icon: 'none' | 'success' | 'error' | 'loading' = 'none'): void {
  uni.showToast({ title, icon, duration: 2000 })
}

/**
 * Typed request wrapper around uni.request.
 * Returns ApiResponse<T> directly (unwrapped from HTTP response).
 */
export function request<T>(options: RequestOptions): Promise<ApiResponse<T>> {
  const token = getToken()
  const queryString = options.params ? buildQueryString(options.params) : ''
  const requestUrls = getRequestBaseUrls().map(baseUrl => `${baseUrl}${options.url}${queryString}`)

  return new Promise((resolve) => {
    const send = (urlIndex: number) => {
      uni.request({
        url: requestUrls[urlIndex],
        method: options.method || 'GET',
        data: options.data,
        timeout: REQUEST_TIMEOUT_MS,
        header: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...options.header,
        },
        success: (res) => {
          const statusCode = res.statusCode
          const body = res.data as ApiResponse<T>

          if (statusCode === 401) {
            clearToken()
            showToast('登录已过期，请重新登录')
            resolve({ success: false, error: { code: 'UNAUTHORIZED', message: '登录已过期' } })
            return
          }

          if (statusCode === 403) {
            resolve({ success: false, error: { code: 'FORBIDDEN', message: '无权访问' } })
            return
          }

          if (statusCode === 409) {
            const msg = body?.error?.message || '数据状态冲突，请刷新后重试'
            showToast(msg)
            resolve({ success: false, error: { code: 'CONFLICT', message: msg } })
            return
          }

          if (statusCode === 422) {
            const msg = body?.error?.message || '提交数据验证失败'
            showToast(msg)
            resolve({ success: false, error: { code: 'UNPROCESSABLE', message: msg } })
            return
          }

          if (statusCode >= 400) {
            const msg = body?.error?.message ? sanitizeErrorMessage(body.error.message) : '请求失败'
            showToast(msg)
            resolve({ success: false, error: { code: `HTTP_${statusCode}`, message: msg } })
            return
          }

          // 2xx success — check business-level error
          if (body && body.success === false) {
            const msg = body.error?.message ? sanitizeErrorMessage(body.error.message) : '操作失败'
            showToast(msg)
            resolve({ success: false, error: { code: body.error?.code || 'BIZ_ERROR', message: msg, details: body.error?.details } })
            return
          }

          resolve(body || { success: true })
        },
        fail: () => {
          if (urlIndex < requestUrls.length - 1) {
            send(urlIndex + 1)
            return
          }
          showToast('网络连接失败')
          resolve({ success: false, error: { code: 'NETWORK_ERROR', message: '网络连接失败' } })
        },
      })
    }

    send(0)
  })
}

/** Convenience methods */
export const http = {
  get<T>(url: string, params?: Record<string, unknown>): Promise<ApiResponse<T>> {
    return request<T>({ url, method: 'GET', params })
  },

  post<T>(url: string, data?: RequestData): Promise<ApiResponse<T>> {
    return request<T>({ url, method: 'POST', data })
  },

  put<T>(url: string, data?: RequestData): Promise<ApiResponse<T>> {
    return request<T>({ url, method: 'PUT', data })
  },

  delete<T>(url: string): Promise<ApiResponse<T>> {
    return request<T>({ url, method: 'DELETE' })
  },
}

export default http
