import type { Directive } from 'vue'
import { hasPermission } from '@/utils/permission'

/**
 * v-permission 按钮级权限指令。
 *
 * 用法：
 *   <el-button v-permission="'asset:create'">新增</el-button>
 *   <el-button v-permission="['asset:edit', 'asset:admin']">编辑</el-button>（数组=任一满足即可）
 *
 * 无权限时直接移除 DOM 元素（比 display:none 更彻底）。
 * 注意：规则为静态配置（非响应式），修改 PERMISSION_RULES 后需刷新页面生效；
 * 指令仅在 mounted 时判定一次，元素一旦移除不会因后续更新而恢复。
 */
export const vPermission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const points = Array.isArray(binding.value) ? binding.value : [binding.value]
    const allowed = points.length === 0 || points.some((p) => hasPermission(p))
    if (!allowed) {
      el.parentNode?.removeChild(el)
    }
  },
}
