/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** axios baseURL，开发默认 /api（走 Vite 代理） */
  readonly VITE_API_BASE_URL: string
  /** auth-center-frontend 登录页地址（token 失效后跳回） */
  readonly VITE_AUTH_CENTER_URL: string
  /** 通知 SSE 推送端点（可选；未配置或连接失败时自动降级 60s 轮询） */
  readonly VITE_NOTIFICATION_SSE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
