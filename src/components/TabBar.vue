<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useTabsStore, AFFIX_PATH } from '@/stores/tabs'

const route = useRoute()
const router = useRouter()
const tabsStore = useTabsStore()

/** 当前激活页签（fullPath 全匹配） */
const activePath = computed(() => route.fullPath)

/** 面包屑：模块 / 页面 */
const breadcrumb = computed(() => {
  if (route.name === 'dashboard') return '工作台'
  const module = String(route.name || '').startsWith('basedata-') ? '资产管理' : ''
  return module ? `${module} / ${route.meta.title}` : String(route.meta.title || '')
})

const handleClick = (fullPath: string) => {
  if (fullPath !== activePath.value) router.push(fullPath)
}

/** 关闭页签（脏页签先确认）；关闭当前页则跳相邻 */
const handleClose = async (fullPath: string) => {
  if (fullPath === AFFIX_PATH) return
  if (tabsStore.dirtyPaths.has(fullPath)) {
    try {
      await ElMessageBox.confirm('该页面有未保存的内容，确认关闭吗？', '关闭页签', {
        type: 'warning',
        confirmButtonText: '关闭',
        cancelButtonText: '取消',
      })
    } catch {
      return /* 用户取消 */
    }
  }
  const idx = tabsStore.tabs.findIndex((t) => t.fullPath === fullPath)
  tabsStore.removeTab(fullPath)
  if (fullPath === activePath.value) {
    const next = tabsStore.tabs[idx] ?? tabsStore.tabs[idx - 1]
    if (next) router.push(next.fullPath)
    else router.push(AFFIX_PATH)
  }
}

const handleCloseOthers = () => tabsStore.closeOthers(activePath.value)
const handleCloseRight = () => tabsStore.closeRight(activePath.value)

/** 页签右键：关闭其他 / 关闭右侧 */
const onTabContextmenu = (e: MouseEvent) => {
  e.preventDefault()
}
</script>

<template>
  <div class="tabbar">
    <div class="tabs" @contextmenu.prevent="onTabContextmenu">
      <div
        v-for="tab in tabsStore.tabs"
        :key="tab.fullPath"
        class="tab"
        :class="{ active: tab.fullPath === activePath }"
        role="tab"
        :aria-selected="tab.fullPath === activePath"
        tabindex="0"
        @click="handleClick(tab.fullPath)"
        @keydown.enter="handleClick(tab.fullPath)"
      >
        <span class="tab-title">{{ tab.title }}</span>
        <span
          v-if="tabsStore.dirtyPaths.has(tab.fullPath)"
          class="tab-dirty"
          title="有未保存内容"
        ></span>
        <button
          v-if="tab.fullPath !== AFFIX_PATH"
          type="button"
          class="tab-close"
          aria-label="关闭页签"
          @click.stop="handleClose(tab.fullPath)"
        >×</button>
      </div>
    </div>

    <div class="tabbar-right">
      <span class="breadcrumb">{{ breadcrumb }}</span>
      <el-dropdown trigger="click" @command="(cmd: string) => cmd === 'others' ? handleCloseOthers() : handleCloseRight()">
        <button type="button" class="more-btn" aria-label="更多操作">⋯</button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="others">关闭其他页签</el-dropdown-item>
            <el-dropdown-item command="right">关闭右侧页签</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped>
.tabbar {
  height: 40px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  flex-shrink: 0;
}

.tabs {
  display: flex;
  align-items: stretch;
  overflow-x: auto;
  scrollbar-width: none;
}

.tabs::-webkit-scrollbar {
  display: none;
}

.tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
  white-space: nowrap;
  border-right: 1px solid #f3f4f6;
  position: relative;
  user-select: none;
}

.tab:hover {
  background: #f9fafb;
}

.tab.active {
  color: #165dff;
  font-weight: 600;
  background: #f0f5ff;
}

.tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: #165dff;
}

.tab-dirty {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff7d00;
}

.tab-close {
  width: 18px;
  height: 18px;
  border: none;
  border-radius: 4px;
  background: none;
  color: #9ca3af;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.tab-close:hover {
  background: #e5e7eb;
  color: #4b5563;
}

.tab:not(.active) .tab-close {
  visibility: hidden;
}

.tab:hover .tab-close,
.tab.active .tab-close {
  visibility: visible;
}

.tabbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  flex-shrink: 0;
}

.breadcrumb {
  font-size: 12px;
  color: #86909c;
  white-space: nowrap;
}

.more-btn {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 4px;
  background: none;
  color: #6b7280;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.more-btn:hover {
  background: #f3f4f6;
  color: #111827;
}
</style>
