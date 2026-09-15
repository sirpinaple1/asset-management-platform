import { ref } from 'vue'

/**
 * 表格密度切换（P1）：紧凑 / 默认 / 宽松，对应 el-table size。
 * 偏好按存储键隔离持久化（localStorage），各列表页独立记忆。
 */
export type TableDensity = 'compact' | 'default' | 'comfortable'

export const DENSITY_META: Record<TableDensity, { label: string; size: 'small' | 'default' | 'large' }> = {
  compact: { label: '紧凑', size: 'small' },
  default: { label: '默认', size: 'default' },
  comfortable: { label: '宽松', size: 'large' },
}

export function useTableDensity(storageKey: string) {
  const KEY = `asset.density.${storageKey}`

  const read = (): TableDensity => {
    const saved = localStorage.getItem(KEY)
    return saved === 'compact' || saved === 'comfortable' ? saved : 'default'
  }

  const density = ref<TableDensity>(read())

  /** 循环切换 紧凑 → 默认 → 宽松 */
  const cycleDensity = () => {
    const order: TableDensity[] = ['compact', 'default', 'comfortable']
    density.value = order[(order.indexOf(density.value) + 1) % order.length]
    localStorage.setItem(KEY, density.value)
  }

  return { density, cycleDensity }
}
