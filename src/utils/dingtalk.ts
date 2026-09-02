/**
 * 钉钉免登工具（企业内部 H5 微应用）。
 *
 * 链路：dd.runtime.permission.requestAuthCode(corpId) → 授权码
 *      → GET /auth-api/api/dingtalk/getUserInfo?code=xxx（comm_public_basic 现成接口）
 *      → LoginUserVo（含 token），token 走既有 Redis 鉴权体系。
 *
 * corpId 优先取 URL ?corpid=（钉钉工作台打开微应用时自动附加），
 * 其次取构建期 VITE_DINGTALK_CORP_ID。
 */
import * as dd from 'dingtalk-jsapi'
import axios from 'axios'

/** 鉴权 API 基础路径：生产网关 /auth-api → comm_public_basic；本地 dev 由 vite 代理到 6002 */
const AUTH_API_BASE = import.meta.env.VITE_AUTH_API_BASE || '/auth-api'

/** 是否运行在钉钉客户端容器内（PC/手机工作台打开的 H5 微应用） */
export function isInDingTalk(): boolean {
  return /DingTalk/i.test(navigator.userAgent)
}

/** 企业 corpId：URL ?corpid= 优先（工作台打开自动携带），其次构建期配置 */
function resolveCorpId(): string {
  const fromUrl = new URLSearchParams(window.location.search).get('corpid')
  if (fromUrl) return fromUrl
  return import.meta.env.VITE_DINGTALK_CORP_ID || ''
}

/** comm_public_basic 响应包装（{ code, message, data }，code=200 成功） */
interface AuthResult<T> {
  code: number
  message?: string
  data: T
}

interface DingTalkLoginUserVo {
  token: string
  name?: string
  username?: string
  /** 仍使用初始默认密码（该类用户免登拿不到业务系统角色，须走账密登录改密） */
  mustChangePassword?: boolean
}

/**
 * 钉钉免登：授权码换系统 token。
 * 失败原因（授权码无效、dd_user_id 未绑定、默认密码用户等）以 Error.message 抛出。
 */
export async function dingTalkLogin(): Promise<string> {
  const corpId = resolveCorpId()
  if (!corpId) {
    throw new Error('钉钉企业 corpId 未配置')
  }

  // requestAuthCode 无需 dd.config 鉴权（官方类型注明），授权码 5 分钟有效且一次性
  const { code } = await dd.runtime.permission.requestAuthCode({ corpId })

  const resp = await axios.get<AuthResult<DingTalkLoginUserVo>>(
    `${AUTH_API_BASE}/api/dingtalk/getUserInfo`,
    { params: { code }, timeout: 10000 }
  )
  const body = resp.data
  if (body?.code !== 200 || !body.data?.token) {
    throw new Error(body?.message || '钉钉免登失败')
  }
  if (body.data.mustChangePassword) {
    throw new Error('账号仍在使用初始默认密码，请用账号密码登录后修改密码再使用钉钉免登')
  }
  return body.data.token
}
