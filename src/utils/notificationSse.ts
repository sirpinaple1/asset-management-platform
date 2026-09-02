import { getToken } from '@/utils/token'

/**
 * 通知 SSE 推送客户端（P0 基建，预备代码）。
 *
 * 使用方式：在 .env 中配置 VITE_NOTIFICATION_SSE_URL（如 http://localhost:6006/api/v1/notifications/sse），
 * 后端推送端点就绪后无需改动前端代码即插即用。
 *
 * 行为约定：
 * - 未配置端点 URL → 不建立连接，直接走轮询
 * - 连接建立后收到任意消息 → 触发 onNotify（由 store 刷新未读数）
 * - 连接失败/中断 → 标记失败并触发 onFallback，由 store 降级为 60s 轮询（本会话不再重试）
 * - EventSource 无法携带 Authorization 头，token 通过 query 参数传递（后端端点需支持）
 */

const SSE_URL: string = import.meta.env.VITE_NOTIFICATION_SSE_URL || ''

let source: EventSource | undefined
let failed = false

/** 尝试建立 SSE 连接；返回 false 表示不可用（未配置/已失败/已连接/未登录），调用方应降级轮询 */
export function connectNotificationSse(onNotify: () => void, onFallback: () => void): boolean {
  if (source) return true
  if (!SSE_URL || failed) return false
  const token = getToken()
  if (!token) return false

  try {
    source = new EventSource(`${SSE_URL}?token=${encodeURIComponent(token)}`)
  } catch {
    failed = true
    return false
  }

  source.onmessage = () => onNotify()
  source.onerror = () => {
    // 首个错误即降级（端点不存在时 EventSource 会持续重连报错，避免无限重试）
    failed = true
    closeNotificationSse()
    onFallback()
  }
  return true
}

/** 关闭 SSE 连接（铃铛全部卸载 / 登出时调用） */
export function closeNotificationSse(): void {
  source?.close()
  source = undefined
}
