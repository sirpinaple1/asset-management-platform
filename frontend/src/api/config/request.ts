import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from '@/api/interface'
import { clearToken, getToken, redirectToAuthCenter, setToken } from '@/utils/token'
import { dingTalkLogin, isInDingTalk } from '@/utils/dingtalk'

/** 401 跳登录页防抖（并发请求同时 401 时只跳一次） */
let redirectingToLogin = false

/**
 * 重置 401 跳转防抖标志（由路由守卫 beforeEach 调用，每次导航重置一次）。
 * 不重置的后果：整页跳转若被中断（beforeunload 拦截等），SPA 存活而标志卡死，
 * 二次 token 过期时 401 将无法再次触发跳转登录。
 */
export function resetLoginRedirectFlag(): void {
  redirectingToLogin = false
}

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // ADR-0004：前端统一携带 systemCode（asset 后端 TokenAuthFilter 目前只读 Bearer，此头为契约对齐保留）
  config.headers.systemCode = 'asset'
  return config
})

service.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    // 二进制响应（Excel 导出等）：无 Result 包装，直接透传给调用方处理
    if (response.config.responseType === 'blob') return response
    const body = response.data
    // 业务失败：HTTP 200 但 code != 200（asset-backend 常规业务错误）
    if (body.code !== 200) {
      if (!(response.config as RequestConfig).skipErrorToast) {
        ElMessage.error(body.message || '请求失败')
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response
  },
  (error) => {
    const status = error.response?.status
    const message: string = error.response?.data?.message || ''
    const skipErrorToast = (error.config as RequestConfig | undefined)?.skipErrorToast

    if (status === 401) {
      // token 无效/过期：钉钉容器内静默免登换新 token 后整页刷新；
      // 浏览器环境清除本地态并跳回 auth-center 登录页
      clearToken()
      if (!redirectingToLogin) {
        redirectingToLogin = true
        if (isInDingTalk()) {
          dingTalkLogin()
            .then((token) => {
              setToken(token)
              window.location.reload()
            })
            .catch((loginError) => {
              console.warn('[dingtalk] 静默重登失败，降级登录页：', loginError)
              ElMessage.error(message || '登录已失效，请重新登录')
              redirectToAuthCenter()
            })
        } else {
          ElMessage.error(message || '登录已失效，请重新登录')
          redirectToAuthCenter()
        }
      }
    } else if (!skipErrorToast) {
      if (status === 403) {
        ElMessage.error(message || '暂无访问权限')
      } else if (status === 503) {
        ElMessage.error(message || '鉴权服务暂不可用，请稍后重试')
      } else {
        ElMessage.error(message || error.message || '网络异常，请稍后重试')
      }
    }
    return Promise.reject(error)
  }
)

/** 扩展配置：skipErrorToast=true 时静默失败，不弹全局错误提示（版本信息探测等非关键请求） */
type RequestConfig = AxiosRequestConfig & { skipErrorToast?: boolean }

/** 请求泛型封装：直接返回 Result.data */
export async function request<T>(config: RequestConfig): Promise<T> {
  const response = await service.request<Result<T>>(config)
  return response.data.data
}

export default service
