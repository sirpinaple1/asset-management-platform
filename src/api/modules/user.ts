import { request } from '@/api/config/request'
import type { MeInfo } from '@/api/interface'

/** 当前登录用户信息（asset-backend GET /api/v1/me，鉴权全链路验证接口） */
export function fetchMe(): Promise<MeInfo> {
  return request<MeInfo>({ url: '/v1/me', method: 'get' })
}
