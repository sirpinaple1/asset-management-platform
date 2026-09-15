import { redirectToAuthCenter } from '@/utils/token'

const TOKEN_KEY = 'asset_token'

/**
 * 多标签页会话同步（P0）。
 *
 * 监听 storage 事件（仅在其他标签页修改 localStorage 时触发，本页修改不触发）：
 * - token 被清除（另一标签页退出登录）→ 本页同步跳转登录中心，避免残留旧 token 继续请求
 * - token 被替换（另一标签页换账号重新登录）→ 本页整页刷新，载入新用户身份与数据
 */
export function initSessionSync(): void {
  window.addEventListener('storage', (e: StorageEvent) => {
    if (e.key !== TOKEN_KEY) return
    if (e.newValue === null) {
      redirectToAuthCenter()
    } else if (typeof e.newValue === 'string') {
      window.location.reload()
    }
  })
}
