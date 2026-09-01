<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useApprovalStore } from '@/stores/approval'
import { useTabsStore } from '@/stores/tabs'
import TabBar from '@/components/TabBar.vue'
import NotificationBell from '@/components/NotificationBell.vue'
import CommandPalette from '@/components/CommandPalette.vue'
import {
  FRONTEND_VERSION,
  RELEASE_DATE,
  CHANGELOG,
  CREDIT,
  fetchBackendVersion,
  type BackendVersion,
} from '@/version'

const route = useRoute()
const userStore = useUserStore()
const approvalStore = useApprovalStore()
const tabsStore = useTabsStore()

const commandPaletteRef = ref<InstanceType<typeof CommandPalette>>()

/* 多页签工作台：路由变化登记页签（fullPath 含 query），keep-alive 缓存已打开页签 */
watch(
  () => route.fullPath,
  () => {
    if (route.name && route.meta.title) {
      tabsStore.addTab({
        fullPath: route.fullPath,
        path: route.path,
        name: String(route.name),
        title: String(route.meta.title),
      })
    }
  },
  { immediate: true },
)

/** keep-alive 缓存集合：已打开页签的组件 name（组件 defineOptions name 与路由 name 一致） */
const cachedViews = computed(() => tabsStore.tabs.map((t) => t.name))

const activeNav = computed(() => route.name)
const user = computed(() => userStore.me)

/** 资产管理模块（资产列表 + 基础数据 + 领用借用/调拨/变更/盘点单据子页面 + 审批中心）→ 显示二级子侧边栏 */
const isAssetModule = computed(() =>
  ['assets-', 'basedata-', 'receipts-', 'transfers-', 'changes-', 'stocktakes-', 'approvals-'].some(
    (p) => String(route.name || '').startsWith(p),
  ),
)

/** 资产管理图标激活态：审批中心页面仅亮“审批中心”，不联动“资产管理” */
const isAssetNavActive = computed(() =>
  ['assets-', 'basedata-', 'receipts-', 'transfers-', 'changes-', 'stocktakes-'].some((p) =>
    String(route.name || '').startsWith(p),
  ),
)

/** 审批中心路由：进入时折叠“资产功能/基础设置”分组（入口保留，可手动展开跳转） */
const isApprovalsRoute = computed(() => String(route.name || '').startsWith('approvals-'))

/** 审批中心子菜单激活态：/approvals?tab=xxx（缺省 tab=todo） */
const approvalsTabActive = (key: string) =>
  route.path === '/approvals' && (String(route.query.tab || 'todo') === key)

/** 二级侧边栏：基础设置菜单（对齐原型：厂商/供应商/分类/位置/型号 + 公司主体） */
const basedataMenus = computed(() => {
  const base = [
    { path: '/basedata/companies', title: '公司主体' },
    { path: '/basedata/manufacturers', title: '厂商管理' },
    { path: '/basedata/suppliers', title: '供应商管理' },
    { path: '/basedata/categories', title: '分类管理' },
    { path: '/basedata/locations', title: '位置管理' },
    { path: '/basedata/models', title: '型号管理' },
    { path: '/basedata/migration', title: '数据迁移' },
  ]
  /* 组织架构管理（审批链配置）仅超管可见 */
  if (userStore.me?.roles?.includes('systemAdmin')) {
    base.push({ path: '/basedata/approval-configs', title: '组织架构管理' })
  }
  return base
})

/** 分组折叠状态（localStorage 持久化，刷新不丢） */
const COLLAPSE_KEY = 'asset.sidebar.collapsed'
const collapsed = ref<Record<string, boolean>>({ assetFn: false, basedata: false })
try {
  const saved = localStorage.getItem(COLLAPSE_KEY)
  if (saved) collapsed.value = { ...collapsed.value, ...JSON.parse(saved) }
} catch {
  /* 忽略损坏的本地存储 */
}
const toggleSection = (key: string) => {
  collapsed.value[key] = !collapsed.value[key]
  localStorage.setItem(COLLAPSE_KEY, JSON.stringify(collapsed.value))
}

/** 进入审批中心：折叠“资产功能/基础设置”分组（保留入口，可手动展开跳转）；离开时恢复用户原折叠偏好 */
let collapsedSnapshot: Record<string, boolean> | null = null
watch(
  isApprovalsRoute,
  (inApprovals) => {
    if (inApprovals) {
      collapsedSnapshot = { ...collapsed.value }
      collapsed.value.assetFn = true
      collapsed.value.basedata = true
    } else if (collapsedSnapshot) {
      collapsed.value = collapsedSnapshot
      collapsedSnapshot = null
      localStorage.setItem(COLLAPSE_KEY, JSON.stringify(collapsed.value))
    }
  },
  { immediate: true },
)

/** 占位菜单点击提示 */
const comingSoon = () => {
  import('element-plus').then(({ ElMessage }) => ElMessage.info('功能建设中，敬请期待'))
}

/** 版本信息面板（左下角帮助问号）：前端版本本地直渲，后端版本打开时拉一次 */
const versionVisible = ref(false)
const backendVersion = ref<BackendVersion | null>(null)
const backendLoading = ref(false)

const openVersionPanel = () => {
  versionVisible.value = true
  backendLoading.value = true
  fetchBackendVersion()
    .then((data) => {
      backendVersion.value = data
    })
    .finally(() => {
      backendLoading.value = false
    })
}

/** 顶栏图标色由 .nav-item 的 color（currentColor）控制 */
</script>

<template>
  <div class="app">
    <!-- 顶部导航栏：公司信息 + 用户区 -->
    <header class="topbar">
      <div class="topbar-logo">
        <div class="logo-image">森</div>
        <div class="logo-texts">
          <div class="logo-text">森科五金（深圳）有限公司</div>
          <div class="logo-subtitle">Tritree Metal (Shenzhen) Co., Ltd</div>
        </div>
      </div>

      <div class="user-area">
        <button class="cmdk-trigger" type="button" title="命令面板（Ctrl+K）" @click="commandPaletteRef?.open()">
          <svg width="14" height="14" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="9" cy="9" r="6" stroke="currentColor" stroke-width="1.5" />
            <line x1="13.5" y1="13.5" x2="17" y2="17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          </svg>
          <span>搜索</span>
          <kbd>⌘K</kbd>
        </button>
        <NotificationBell />
        <el-dropdown trigger="click">
          <div class="user-trigger">
            <span class="user-name">{{ userStore.displayName || '未登录' }}</span>
            <div class="user-avatar">{{ userStore.avatarChar }}</div>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <div class="user-panel">
                <div class="user-panel-name">{{ user?.name || '-' }}</div>
                <div class="user-panel-row"><span>用户名</span><span>{{ user?.username || '-' }}</span></div>
                <div class="user-panel-row"><span>部门</span><span>{{ user?.dept || '-' }}</span></div>
                <div class="user-panel-row"><span>岗位</span><span>{{ user?.job || '-' }}</span></div>
                <div class="user-panel-row">
                  <span>角色</span>
                  <span>{{ user?.roles?.length ? user.roles.join('、') : '未分配' }}</span>
                </div>
              </div>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="main">
      <!-- 全局侧边栏：模块级图标导航 -->
      <aside class="sidebar">
        <el-tooltip content="工作台" placement="right" :show-after="300">
          <router-link to="/dashboard" class="nav-item" :class="{ active: activeNav === 'dashboard' }">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="2" y="8" width="4" height="9" rx="1" stroke="currentColor" stroke-width="1.5" />
              <rect x="8" y="5" width="4" height="12" rx="1" stroke="currentColor" stroke-width="1.5" />
              <rect x="14" y="10" width="4" height="7" rx="1" stroke="currentColor" stroke-width="1.5" />
            </svg>
          </router-link>
        </el-tooltip>

        <el-tooltip content="审批中心" placement="right" :show-after="300">
          <router-link to="/approvals" class="nav-item" :class="{ active: activeNav === 'approvals-center' }">
            <span v-if="approvalStore.todoCount > 0" class="red-dot"></span>
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M3 5L6 8L9 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
              <line x1="11" y1="6.5" x2="17" y2="6.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
              <path d="M3 10L6 13L9 10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
              <line x1="11" y1="11.5" x2="17" y2="11.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
              <circle cx="5" cy="16" r="1.2" stroke="currentColor" stroke-width="1.5" />
              <line x1="11" y1="16" x2="17" y2="16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            </svg>
          </router-link>
        </el-tooltip>

        <el-tooltip content="资产管理" placement="right" :show-after="300">
          <router-link to="/assets" class="nav-item" :class="{ active: isAssetNavActive }">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="3" y="6" width="14" height="11" rx="1.5" stroke="currentColor" stroke-width="1.5" />
              <path d="M3 9.5L10 6L17 9.5" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
              <line x1="10" y1="6" x2="10" y2="17" stroke="currentColor" stroke-width="1.5" />
              <rect x="7" y="3" width="6" height="3" rx="0.5" fill="currentColor" />
            </svg>
          </router-link>
        </el-tooltip>

        <el-tooltip content="库存管理（待接入）" placement="right" :show-after="300">
          <div class="nav-item" @click="comingSoon">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="3" y="11" width="6" height="6" rx="1" style="stroke: var(--color-text-4)" stroke-width="1.5" />
              <rect x="11" y="11" width="6" height="6" rx="1" style="stroke: var(--color-text-4)" stroke-width="1.5" />
              <rect x="7" y="3" width="6" height="6" rx="1" style="stroke: var(--color-text-4)" stroke-width="1.5" />
            </svg>
          </div>
        </el-tooltip>

        <div class="sidebar-spacer"></div>

        <div class="bottom-nav">
          <NotificationBell class="bottom-nav-bell" />
          <el-tooltip content="版本信息" placement="right" :show-after="300">
            <div class="bottom-nav-item" @click="openVersionPanel">
              <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
                <circle cx="10" cy="10" r="7.5" style="stroke: var(--color-text-4)" stroke-width="1.5" />
                <path d="M8 8C8 6.89543 8.89543 6 10 6C11.1046 6 12 6.89543 12 8C12 8.73638 11.5977 9.37205 11 9.7324V11" style="stroke: var(--color-text-4)" stroke-width="1.5" stroke-linecap="round" />
                <circle cx="10" cy="13.5" r="0.75" style="fill: var(--color-text-4)" />
              </svg>
            </div>
          </el-tooltip>
        </div>
      </aside>

      <!-- 二级子侧边栏：资产管理模块菜单 -->
      <aside v-if="isAssetModule" class="sub-sidebar">
        <div class="sidebar-header">
          <h1 class="sidebar-title">资产管理</h1>
        </div>

        <nav class="sidebar-menu">
          <router-link to="/approvals?tab=todo" class="menu-item" :class="{ active: approvalsTabActive('todo') }">
            <span class="menu-icon">
              <svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M3 5L6 8L9 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" /><line x1="11" y1="6.5" x2="17" y2="6.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" /><path d="M3 10L6 13L9 10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" /><line x1="11" y1="11.5" x2="17" y2="11.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" /></svg>
            </span>
            <span>我的待办</span>
          </router-link>
          <router-link to="/approvals?tab=mine" class="menu-item" :class="{ active: approvalsTabActive('mine') }">
            <span class="menu-icon">
              <svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M6 14L10 18L14 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" /><path d="M6 6L10 2L14 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </span>
            <span>我发起的</span>
          </router-link>
          <router-link to="/approvals?tab=handled" class="menu-item" :class="{ active: approvalsTabActive('handled') }">
            <span class="menu-icon">
              <svg width="18" height="18" viewBox="0 0 20 20" fill="none"><rect x="4" y="4" width="12" height="12" rx="2" stroke="currentColor" stroke-width="1.5" /><path d="M7.5 10L9.5 12L12.5 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </span>
            <span>我处理的</span>
          </router-link>
          <a class="menu-item" @click="comingSoon">
            <span class="menu-icon">
              <svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M3 6C3 4.34315 4.34315 3 6 3H14C15.6569 3 17 4.34315 17 6V12C17 13.6569 15.6569 15 14 15H9L6 17.5V15H6C4.34315 15 3 13.6569 3 12V6Z" stroke="currentColor" stroke-width="1.5" /></svg>
            </span>
            <span>抄送我的</span>
          </a>
        </nav>

        <div class="menu-section">
          <div class="section-title-static">常用入口</div>
          <router-link to="/approvals?tab=todo" class="menu-item" :class="{ active: approvalsTabActive('todo') }">
            <span>待处理工单</span>
          </router-link>
          <router-link to="/transfers?tab=PENDING" class="menu-item" :class="{ active: route.path === '/transfers' && route.query.tab === 'PENDING' }">
            <span>待确认调拨单</span>
          </router-link>
          <router-link to="/receipts/receive?tab=PENDING" class="menu-item" :class="{ active: route.path === '/receipts/receive' && route.query.tab === 'PENDING' }">
            <span>待处理员工申请</span>
          </router-link>
        </div>

        <div class="menu-section">
          <button class="section-title" type="button" @click="toggleSection('assetFn')">
            <span>资产功能</span>
            <span class="collapse-icon" :class="{ collapsed: collapsed.assetFn }">▸</span>
          </button>
          <div v-show="!collapsed.assetFn" class="section-content">
            <router-link to="/assets" class="menu-item" :class="{ active: route.path === '/assets' }">
              <span>资产列表</span>
            </router-link>
            <router-link to="/transfers" class="menu-item" :class="{ active: route.path === '/transfers' }">
              <span>资产调拨</span>
            </router-link>
            <router-link to="/receipts/receive" class="menu-item" :class="{ active: route.path === '/receipts/receive' }">
              <span>领用&退库</span>
            </router-link>
            <router-link to="/receipts/borrow" class="menu-item" :class="{ active: route.path === '/receipts/borrow' }">
              <span>借用&归还</span>
            </router-link>
            <router-link to="/changes" class="menu-item" :class="{ active: route.path === '/changes' }">
              <span>实物信息变更</span>
            </router-link>
            <router-link to="/stocktakes" class="menu-item" :class="{ active: route.path === '/stocktakes' }">
              <span>盘点管理</span>
            </router-link>
            <a class="menu-item" @click="comingSoon"><span>分析报表</span></a>
          </div>
        </div>

        <div class="menu-section">
          <button class="section-title" type="button" @click="toggleSection('basedata')">
            <span>基础设置</span>
            <span class="collapse-icon" :class="{ collapsed: collapsed.basedata }">▸</span>
          </button>
          <div v-show="!collapsed.basedata" class="section-content">
            <router-link
              v-for="menu in basedataMenus"
              :key="menu.path"
              :to="menu.path"
              class="menu-item"
              :class="{ active: route.path === menu.path }"
            >
              <span>{{ menu.title }}</span>
            </router-link>
          </div>
        </div>
      </aside>

      <div class="content">
        <!-- 多页签工作台 + 面包屑 -->
        <TabBar />
        <div class="content-scroll">
          <router-view v-slot="{ Component }">
            <keep-alive :include="cachedViews">
              <component :is="Component" />
            </keep-alive>
          </router-view>
        </div>
        <!-- 滚轮回顶部 -->
        <el-backtop target=".content-scroll" :right="32" :bottom="32" />
      </div>
    </div>

    <!-- 命令面板（⌘K / Ctrl+K 全局呼出） -->
    <CommandPalette ref="commandPaletteRef" />

    <!-- 版本信息面板（左下角帮助问号呼出，贴左侧滑出） -->
    <el-drawer
      v-model="versionVisible"
      title="版本信息"
      direction="ltr"
      size="360px"
      :append-to-body="true"
    >
      <div class="version-panel">
        <!-- 前端版本：本地常量直渲 -->
        <section class="version-block">
          <div class="version-block-head">
            <span class="version-tag">前端版本</span>
            <span class="version-num">v{{ FRONTEND_VERSION }}</span>
          </div>
          <div class="version-date">{{ RELEASE_DATE }}</div>
          <ul class="changelog-list">
            <li v-for="(item, i) in CHANGELOG" :key="i">{{ item }}</li>
          </ul>
        </section>

        <el-divider class="version-divider" />

        <!-- 后端版本：打开面板时拉取，失败降级占位 -->
        <section class="version-block">
          <div class="version-block-head">
            <span class="version-tag">后端版本</span>
            <span v-if="backendVersion" class="version-num">v{{ backendVersion.version }}</span>
            <span v-else-if="backendLoading" class="version-num version-loading">获取中…</span>
            <span v-else class="version-num version-fail">后端版本获取失败</span>
          </div>
          <template v-if="backendVersion">
            <div class="version-date">{{ backendVersion.releaseDate }}</div>
            <ul v-if="backendVersion.changelog?.length" class="changelog-list">
              <li v-for="(item, i) in backendVersion.changelog" :key="i">{{ item }}</li>
            </ul>
          </template>
        </section>

        <div class="version-credit">{{ CREDIT }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.app {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-1);
  min-width: 1200px;
}

/* 顶部导航栏 */
.topbar {
  height: 56px;
  background: var(--color-bg-2);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  flex-shrink: 0;
}

.topbar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-image {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, #00d4aa 0%, #00b894 100%);
  color: var(--color-text-inverse);
  font-size: var(--text-lg);
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-texts {
  display: flex;
  flex-direction: column;
}

.logo-text {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text-1);
  white-space: nowrap;
  line-height: 22px;
}

.logo-subtitle {
  font-size: 11px;
  color: var(--color-text-2);
  font-weight: 400;
  white-space: nowrap;
  line-height: 16px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 命令面板入口按钮（⌘K） */
.cmdk-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-2);
  color: var(--color-text-3);
  font-size: var(--text-sm);
  cursor: pointer;
}

.cmdk-trigger:hover {
  border-color: var(--color-primary);
  color: var(--color-text-2);
}

.cmdk-trigger kbd {
  font-family: inherit;
  font-size: var(--text-xs);
  color: var(--color-text-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 1px 4px;
  line-height: 1.4;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.user-name {
  font-size: var(--text-base);
  font-weight: 400;
  color: var(--color-text-1);
  line-height: 22px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-inverse);
  font-size: var(--text-base);
  font-weight: 500;
  line-height: 22px;
}

.user-panel {
  padding: 8px 16px;
  min-width: 240px;
}

.user-panel-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-border-light);
  margin-bottom: 8px;
}

.user-panel-row {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  font-size: var(--text-sm);
  line-height: 24px;
  color: var(--color-text-2);
}

.user-panel-row span:first-child {
  color: var(--color-text-3);
}

/* 主区三栏布局 */
.main {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* 全局侧边栏 */
.sidebar {
  width: 64px;
  background: var(--color-bg-2);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 16px;
  gap: 8px;
  flex-shrink: 0;
}

.nav-item {
  width: 64px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  text-decoration: none;
  transition: background 0.15s ease;
  border-radius: 0;
  position: relative;
  color: var(--color-text-4);
}

.nav-item:hover {
  background: var(--color-bg-1);
  color: var(--color-text-3);
}

.nav-item.active {
  background: var(--color-primary);
  color: var(--color-text-inverse);
}

.sidebar-spacer {
  flex: 1;
  width: 100%;
  min-height: 0;
}

.bottom-nav {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding-bottom: 16px;
}

.bottom-nav-bell {
  width: 64px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bottom-nav-item {
  width: 64px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
}

.red-dot {
  position: absolute;
  top: 8px;
  right: 18px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-error);
}

/* 二级子侧边栏 */
.sub-sidebar {
  width: 240px;
  background: var(--color-bg-2);
  box-shadow: 1px 0 3px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow-y: auto;
}

.sidebar-header {
  height: 64px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--color-bg-3);
  flex-shrink: 0;
}

.sidebar-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-1);
  margin: 0;
}

.sidebar-menu {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex-shrink: 0;
}

.menu-section {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.section-title {
  height: 28px;
  padding: 0 8px;
  border: none;
  background: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--color-text-3);
  cursor: pointer;
  user-select: none;
  width: 100%;
}

.section-title:hover {
  color: var(--color-text-2);
}

.section-title-static {
  height: 28px;
  padding: 0 8px 4px;
  display: flex;
  align-items: center;
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--color-text-3);
  user-select: none;
}

.collapse-icon {
  font-size: var(--text-xs);
  transition: transform 0.2s ease;
  color: var(--color-text-3);
}

.collapse-icon.collapsed {
  transform: rotate(-90deg);
}

.section-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.menu-item {
  height: 40px;
  padding: 0 8px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  font-size: var(--text-base);
  color: var(--color-text-2);
  transition: background 0.15s ease;
  text-decoration: none;
}

.menu-item:hover:not(.active) {
  background: var(--color-bg-1);
}

.menu-item.active {
  background: var(--color-primary-bg);
  color: var(--color-primary);
  font-weight: 600;
}

.menu-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* 内容区：页签栏 + 滚动容器 */
.content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--color-bg-1);
}

.content-scroll {
  flex: 1;
  overflow: auto;
  padding: 24px;
  min-height: 0;
}

/* 版本信息面板（el-drawer 贴左侧滑出） */
.version-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.version-block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.version-tag {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-1);
}

.version-num {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-primary);
}

.version-num.version-loading {
  color: var(--color-text-4);
}

.version-num.version-fail {
  color: var(--color-error-text);
  font-weight: 400;
}

.version-date {
  margin-top: 4px;
  font-size: var(--text-xs);
  color: var(--color-text-3);
}

.changelog-list {
  margin: 8px 0 0;
  padding-left: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.changelog-list li {
  position: relative;
  padding-left: 14px;
  font-size: var(--text-sm);
  line-height: 20px;
  color: var(--color-text-2);
}

.changelog-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 7px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-border);
}

.version-divider {
  margin: 16px 0;
}

.version-credit {
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid var(--color-border-light);
  font-size: var(--text-xs);
  color: var(--color-text-4);
  text-align: center;
}
</style>
