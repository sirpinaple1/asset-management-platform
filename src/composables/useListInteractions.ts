import { onBeforeUnmount, onMounted, reactive, ref, watch, type ComputedRef, type Ref } from 'vue'
import type { InputInstance, TableInstance } from 'element-plus'

export interface ContextMenuItem {
  key: string
  label: string
  danger?: boolean
}

/** 表格行右键菜单固定项 */
export const ROW_CTX_MENU_ITEMS: ContextMenuItem[] = [
  { key: 'edit', label: '编辑' },
  { key: 'copy', label: '复制信息' },
  { key: 'delete', label: '删除', danger: true },
]

/** 输入类目标判断（快捷键在输入场景下不劫持） */
const isTypingTarget = (target: EventTarget | null) => {
  const el = target as HTMLElement | null
  if (!el) return false
  return el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable
}

interface Options<T extends { id: number }> {
  /** 当前页展示行（方向键导航作用域） */
  pageRows: Ref<T[]> | ComputedRef<T[]>
  /** 已勾选行（Delete 批量删除） */
  selectedRows: Ref<T[]>
  /** 弹窗打开时挂起页面级快捷键 */
  isModalOpen: Ref<boolean>
  tableRef: Ref<TableInstance | undefined>
  searchInputRef: Ref<InputInstance | undefined>
  onEditRow: (row: T) => void
  onCopyRow: (row: T) => void
  onDeleteRow: (row: T) => void
  onBatchDelete: () => void
  /** Enter 打开行（缺省回落到编辑） */
  onOpenRow?: (row: T) => void
  /** Space 切换行勾选（页面有选择列时传入） */
  onSpaceRow?: (row: T) => void
  /** 自定义右键菜单项（缺省：编辑/复制信息/删除） */
  ctxMenuItems?: ContextMenuItem[]
  /** 内置 key（edit/copy/delete）之外的自定义菜单项回调 */
  onCtxAction?: (key: string, row: T) => void
}

/**
 * 列表页输入与触发交互：
 * - 鼠标：行右键菜单（编辑/复制/删除）、双击编辑（由页面绑定 @row-dblclick）
 * - 键盘：Ctrl+F 聚焦搜索、Delete 删除选中行、表格内方向键移动当前行、Enter 编辑当前行、Esc 关闭右键菜单
 */
export function useListInteractions<T extends { id: number }>(options: Options<T>) {
  const { pageRows, selectedRows, isModalOpen, tableRef, searchInputRef } = options

  /* ---------------- 右键菜单 ---------------- */
  const ctxMenu = reactive({ visible: false, x: 0, y: 0 })
  const ctxRow = ref<T>()

  const closeCtxMenu = () => {
    ctxMenu.visible = false
  }

  const onRowContextmenu = (row: T, _column: unknown, e: MouseEvent) => {
    e.preventDefault()
    ctxRow.value = row
    const items = options.ctxMenuItems ?? ROW_CTX_MENU_ITEMS
    // 靠近视口边缘时回退，避免菜单溢出屏幕
    ctxMenu.x = Math.min(e.clientX, window.innerWidth - 180)
    ctxMenu.y = Math.min(e.clientY, window.innerHeight - items.length * 32 - 16)
    ctxMenu.visible = true
  }

  const onCtxMenuSelect = (key: string) => {
    const row = ctxRow.value
    closeCtxMenu()
    if (!row) return
    if (key === 'edit') options.onEditRow(row)
    else if (key === 'copy') options.onCopyRow(row)
    else if (key === 'delete') options.onDeleteRow(row)
    else options.onCtxAction?.(key, row)
  }

  /* ---------------- 表格键盘导航（方向键 + Enter） ---------------- */
  let currentRowIndex = -1

  const onCurrentChange = (row: T | null) => {
    currentRowIndex = row ? pageRows.value.findIndex((r) => r.id === row.id) : -1
  }

  const onTableKeydown = (e: KeyboardEvent) => {
    if (!pageRows.value.length) return
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      const delta = e.key === 'ArrowDown' ? 1 : -1
      const next = Math.min(Math.max(currentRowIndex + delta, 0), pageRows.value.length - 1)
      currentRowIndex = next
      tableRef.value?.setCurrentRow(pageRows.value[next])
    } else if (e.key === 'Enter') {
      const row = pageRows.value[currentRowIndex]
      if (row) {
        e.preventDefault()
        ;(options.onOpenRow ?? options.onEditRow)(row)
      }
    } else if (e.key === ' ' || e.code === 'Space') {
      /* Space：切换当前行勾选（多选批量场景，参考 Airtable/Notion DB） */
      const row = pageRows.value[currentRowIndex]
      if (row && options.onSpaceRow) {
        e.preventDefault()
        options.onSpaceRow(row)
      }
    }
  }

  /* ---------------- 页面级全局快捷键 ---------------- */
  const onGlobalKeydown = (e: KeyboardEvent) => {
    if (e.key === 'Escape') closeCtxMenu()
    if (isModalOpen.value) return

    const mod = e.ctrlKey || e.metaKey
    // Ctrl/Cmd + F：聚焦搜索框
    if (mod && e.key.toLowerCase() === 'f') {
      e.preventDefault()
      searchInputRef.value?.focus()
      return
    }
    // Ctrl/Cmd + S：列表页无保存对象，仅拦截浏览器默认"保存网页"
    if (mod && e.key.toLowerCase() === 's') {
      e.preventDefault()
      return
    }
    // Delete：删除选中行（输入场景 / 确认框已打开时除外）
    if (
      e.key === 'Delete' &&
      !isTypingTarget(e.target) &&
      selectedRows.value.length &&
      !document.querySelector('.el-overlay.is-message-box')
    ) {
      e.preventDefault()
      options.onBatchDelete()
    }
  }

  const onDocMouseDown = (e: MouseEvent) => {
    const el = e.target as HTMLElement | null
    if (ctxMenu.visible && el && !el.closest('.ctx-menu')) closeCtxMenu()
  }

  onMounted(() => {
    window.addEventListener('keydown', onGlobalKeydown)
    document.addEventListener('mousedown', onDocMouseDown)
    window.addEventListener('scroll', closeCtxMenu, true)
    window.addEventListener('resize', closeCtxMenu)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('keydown', onGlobalKeydown)
    document.removeEventListener('mousedown', onDocMouseDown)
    window.removeEventListener('scroll', closeCtxMenu, true)
    window.removeEventListener('resize', closeCtxMenu)
  })

  /* 翻页/过滤后当前行失效 */
  watch(pageRows, () => {
    currentRowIndex = -1
    tableRef.value?.setCurrentRow(undefined)
  })

  return {
    ctxMenu,
    ctxMenuItems: options.ctxMenuItems ?? ROW_CTX_MENU_ITEMS,
    onRowContextmenu,
    onCtxMenuSelect,
    onTableKeydown,
    onCurrentChange,
  }
}
