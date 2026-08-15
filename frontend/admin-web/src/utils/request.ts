import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import type { ApiResponse } from '../types/api'
import { sanitizeErrorMessage } from './error-sanitizer'
import { getUserErrorMessage } from './feedback'

const axiosInstance = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// HttpOnly Cookie 双轨改造（2026-08-15）：凭证由后端 Set-Cookie 承载，
// 同源请求浏览器自动携带，不再注入 Authorization 头（token 不落 localStorage）。

/**
 * Response interceptor — unwraps AxiosResponse to ApiResponse<T>.
 * Uses `as never` to bypass strict AxiosResponse return type constraint.
 */
axiosInstance.interceptors.response.use(
  (response) => {
    const apiResponse = response.data as ApiResponse<unknown>

    if (apiResponse && apiResponse.success === false) {
      const errorMsg = sanitizeErrorMessage(apiResponse.error?.message || '请求失败')
      ElMessage.error(errorMsg)
      return Promise.reject(new Error(errorMsg)) as never
    }

    return apiResponse as never
  },
  (error) => {
    let userMessage = '网络连接失败'

    if (error.response) {
      const { status, data } = error.response
      const rawMsg = data?.error?.message || data?.message
      const msg = rawMsg ? sanitizeErrorMessage(rawMsg) : ''
      const requestUrl = error.config?.url || ''
      userMessage = getUserErrorMessage(error, '服务器错误')

      switch (status) {
        case 400:
          userMessage = msg || '请求参数不合法，请检查填写内容'
          ElMessage.warning(userMessage)
          break
        case 401:
          localStorage.removeItem('admin_auth')
          if (!requestUrl.includes('/admin/auth/login')) {
            router.push('/login')
          }
          userMessage = requestUrl.includes('/admin/auth/login')
            ? (msg || '用户名或密码错误')
            : '登录已过期，请重新登录'
          ElMessage.warning(userMessage)
          break
        case 403:
          router.push('/403')
          userMessage = msg || '无权访问该功能'
          break
        case 409:
          userMessage = msg || '数据状态冲突，请刷新后重试'
          ElMessage.warning(userMessage)
          break
        case 422:
          userMessage = msg || '提交数据验证失败'
          ElMessage.warning(userMessage)
          break
        default:
          userMessage = msg || userMessage
          ElMessage.error(userMessage)
      }
    } else {
      ElMessage.error('网络连接失败')
    }
    return Promise.reject(new Error(userMessage))
  },
)

/**
 * Typed API client.
 * Because the response interceptor strips AxiosResponse and returns ApiResponse<T>,
 * these wrappers correctly resolve to Promise<ApiResponse<T>>.
 */
const request = {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return axiosInstance.get(url, config) as Promise<unknown> as Promise<ApiResponse<T>>
  },

  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return axiosInstance.post(url, data, config) as Promise<unknown> as Promise<ApiResponse<T>>
  },

  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return axiosInstance.put(url, data, config) as Promise<unknown> as Promise<ApiResponse<T>>
  },

  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return axiosInstance.patch(url, data, config) as Promise<unknown> as Promise<ApiResponse<T>>
  },

  delete<T>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return axiosInstance.delete(url, config) as Promise<unknown> as Promise<ApiResponse<T>>
  },
}

export default request
