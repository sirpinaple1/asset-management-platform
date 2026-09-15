import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限控制（P0 基建）。
 *
 * 设计约定：
 * - 权限点命名 `模块:动作`，如 asset:create / asset:edit / asset:discard / asset:export / asset:label-print
 * - PERMISSION_RULES 未配置的权限点 = 对全部用户开放（当前阶段所有权限点默认放开）
 * - 需要收紧时在 PERMISSION_RULES 中补一条规则即可，无需改动页面代码
 * - systemAdmin 超管始终放行；真实拦截以资产后端接口鉴权为准，此处仅控制前端可见性
 *
 * 使用方式：
 * - 模板按钮：<el-button v-permission="'asset:create'">新增</el-button>
 * - 脚本判断：if (hasPermission('asset:discard')) { ... }
 */

export const SUPER_ROLE = 'systemAdmin'

export interface PermissionRule {
  /** 允许访问的角色名（/api/v1/me 返回 roles），任一命中即放行 */
  roles?: string[]
  /** 允许访问的后端权限码（/api/v1/me 返回 permissions），任一命中即放行 */
  codes?: string[]
}

/**
 * 权限点规则表。
 * 当前全部留空 = 全用户可视；示例（需要收紧时取消注释）：
 *   'asset:create': { roles: ['asset-资产管理员'] },
 *   'asset:edit': { roles: ['asset-资产管理员'] },
 *   'asset:discard': { roles: ['asset-资产管理员'] },
 *   'asset:export': {},
 */
export const PERMISSION_RULES: Record<string, PermissionRule> = {}

/** 判断当前用户是否具备某权限点（未配置规则 / 用户信息未加载 / 超管 → 放行） */
export function hasPermission(point: string): boolean {
  const rule = PERMISSION_RULES[point]
  if (!rule) return true

  const me = useUserStore().me
  if (!me) return true
  if (me.roles?.includes(SUPER_ROLE)) return true
  if (rule.roles?.length && rule.roles.some((r) => me.roles?.includes(r))) return true
  if (rule.codes?.length && rule.codes.some((c) => me.permissions?.includes(c))) return true
  return false
}
