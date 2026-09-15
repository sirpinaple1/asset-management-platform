import { request } from '@/api/config/request'
import type { MeInfo } from '@/api/interface'
import type { UserSearchPage } from '@/api/interface/user'

/** 当前登录用户信息（asset-backend GET /api/v1/me，鉴权全链路验证接口） */
export function fetchMe(): Promise<MeInfo> {
  return request<MeInfo>({ url: '/v1/me', method: 'get' })
}

/**
 * B1 定向待办：用户搜索（GET /api/v1/users），用于选人器搜索指定处理人、
 * 调拨负责人、变更使用人等。keyword 模糊匹配工号/姓名/部门。
 */
export const userApi = {
  searchUsers: (keyword: string, page = 1, size = 20) =>
    request<UserSearchPage>({
      url: '/v1/users',
      method: 'get',
      params: { keyword: keyword.trim() || undefined, page, size },
    }),

  fetchMe,
}
