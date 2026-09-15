import { computed, ref, watch } from 'vue'

/** 可配置列定义 */
export interface ColumnDef {
  /** 唯一键（对应业务字段） */
  key: string
  label: string
  visible: boolean
}

/** 恢复默认 */
const cloneDefaults = (defaults: ColumnDef[]) => defaults.map((d) => ({ ...d }))

/**
 * 列配置：显隐 / 拖拽排序 / localStorage 记住偏好。
 * - 存储结构：{ version, columns }，version 不匹配时丢弃（列定义演进后自动回默认）
 * - 至少保留一列可见
 */
export function useColumnConfig(storageKey: string, defaults: ColumnDef[]) {
  const columns = ref<ColumnDef[]>(cloneDefaults(defaults))

  const load = () => {
    try {
      const raw = localStorage.getItem(storageKey)
      if (!raw) return
      const saved = JSON.parse(raw) as { version: number; columns: ColumnDef[] }
      if (!saved || !Array.isArray(saved.columns)) return
      /* 只认默认集合里存在的键，顺序以存储为准；新增列追加到末尾 */
      const defaultKeys = defaults.map((d) => d.key)
      const valid = saved.columns.filter((c) => defaultKeys.includes(c.key))
      const missing = defaults.filter((d) => !valid.some((c) => c.key === d.key))
      columns.value = [...valid, ...missing]
    } catch {
      /* 损坏的存储忽略，回默认 */
    }
  }
  load()

  watch(
    columns,
    () => {
      localStorage.setItem(storageKey, JSON.stringify({ version: 1, columns: columns.value }))
    },
    { deep: true },
  )

  /** 渲染顺序：仅可见列 */
  const visibleColumns = computed(() => columns.value.filter((c) => c.visible))

  const toggle = (key: string) => {
    const col = columns.value.find((c) => c.key === key)
    if (!col) return
    /* 至少保留一列，避免表格空列 */
    if (col.visible && columns.value.filter((c) => c.visible).length <= 1) return
    col.visible = !col.visible
  }

  const reset = () => {
    columns.value = cloneDefaults(defaults)
  }

  /** 拖拽排序：把 from 移动到 to 的位置 */
  const move = (from: number, to: number) => {
    if (from === to || from < 0 || to < 0 || from >= columns.value.length || to >= columns.value.length) return
    const next = [...columns.value]
    const [item] = next.splice(from, 1)
    next.splice(to, 0, item)
    columns.value = next
  }

  return { columns, visibleColumns, toggle, reset, move }
}
