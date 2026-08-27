<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { Component } from 'vue'
import { useUserStore } from '@/stores/user'
import { useApprovalStore } from '@/stores/approval'
import { stocktakeApi } from '@/api/modules/stocktake'
import { statsApi } from '@/api/modules/stats'
import type { AssetStatusSlice, StatsOverviewVO } from '@/api/interface/stats'
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

/** B3 工作台统计：资产状态占比 + 待办/持有/盘点总览 */
const overview = ref<StatsOverviewVO | null>(null)
const overviewLoading = ref(false)
const overviewFailed = ref(false)

onMounted(async () => {
  try {
    me.value = await userStore.loadMe()
    if (!me.value) failed.value = true
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
  /* 审批计数、进行中盘点、B3 聚合统计 三者独立并行，互不阻塞 */
  void approvalStore.refresh()
  stocktakeApi
    .getStocktakes({ status: 'IN_PROGRESS' })
    .then((list) => (inProgressStocktakes.value = list.length))
    .catch(() => {
      /* 拦截器已提示；提示行不渲染即可 */
    })
  void loadOverview()
})

/** 加载 /stats/overview，失败不抛出不阻塞主页面 */
const loadOverview = async () => {
  overviewLoading.value = true
  overviewFailed.value = false
  try {
    overview.value = await statsApi.overview()
  } catch {
    overview.value = null
    overviewFailed.value = true
  } finally {
    overviewLoading.value = false
  }
}

/* ---------------- 待办卡三格（优先用 B3 概览，若 B3 未就绪则退回审批 store 计数） ---------------- */
const effectiveTodo = computed(() => overview.value?.todoStat)
const todoCells = computed(() => {
  const total = effectiveTodo.value?.total ?? approvalStore.todoCount
  const urgent = effectiveTodo.value?.urgent ?? approvalStore.todoCount
  return [
    {
      key: 'todo',
      label: '待我处理',
      count: total,
      sub: urgent > 0 ? `定向 ${urgent}` : undefined,
      path: '/approvals?tab=todo',
    },
    {
      key: 'mine',
      label: '我发起的',
      count: approvalStore.mineActiveCount,
      path: '/approvals?tab=mine',
    },
    {
      key: 'handled',
      label: '我处理的',
      count: approvalStore.handledCount,
      path: '/approvals?tab=handled',
    },
  ]
})

/* ---------------- 发起流程（待办卡第四格 popover + 快捷入口卡共用） ---------------- */
const composeEntries: { label: string; path: string; icon: Component }[] = [
  { label: '领用申请', path: '/receipts/receive?compose=1', icon: IconDocReceive },
  { label: '借用申请', path: '/receipts/borrow?compose=1', icon: IconDocBorrow },
  { label: '资产调拨', path: '/transfers?compose=1', icon: IconDocTransfer },
  { label: '信息变更', path: '/changes?compose=1', icon: IconDocChange },
  { label: '盘点任务', path: '/stocktakes?compose=1', icon: IconDocStocktake },
]

/* ---------------- 资产状态占比饼（不用 ECharts：纯 CSS/SVG 环形图，避免额外依赖） ---------------- */
const pieSlices = computed<AssetStatusSlice[]>(() => overview.value?.assetStatusPie ?? [])
const pieTotal = computed(() => pieSlices.value.reduce((acc, s) => acc + s.count, 0))
/** SVG 环形图参数：circle r=42, d=2*PI*42 ≈ 263.89 */
const PIE_RADIUS = 42
const PIE_CIRC = Math.round(2 * Math.PI * PIE_RADIUS * 100) / 100
const pieSegments = computed(() => {
  let offset = 0
  return pieSlices.value.map((s) => {
    const length = pieTotal.value > 0 ? (s.count / pieTotal.value) * PIE_CIRC : 0
    const seg = {
      ...s,
      dashArray: `${length.toFixed(2)} ${(PIE_CIRC - length).toFixed(2)}`,
      dashOffset: (-offset).toFixed(2),
    }
    offset += length
    return seg
  })
})
const pieColor = (c: string) => {
  switch (c) {
    case 'primary':
      return '#165DFF'
    case 'success':
      return '#00B42A'
    case 'warning':
      return '#FF7D00'
    case 'danger':
      return '#F53F3F'
    case 'info':
    default:
      return '#86909C'
  }
}

/* ---------------- 持有卡 + 盘点卡（B3 小卡） ---------------- */
const holdingStat = computed(() => overview.value?.holdingStat)
const stocktakeStat = computed(() => overview.value?.stocktakeStat)

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

/** 百分比显示：0.34 → 34%，0 显示 -，总计 0 时也显示 - */
const formatPercent = (p: number) => {
  if (!p || p <= 0) return '-'
  const v = Math.round(p * 100)
  return v <= 0 ? '<1%' : `${v}%`
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
                  <div class="todo-col">
                    <span class="todo-item-text">{{ cell.label }}</span>
                    <span v-if="cell.sub" class="todo-sub">· {{ cell.sub }}</span>
                  </div>
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

          <!-- 工作台数据卡：资产状态占比（环形图） + 持有卡 + 盘点卡（B3 /stats/overview） -->
          <div class="card stats-card">
            <div class="title-row">
              <span class="card-title">数据概览</span>
              <el-tooltip
                v-if="overviewFailed"
                content="统计接口加载失败，点击重试"
                placement="top"
              >
                <span class="action" @click="loadOverview">↻ 重试</span>
              </el-tooltip>
            </div>

            <div v-loading="overviewLoading" class="stats-body">
              <!-- 左：资产状态占比饼 -->
              <div class="pie-card">
                <div class="pie-title">资产状态占比</div>
                <div class="pie-row">
                  <div class="pie-chart" :title="`资产总数 ${pieTotal}`">
                    <svg viewBox="0 0 100 100" width="104" height="104">
                      <circle
                        cx="50"
                        cy="50"
                        r="42"
                        fill="none"
                        stroke="#f2f3f5"
                        stroke-width="12"
                      />
                      <circle
                        v-for="seg in pieSegments"
                        :key="seg.status"
                        cx="50"
                        cy="50"
                        r="42"
                        fill="none"
                        :stroke="pieColor(seg.color)"
                        stroke-width="12"
                        stroke-dasharray="263.89"
                        :stroke-dashoffset="0"
                        transform="rotate(-90 50 50)"
                        style="pointer-events: none"
                      />
                      <!-- 第二个叠层，用于按 dashArray 分块渲染（避免 SVG 渲染歧义） -->
                      <circle
                        v-for="seg in pieSegments"
                        :key="'p2-' + seg.status"
                        cx="50"
                        cy="50"
                        r="42"
                        fill="none"
                        :stroke="pieColor(seg.color)"
                        stroke-width="12"
                        :stroke-dasharray="seg.dashArray"
                        :stroke-dashoffset="seg.dashOffset"
                        :stroke-linecap="'butt'"
                        transform="rotate(-90 50 50)"
                      />
                    </svg>
                    <div class="pie-center">
                      <span class="pie-num">{{ pieTotal }}</span>
                      <span class="pie-num-label">总数</span>
                    </div>
                  </div>

                  <ul class="pie-legend">
                    <li v-for="s in pieSlices" :key="s.status" class="legend-row">
                      <span class="legend-dot" :style="{ background: pieColor(s.color) }" />
                      <span class="legend-label">{{ s.label }}</span>
                      <span class="legend-val">{{ s.count }} · {{ formatPercent(s.percent) }}</span>
                    </li>
                    <li v-if="!overviewLoading && pieSlices.length === 0" class="legend-empty">
                      {{ overviewFailed ? '统计加载失败' : '暂无数据' }}
                    </li>
                  </ul>
                </div>
              </div>

              <!-- 右：持有 + 盘点 两张小卡 -->
              <div class="mini-cards">
                <div class="mini-card holding-card-mini">
                  <div class="mini-title">我的持有</div>
                  <div class="mini-big-row">
                    <div class="mini-num">
                      {{ holdingStat?.myHoldings ?? 0 }}
                      <span class="mini-num-unit">件</span>
                    </div>
                    <div class="mini-sub">
                      部门持有：
                      <strong>{{ typeof holdingStat?.deptHoldings === 'number' ? holdingStat.deptHoldings : '-' }}</strong>
                    </div>
                  </div>
                  <div class="mini-link" @click="router.push('/assets?me=hold')">
                    查看我的资产 →
                  </div>
                </div>

                <div class="mini-card stocktake-card-mini">
                  <div class="mini-title">盘点</div>
                  <div class="mini-big-row">
                    <div class="mini-num">
                      {{ stocktakeStat?.ongoing ?? 0 }}
                      <span class="mini-num-unit">单</span>
                    </div>
                    <div class="mini-sub">
                      近 30 天完成：
                      <strong>{{ typeof stocktakeStat?.recent === 'number' ? stocktakeStat.recent : '-' }}</strong>
                    </div>
                  </div>
                  <div class="mini-link" @click="router.push('/stocktakes?tab=IN_PROGRESS')">
                    进入盘点 →
                  </div>
                </div>
              </div>
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

.todo-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.todo-sub {
  font-size: 12px;
  color: #f53f3f;
  font-weight: 500;
  line-height: 16px;
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

/* ---------- F4 数据概览卡 ---------- */
.stats-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex-shrink: 0;
}

.stats-body {
  display: flex;
  align-items: stretch;
  gap: 20px;
  min-height: 160px;
}

/* 饼区 */
.pie-card {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pie-title {
  font-size: 13px;
  color: #86909c;
  line-height: 18px;
}

.pie-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.pie-chart {
  position: relative;
  width: 104px;
  height: 104px;
  flex-shrink: 0;
}

.pie-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.pie-num {
  font-size: 20px;
  font-weight: 700;
  color: #1d2129;
  line-height: 24px;
  font-variant-numeric: tabular-nums;
}

.pie-num-label {
  font-size: 12px;
  color: #86909c;
  line-height: 16px;
}

.pie-legend {
  list-style: none;
  padding: 0;
  margin: 0;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.legend-row {
  display: grid;
  grid-template-columns: 12px 1fr auto;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #4e5969;
  line-height: 20px;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.legend-label {
  color: #4e5969;
}

.legend-val {
  color: #1d2129;
  font-variant-numeric: tabular-nums;
}

.legend-empty {
  font-size: 13px;
  color: #86909c;
  padding: 12px 0;
  text-align: center;
}

/* 右小卡：持有 + 盘点 */
.mini-cards {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mini-card {
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  position: relative;
  overflow: hidden;
}

.holding-card-mini {
  background: linear-gradient(135deg, #eef4ff 0%, #f5faff 100%);
  border: 1px solid #d6e4ff;
}

.stocktake-card-mini {
  background: linear-gradient(135deg, #e8ffea 0%, #f3fff4 100%);
  border: 1px solid #c9f2cf;
}

.mini-title {
  font-size: 13px;
  color: #86909c;
  line-height: 18px;
}

.mini-big-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
}

.mini-num {
  font-size: 28px;
  font-weight: 700;
  color: #1d2129;
  line-height: 32px;
  font-variant-numeric: tabular-nums;
}

.mini-num-unit {
  font-size: 13px;
  font-weight: 400;
  color: #86909c;
  margin-left: 2px;
}

.mini-sub {
  font-size: 12px;
  color: #4e5969;
  line-height: 18px;
  padding-bottom: 4px;
}
.mini-sub strong {
  color: #1d2129;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.mini-link {
  align-self: flex-start;
  font-size: 12px;
  color: #165dff;
  line-height: 18px;
  cursor: pointer;
  font-weight: 500;
}
.mini-link:hover {
  text-decoration: underline;
}

/* ---------- 响应式：窄屏下 mini-cards 切 100% 宽 ---------- */
@media (max-width: 1100px) {
  .stats-body {
    flex-direction: column;
  }
  .mini-cards {
    width: 100%;
    flex-direction: row;
  }
  .mini-card {
    flex: 1;
  }
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
