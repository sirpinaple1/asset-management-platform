import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

/** 页签项：fullPath 含 query（同名路由不同参数=不同页签）；name 与组件 name 一致（keep-alive include 用） */
export interface TabItem {
  fullPath: string
  path: string
  name: string
  title: string
}

const STORAGE_KEY = 'asset.tabs.visited'

/** 固定页签（不可关闭）：工作台 */
export const AFFIX_PATH = '/dashboard'

function loadTabs(): TabItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const list = JSON.parse(raw) as TabItem[]
      if (Array.isArray(list) && list.length) {
        /* 按 path 去重（防御历史版本 fullPath 键控留下的重复页签），保留最新一条 */
        const byPath = new Map<string, TabItem>()
        list.filter((t) => t?.fullPath && t?.path).forEach((t) => byPath.set(t.path, t))
        const unique = [...byPath.values()]
        if (!unique.some((t) => t.path === AFFIX_PATH)) {
          unique.unshift({ fullPath: AFFIX_PATH, path: AFFIX_PATH, name: 'dashboard', title: '工作台' })
        }
        return unique
      }
    }
  } catch {
    /* 忽略损坏的本地存储 */
  }
  return [{ fullPath: AFFIX_PATH, path: AFFIX_PATH, name: 'dashboard', title: '工作台' }]
}

/**
 * 多页签工作台状态：
 * - tabs：已打开页签集合（localStorage 持久化，刷新保持）
 * - dirtyPaths：有未保存内容的页签（关闭前需确认，由页面注册/注销）
 */
export const useTabsStore = defineStore('tabs', () => {
  const tabs = ref<TabItem[]>(loadTabs())
  const dirtyPaths = ref(new Set<string>())

  watch(tabs, (v) => localStorage.setItem(STORAGE_KEY, JSON.stringify(v)), { deep: true })

  /** 登记页签：按 path 键控（同一路由唯一页签），query 变化时原地更新 fullPath（搜索/分页不开新页签） */
  const addTab = (tab: TabItem) => {
    const idx = tabs.value.findIndex((t) => t.path === tab.path)
    if (idx >= 0) {
      const existing = tabs.value[idx]
      if (existing.fullPath !== tab.fullPath) {
        tabs.value.splice(idx, 1, tab)
        /* fullPath 变更时迁移脏标记 */
        if (dirtyPaths.value.has(existing.fullPath)) {
          dirtyPaths.value.delete(existing.fullPath)
          dirtyPaths.value.add(tab.fullPath)
        }
      }
    } else {
      tabs.value.push(tab)
    }
  }

  const removeTab = (fullPath: string) => {
    const idx = tabs.value.findIndex((t) => t.fullPath === fullPath)
    if (idx >= 0) tabs.value.splice(idx, 1)
    dirtyPaths.value.delete(fullPath)
  }

  /** 关闭其他（保留固定页签与当前） */
  const closeOthers = (keep: string) => {
    tabs.value = tabs.value.filter((t) => t.fullPath === keep || t.fullPath === AFFIX_PATH)
  }

  /** 关闭右侧 */
  const closeRight = (keep: string) => {
    const idx = tabs.value.findIndex((t) => t.fullPath === keep)
    if (idx >= 0) tabs.value = tabs.value.slice(0, idx + 1)
  }

  /** 页面声明/清除未保存标记（如弹窗打开中） */
  const setDirty = (fullPath: string, dirty: boolean) => {
    if (dirty) dirtyPaths.value.add(fullPath)
    else dirtyPaths.value.delete(fullPath)
  }

  return { tabs, dirtyPaths, addTab, removeTab, setDirty, closeOthers, closeRight }
})
