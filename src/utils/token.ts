/**
 * token 存取与 URL 清洗工具。
 *
 * 传递契约（已对照 auth-center-frontend handleProjectClick 核实）：
 * auth-center 登录后跳子系统 `?token=xxx&name=xxx&systemcode=asset`；
 * history 路由拼在 search 上，hash 路由拼在 # 后（两处都做兼容读取）。
 * 本工程为 history 路由，token 正常出现在 location.search。
 */

const TOKEN_KEY = 'asset_token'
/** auth-center 传递的用户名参数（仅展示用，权威数据来自 /api/v1/me） */
const URL_NAME_KEY = 'name'
const URL_SYSTEM_CODE_KEY = 'systemcode'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * 从当前 URL 读取 token：
 * 1. location.search 中的 token（history 路由，auth-center 普通拼接）
 * 2. hash query 中的 token（防 web_url 注册成 hash 形式）
 * 3. 路由 query（Vue Router 解析后的 to.query，兜底）
 */
export function extractTokenFromUrl(routeQueryToken?: unknown): string {
  const fromSearch = new URLSearchParams(window.location.search).get('token')
  if (fromSearch) return fromSearch

  const hash = window.location.hash
  const hashQueryIndex = hash.indexOf('?')
  if (hashQueryIndex !== -1) {
    const fromHash = new URLSearchParams(hash.slice(hashQueryIndex + 1)).get('token')
    if (fromHash) return fromHash
  }

  if (typeof routeQueryToken === 'string' && routeQueryToken) return routeQueryToken
  return ''
}

/**
 * 从 URL 清除 token/name/systemcode（search 与 hash 两处），
 * 用 replaceState 避免带 token 的地址留在浏览器历史。
 */
export function stripAuthParamsFromUrl(): void {
  const url = new URL(window.location.href)
  let touched = false

  for (const key of ['token', URL_NAME_KEY, URL_SYSTEM_CODE_KEY]) {
    if (url.searchParams.has(key)) {
      url.searchParams.delete(key)
      touched = true
    }
  }

  const hash = url.hash
  const hashQueryIndex = hash.indexOf('?')
  if (hashQueryIndex !== -1) {
    const hashParams = new URLSearchParams(hash.slice(hashQueryIndex + 1))
    let hashTouched = false
    for (const key of ['token', URL_NAME_KEY, URL_SYSTEM_CODE_KEY]) {
      if (hashParams.has(key)) {
        hashParams.delete(key)
        hashTouched = true
      }
    }
    if (hashTouched) {
      const query = hashParams.toString()
      url.hash = hash.slice(0, hashQueryIndex) + (query ? `?${query}` : '')
      touched = true
    }
  }

  if (touched) {
    window.history.replaceState({}, '', url.toString())
  }
}

/** 跳回 auth-center-frontend 登录页，携带 returnUrl 供登录后跳回 */
export function redirectToAuthCenter(): void {
  const base = import.meta.env.VITE_AUTH_CENTER_URL || `${window.location.origin}`
  const returnUrl = encodeURIComponent(window.location.origin + window.location.pathname)
  window.location.href = `${base}/login?returnUrl=${returnUrl}`
}
