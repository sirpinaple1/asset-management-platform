<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useApprovalStore } from '@/stores/approval'
import { useTabsStore } from '@/stores/tabs'
import TabBar from '@/components/TabBar.vue'
import NotificationBell from '@/components/NotificationBell.vue'

const route = useRoute()
const userStore = useUserStore()
const approvalStore = useApprovalStore()
const tabsStore = useTabsStore()

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

/** 审批中心子菜单激活态：/approvals?tab=xxx（缺省 tab=todo） */
const approvalsTabActive = (key: string) =>
  route.path === '/approvals' && (String(route.query.tab || 'todo') === key)

/** 二级侧边栏：基础设置菜单（对齐原型：厂商/供应商/分类/位置/型号 + 公司主体） */
const basedataMenus = [
  { path: '/basedata/companies', title: '公司主体' },
  { path: '/basedata/manufacturers', title: '厂商管理' },
  { path: '/basedata/suppliers', title: '供应商管理' },
  { path: '/basedata/categories', title: '分类管理' },
  { path: '/basedata/locations', title: '位置管理' },
  { path: '/basedata/models', title: '型号管理' },
  { path: '/basedata/migration', title: '数据迁移' },
]

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

/** 占位菜单点击提示 */
const comingSoon = () => {
  import('element-plus').then(({ ElMessage }) => ElMessage.info('功能建设中，敬请期待'))
}

/** 顶栏图标色（active 白色） */
const navStroke = (active: boolean) => (active ? '#FFFFFF' : '#86909C')
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
              <rect x="2" y="8" width="4" height="9" rx="1" :stroke="navStroke(activeNav === 'dashboard')" stroke-width="1.5" />
              <rect x="8" y="5" width="4" height="12" rx="1" :stroke="navStroke(activeNav === 'dashboard')" stroke-width="1.5" />
              <rect x="14" y="10" width="4" height="7" rx="1" :stroke="navStroke(activeNav === 'dashboard')" stroke-width="1.5" />
            </svg>
          </router-link>
        </el-tooltip>

        <el-tooltip content="审批中心" placement="right" :show-after="300">
          <router-link to="/approvals" class="nav-item" :class="{ active: activeNav === 'approvals-center' }">
            <span v-if="approvalStore.todoCount > 0" class="red-dot"></span>
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M3 5L6 8L9 5" :stroke="navStroke(activeNav === 'approvals-center')" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
              <line x1="11" y1="6.5" x2="17" y2="6.5" :stroke="navStroke(activeNav === 'approvals-center')" stroke-width="1.5" stroke-linecap="round" />
              <path d="M3 10L6 13L9 10" :stroke="navStroke(activeNav === 'approvals-center')" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
              <line x1="11" y1="11.5" x2="17" y2="11.5" :stroke="navStroke(activeNav === 'approvals-center')" stroke-width="1.5" stroke-linecap="round" />
              <circle cx="5" cy="16" r="1.2" :stroke="navStroke(activeNav === 'approvals-center')" stroke-width="1.5" />
              <line x1="11" y1="16" x2="17" y2="16" :stroke="navStroke(activeNav === 'approvals-center')" stroke-width="1.5" stroke-linecap="round" />
            </svg>
          </router-link>
        </el-tooltip>

        <el-tooltip content="资产管理" placement="right" :show-after="300">
          <router-link to="/assets" class="nav-item" :class="{ active: isAssetModule }">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="3" y="6" width="14" height="11" rx="1.5" :stroke="navStroke(isAssetModule)" stroke-width="1.5" />
              <path d="M3 9.5L10 6L17 9.5" :stroke="navStroke(isAssetModule)" stroke-width="1.5" stroke-linejoin="round" />
              <line x1="10" y1="6" x2="10" y2="17" :stroke="navStroke(isAssetModule)" stroke-width="1.5" />
              <rect x="7" y="3" width="6" height="3" rx="0.5" :fill="navStroke(isAssetModule)" />
            </svg>
          </router-link>
        </el-tooltip>

        <el-tooltip content="库存管理（待接入）" placement="right" :show-after="300">
          <div class="nav-item" @click="comingSoon">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="3" y="11" width="6" height="6" rx="1" stroke="#86909C" stroke-width="1.5" />
              <rect x="11" y="11" width="6" height="6" rx="1" stroke="#86909C" stroke-width="1.5" />
              <rect x="7" y="3" width="6" height="6" rx="1" stroke="#86909C" stroke-width="1.5" />
            </svg>
          </div>
        </el-tooltip>

        <div class="sidebar-spacer"></div>

        <div class="bottom-nav">
          <el-tooltip content="通知（待接入）" placement="right" :show-after="300">
            <div class="bottom-nav-item">
              <span class="red-dot"></span>
              <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M8 17C8 18.1046 8.89543 19 10 19C11.1046 19 12 18.1046 12 17" stroke="#86909C" stroke-width="1.5" />
                <path d="M15 12V8C15 5.23858 12.7614 3 10 3C7.23858 3 5 5.23858 5 8V12L3 14H17L15 12Z" stroke="#86909C" stroke-width="1.5" stroke-linejoin="round" />
              </svg>
            </div>
          </el-tooltip>
          <el-tooltip content="帮助（待接入）" placement="right" :show-after="300">
            <div class="bottom-nav-item">
              <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
                <circle cx="10" cy="10" r="7.5" stroke="#86909C" stroke-width="1.5" />
                <path d="M8 8C8 6.89543 8.89543 6 10 6C11.1046 6 12 6.89543 12 8C12 8.73638 11.5977 9.37205 11 9.7324V11" stroke="#86909C" stroke-width="1.5" stroke-linecap="round" />
                <circle cx="10" cy="13.5" r="0.75" fill="#86909C" />
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
  </div>
</template>

<style scoped>
.app {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f6fa;
  min-width: 1200px;
}

/* 顶部导航栏 */
.topbar {
  height: 56px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
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
  border-radius: 4px;
  background: linear-gradient(135deg, #00d4aa 0%, #00b894 100%);
  color: #ffffff;
  font-size: 20px;
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
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
  line-height: 22px;
}

.logo-subtitle {
  font-size: 11px;
  color: #6b7280;
  font-weight: 400;
  white-space: nowrap;
  line-height: 16px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  outline: none;
}

.user-name {
  font-size: 14px;
  font-weight: 400;
  color: #111827;
  line-height: 22px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #4f7ff7;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 14px;
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
  color: #1d2129;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f2f5;
  margin-bottom: 8px;
}

.user-panel-row {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  font-size: 13px;
  line-height: 24px;
  color: #4e5969;
}

.user-panel-row span:first-child {
  color: #86909c;
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
  background: #ffffff;
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
}

.nav-item:hover {
  background: #f7f8fa;
}

.nav-item.active {
  background: #165dff;
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
  background: #f53f3f;
}

/* 二级子侧边栏 */
.sub-sidebar {
  width: 240px;
  background: #ffffff;
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
  border-bottom: 1px solid #f3f4f6;
  flex-shrink: 0;
}

.sidebar-title {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
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
  font-size: 12px;
  font-weight: 500;
  color: #9ca3af;
  cursor: pointer;
  user-select: none;
  width: 100%;
}

.section-title:hover {
  color: #6b7280;
}

.section-title-static {
  height: 28px;
  padding: 0 8px 4px;
  display: flex;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  color: #9ca3af;
  user-select: none;
}

.collapse-icon {
  font-size: 12px;
  transition: transform 0.2s ease;
  color: #9ca3af;
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
  padding: 0 10px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  font-size: 14px;
  color: #4b5563;
  transition: background 0.15s ease;
  text-decoration: none;
}

.menu-item:hover:not(.active) {
  background: #f9fafb;
}

.menu-item.active {
  background: #eef2ff;
  color: #165dff;
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
  background: #f5f6fa;
}

.content-scroll {
  flex: 1;
  overflow: auto;
  padding: 24px;
  min-height: 0;
}
</style>
