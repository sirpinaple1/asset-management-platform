/** 搜索到的用户选项（B1 GET /v1/users records 元素） */
export interface UserSearchOption {
  id: number
  /** 工号，如 SK9802 */
  username?: string
  /** 姓名 */
  name: string
  /** 部门（展示用，可选） */
  dept?: string
}

/** GET /v1/users 分页响应 */
export interface UserSearchPage {
  records: UserSearchOption[]
  total: number
  page: number
  size: number
}
