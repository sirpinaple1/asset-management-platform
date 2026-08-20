/** 后端统一响应结构（与 asset-backend common.Result 对齐，code 语义同 comm_public_basic：200 成功） */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** 菜单/按钮节点（asset-backend SystemMenuDto） */
export interface SystemMenu {
  id: number
  name: string
  /** 权限编码（按钮级权限判断用） */
  code: string
  /** 前端路由路径 */
  path: string
  icon: string
  /** 权限类型：0=菜单 1=按钮 */
  type: number
  sortOrder: number
  children: SystemMenu[] | null
}

/** GET /api/v1/me 响应（asset-backend MeResp） */
export interface MeInfo {
  userId: number
  username: string
  name: string
  dept: string
  job: string
  /** asset 系统角色名 */
  roles: string[]
  /** asset 系统按钮权限码 */
  permissions: string[]
  /** asset 系统菜单树 */
  menus: SystemMenu[] | null
}
