<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { Component } from 'vue'
import { useUserStore } from '@/stores/user'
import { useApprovalStore } from '@/stores/approval'
import { stocktakeApi } from '@/api/modules/stocktake'
import type { MeInfo } from '@/api/interface'
import IconDocReceive from '@/components/icons/IconDocReceive.vue'
import IconDocBorrow from '@/components/icons/IconDocBorrow.vue'
import IconDocTransfer from '@/components/icons/IconDocTransfer.vue'
import IconDocChange from '@/components/icons/IconDocChange.vue'
import IconDocStocktake from '@/components/icons/IconDocStocktake.vue'

const router = useRouter()
const userStore = useUserStore()
const approvalStore = useApprovalStore()

const loading = ref(true)
const failed = ref(false)
const me = ref<MeInfo | null>(null)

/** 进行中盘点任务数（>0 时待办卡下方提示） */
const inProgressStocktakes = ref(0)

onMounted(async () => {
  try {
    me.value = await userStore.loadMe()
    if (!me.value) failed.value = true
  } catch {
    // 401/403/503 已由 axios 拦截器统一提示/跳转，这里只标记失败态
    failed.value = true
  } finally {
    loading.value = false
  }
  /* 审批计数（待办卡三格）与进行中盘点数并行拉取，互不阻塞 */
  void approvalStore.refresh()
  stocktakeApi
    .getStocktakes({ status: 'IN_PROGRESS' })
    .then((list) => (inProgressStocktakes.value = list.length))
    .catch(() => {
      /* 拦截器已提示；提示行不渲染即可 */
    })
})

/* ---------------- 发起流程（待办卡第四格 popover + 快捷入口卡共用） ---------------- */
const composeEntries: { label: string; path: string; icon: Component }[] = [
  { label: '领用申请', path: '/receipts/receive?compose=1', icon: IconDocReceive },
  { label: '借用申请', path: '/receipts/borrow?compose=1', icon: IconDocBorrow },
  { label: '资产调拨', path: '/transfers?compose=1', icon: IconDocTransfer },
  { label: '信息变更', path: '/changes?compose=1', icon: IconDocChange },
  { label: '盘点任务', path: '/stocktakes?compose=1', icon: IconDocStocktake },
]

/* ---------------- 我的待办卡：审批计数三格 ---------------- */
const todoCells = computed(() => [
  { key: 'todo', label: '待我处理', count: approvalStore.todoCount, path: '/approvals?tab=todo' },
  { key: 'mine', label: '我发起的', count: approvalStore.mineActiveCount, path: '/approvals?tab=mine' },
  { key: 'handled', label: '我处理的', count: approvalStore.handledCount, path: '/approvals?tab=handled' },
])

/* ---------------- 最近使用（路由 afterEach 写 localStorage） ---------------- */
interface RecentRoute {
  path: string
  title: string
  ts: number
}
const recentRoutes = ref<RecentRoute[]>([])

const loadRecent = () => {
  try {
    const raw = localStorage.getItem('asset.recent.routes')
    if (raw) recentRoutes.value = JSON.parse(raw)
  } catch {
    /* 忽略损坏的本地存储 */
  }
}
loadRecent()

/** 足迹条目图标：按路径前缀映射五单据图标，其余用通用文档图形 */
const RECENT_ICONS: { prefix: string; icon: Component }[] = [
  { prefix: '/receipts/receive', icon: IconDocReceive },
  { prefix: '/receipts/borrow', icon: IconDocBorrow },
  { prefix: '/transfers', icon: IconDocTransfer },
  { prefix: '/changes', icon: IconDocChange },
  { prefix: '/stocktakes', icon: IconDocStocktake },
]
const iconOf = (path: string): Component | 'doc' =>
  RECENT_ICONS.find((it) => path === it.prefix || path.startsWith(`${it.prefix}/`) || path.startsWith(`${it.prefix}?`))
    ?.icon ?? 'doc'

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前 / 日期 */
const formatTime = (ts: number) => {
  const diff = Date.now() - ts
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  if (diff < 7 * 86_400_000) return `${Math.floor(diff / 86_400_000)} 天前`
  return new Date(ts).toLocaleDateString()
}
</script>

<template>
  <div v-loading="loading" class="workbench">
    <template v-if="me">
      <div class="columns">
        <!-- 左列：最近使用 / 我的收藏 / 快捷入口 -->
        <div class="left-col">
          <div class="card recent-card">
            <div class="card-title">最近使用</div>
            <template v-if="recentRoutes.length">
              <div
                v-for="r in recentRoutes"
                :key="r.path"
                class="recent-item"
                @click="router.push(r.path)"
              >
                <div class="recent-icon">
                  <component :is="iconOf(r.path)" v-if="iconOf(r.path) !== 'doc'" :size="22" />
                  <svg v-else width="22" height="22" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M5 2.5H12L16 6.5V17.5H5V2.5Z" stroke="#165DFF" stroke-width="1.5" stroke-linejoin="round" />
                    <path d="M12 2.5V6.5H16" stroke="#165DFF" stroke-width="1.5" stroke-linejoin="round" />
                    <path d="M7.5 10H12.5M7.5 13H12.5" stroke="#165DFF" stroke-width="1.5" stroke-linecap="round" />
                  </svg>
                </div>
                <div class="recent-meta">
                  <span class="recent-text">{{ r.title }}</span>
                  <span class="recent-time">{{ formatTime(r.ts) }}</span>
                </div>
              </div>
            </template>
            <div v-else class="empty-text">暂无最近使用</div>
          </div>

          <div class="card fav-card">
            <div class="title-row">
              <span class="card-title">我的收藏</span>
              <span class="action">+ 添加</span>
            </div>
            <div class="empty-state">
              <span class="empty-text">暂无收藏</span>
              <span class="empty-action">添加</span>
            </div>
          </div>

          <div class="card quick-card">
            <div class="card-title">快捷入口</div>
            <div class="quick-list">
              <div
                v-for="entry in composeEntries"
                :key="entry.path"
                class="quick-item"
                @click="router.push(entry.path)"
              >
                <div class="quick-icon">
                  <component :is="entry.icon" :size="18" />
                </div>
                <span class="quick-label">{{ entry.label }}</span>
                <span class="quick-arrow">→</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 右列：我的待办 / 我的应用 / 我的图表 -->
        <div class="right-col">
          <div class="card todo-card">
            <div class="todo-header">
              <div class="todo-left">
                <div class="todo-icon">✓</div>
                <span class="todo-title">我的待办</span>
              </div>
              <div class="todo-right">
                <div
                  v-for="cell in todoCells"
                  :key="cell.key"
                  class="todo-item"
                  @click="router.push(cell.path)"
                >
                  <div class="todo-count" :class="{ hot: cell.key === 'todo' && cell.count > 0 }">
                    {{ cell.count }}
                  </div>
                  <span class="todo-item-text">{{ cell.label }}</span>
                </div>
                <el-popover placement="bottom" :width="150" trigger="hover">
                  <template #reference>
                    <div class="todo-item">
                      <div class="todo-item-icon" style="background: #f5e8ff; color: #722ed1">+</div>
                      <span class="todo-item-text">发起流程</span>
                    </div>
                  </template>
                  <div class="compose-menu">
                    <div
                      v-for="entry in composeEntries"
                      :key="entry.path"
                      class="compose-item"
                      @click="router.push(entry.path)"
                    >
                      <component :is="entry.icon" :size="16" />
                      <span>{{ entry.label }}</span>
                    </div>
                  </div>
                </el-popover>
              </div>
            </div>
            <div
              v-if="inProgressStocktakes > 0"
              class="stocktake-line"
              @click="router.push('/stocktakes?tab=IN_PROGRESS')"
            >
              <span>进行中盘点任务 {{ inProgressStocktakes }} 个</span>
              <span class="stocktake-arrow">去处理 →</span>
            </div>
          </div>

          <div class="card app-card">
            <div class="app-title">我的应用</div>
            <div class="app-section-title">资产管理</div>
            <div class="app-row">
              <div class="app-item" @click="router.push('/assets')">
                <div class="app-icon-wrap">
                  <svg width="48" height="48" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <circle cx="24" cy="12" r="6.5" stroke="#165DFF" stroke-width="2.6" />
                    <path d="M24 19V32" stroke="#165DFF" stroke-width="2.6" stroke-linecap="round" />
                    <path d="M16 40L24 31L32 40" stroke="#165DFF" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" />
                    <path d="M15 25L24 21L33 25" stroke="#165DFF" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </div>
                <span class="app-label">资产管理</span>
              </div>
              <div class="app-item">
                <div class="app-icon-wrap">
                  <svg width="48" height="48" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <rect x="10" y="10" width="28" height="16" rx="2" stroke="#14C9C9" stroke-width="2.4" />
                    <rect x="10" y="26" width="28" height="12" rx="2" stroke="#14C9C9" stroke-width="2.4" />
                    <rect x="14" y="16" width="20" height="2" rx="1" fill="#14C9C9" />
                    <rect x="14" y="31" width="20" height="2" rx="1" fill="#14C9C9" />
                  </svg>
                </div>
                <span class="app-label">库存管理</span>
              </div>
            </div>

            <!-- 鉴权全链路验证（M-FE01 完成标准）：当前用户信息来自 GET /api/v1/me -->
            <div class="me-panel">
              <div class="me-panel-title">登录信息</div>
              <div class="me-grid">
                <div class="me-field">
                  <span class="me-label">姓名</span>
                  <span class="me-value">{{ me.name || '-' }}</span>
                </div>
                <div class="me-field">
                  <span class="me-label">部门</span>
                  <span class="me-value">{{ me.dept || '-' }}</span>
                </div>
                <div class="me-field">
                  <span class="me-label">岗位</span>
                  <span class="me-value">{{ me.job || '-' }}</span>
                </div>
                <div class="me-field">
                  <span class="me-label">角色</span>
                  <span class="me-value">{{ me.roles?.length ? me.roles.join('、') : '未分配' }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="card chart-card">
            <div class="title-row">
              <span class="card-title">我的图表</span>
              <span class="action">+ 添加</span>
            </div>
            <div class="chart-empty">
              <span class="empty-text">暂无图表</span>
              <span class="empty-action">添加</span>
            </div>
          </div>
        </div>
      </div>
    </template>

    <el-result
      v-else-if="failed && !loading"
      icon="warning"
      title="用户信息加载失败"
      sub-title="请检查后端服务与登录状态，或重新从应用中心进入"
    />
  </div>
</template>

<style scoped>
.workbench {
  min-height: 100%;
}

.columns {
  display: flex;
  gap: 24px;
  min-height: calc(100vh - 56px - 48px);
}

.card {
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(217, 222, 232, 0.3);
}

.card-title {
  font-size: 14px;
  font-weight: 500;
  color: #1d2129;
  line-height: 22px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-row .action {
  font-size: 12px;
  font-weight: 400;
  color: #165dff;
  line-height: 20px;
  cursor: pointer;
}

.empty-state {
  display: flex;
  align-items: center;
  gap: 6px;
}

.empty-text {
  font-size: 13px;
  font-weight: 400;
  color: #86909c;
  line-height: 22px;
}

.empty-action {
  font-size: 13px;
  font-weight: 400;
  color: #165dff;
  line-height: 22px;
  cursor: pointer;
}

/* 左列 */
.left-col {
  width: 256px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex-shrink: 0;
}

.recent-card {
  flex: 1;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  margin: 0 -8px;
  border-radius: 8px;
  cursor: pointer;
}

.recent-item:hover {
  background: #f7f8fa;
}

.recent-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #f2f6ff;
  color: #165dff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.recent-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.recent-text {
  font-size: 14px;
  font-weight: 400;
  color: #4e5969;
  line-height: 22px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recent-time {
  font-size: 12px;
  color: #a6adb8;
  line-height: 18px;
}

.fav-card,
.quick-card {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 快捷入口：五个发起流程（跳列表页 ?compose=1 自动开弹窗） */
.quick-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.quick-item {
  height: 38px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px;
  margin: 0 -8px;
  border-radius: 8px;
  cursor: pointer;
}

.quick-item:hover {
  background: #f7f8fa;
}

.quick-icon {
  width: 30px;
  height: 30px;
  border-radius: 6px;
  background: #eef4ff;
  color: #165dff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.quick-label {
  font-size: 14px;
  color: #4e5969;
  flex: 1;
}

.quick-arrow {
  font-size: 12px;
  color: #c0c6cf;
}

/* 右列 */
.right-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  min-height: 0;
}

.todo-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex-shrink: 0;
}

.todo-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 0;
}

.todo-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.todo-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #165dff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 20px;
  font-weight: 700;
  line-height: 28px;
}

.todo-title {
  font-size: 16px;
  font-weight: 500;
  color: #1d2129;
  line-height: 24px;
}

.todo-right {
  flex: 1;
  display: flex;
  align-items: center;
  min-width: 0;
}

.todo-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding-bottom: 4px;
  cursor: pointer;
}

.todo-item:hover .todo-count,
.todo-item:hover .todo-item-text {
  color: #165dff;
}

.todo-count {
  font-size: 26px;
  font-weight: 600;
  color: #1d2129;
  line-height: 32px;
  font-variant-numeric: tabular-nums;
  transition: color 0.15s ease;
}

.todo-count.hot {
  color: #f53f3f;
}

.todo-item-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  line-height: 24px;
}

.todo-item-text {
  font-size: 13px;
  font-weight: 400;
  color: #4e5969;
  line-height: 22px;
  transition: color 0.15s ease;
}

/* 进行中盘点提示行（仅 N>0 渲染） */
.stocktake-line {
  height: 40px;
  border-radius: 8px;
  background: #fff7e8;
  padding: 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #d25f00;
  cursor: pointer;
}

.stocktake-arrow {
  font-weight: 500;
}

.app-card {
  flex: 1;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 0;
}

.app-title {
  font-size: 16px;
  font-weight: 500;
  color: #1d2129;
  line-height: 24px;
}

.app-section-title {
  font-size: 13px;
  font-weight: 400;
  color: #86909c;
  line-height: 22px;
}

.app-row {
  display: flex;
  gap: 32px;
  align-items: center;
}

.app-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.app-icon-wrap {
  width: 77px;
  height: 77px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.app-icon {
  width: 48px;
  height: 48px;
  display: block;
}

.app-label {
  font-size: 13px;
  font-weight: 400;
  color: #1d2129;
  line-height: 22px;
}

/* 登录信息面板（M-FE01 鉴权验证） */
.me-panel {
  margin-top: auto;
  border-top: 1px solid #f0f2f5;
  padding-top: 16px;
}

.me-panel-title {
  font-size: 13px;
  font-weight: 400;
  color: #86909c;
  line-height: 22px;
  margin-bottom: 12px;
}

.me-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 48px;
}

.me-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.me-label {
  font-size: 12px;
  color: #86909c;
  line-height: 20px;
}

.me-value {
  font-size: 14px;
  color: #1d2129;
  line-height: 22px;
}

.chart-card {
  height: 140px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex-shrink: 0;
}

.chart-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

/* 发起流程 popover 菜单 */
.compose-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.compose-item {
  height: 34px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  margin: 0 -6px;
  border-radius: 6px;
  font-size: 13px;
  color: #4e5969;
  cursor: pointer;
}

.compose-item:hover {
  background: #f2f6ff;
  color: #165dff;
}
</style>
