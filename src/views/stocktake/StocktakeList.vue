<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useStocktakeStore } from '@/stores/stocktake'
import { useUserStore } from '@/stores/user'
import { useTabsStore } from '@/stores/tabs'
import { stocktakeApi } from '@/api/modules/stocktake'
import type { Stocktake, StocktakeStatus } from '@/api/interface/stocktake'
import { STOCKTAKE_STATUS_META } from '@/api/interface/stocktake'
import StocktakeCreateModal from './components/StocktakeCreateModal.vue'
import StocktakeDetailDrawer from './components/StocktakeDetailDrawer.vue'

/**
 * M07 盘点列表：创建（范围快照）→ 开始 → 扫码核对（五态）→ 完成 → 报告/触发调拨。
 * 状态 tabs + 关键词搜索 + 前端分页 + 深链 query 同步；
 * keep-alive 缓存由本组件 defineOptions name（与路由 name 一致）决定。
 */
defineOptions({ name: 'stocktakes-list' })

const route = useRoute()
const router = useRouter()
const tabsStore = useTabsStore()
const userStore = useUserStore()
const store = useStocktakeStore()

const stocktakes = computed(() => store.stocktakes)
const loading = computed(() => store.loading)

/* ---------------- 状态 tabs（前端过滤 + 计数） ---------------- */
type TabKey = 'ALL' | StocktakeStatus
const activeTab = ref<TabKey>('ALL')

const statusCount = (status: StocktakeStatus) =>
  stocktakes.value.filter((s) => s.status === status).length

const tabs = computed(() => [
  { key: 'ALL' as TabKey, label: '全部', count: stocktakes.value.length },
  { key: 'PENDING' as TabKey, label: '待开始', count: statusCount('PENDING') },
  { key: 'IN_PROGRESS' as TabKey, label: '进行中', count: statusCount('IN_PROGRESS') },
  { key: 'COMPLETED' as TabKey, label: '已完成', count: statusCount('COMPLETED') },
  { key: 'CANCELLED' as TabKey, label: '已取消', count: statusCount('CANCELLED') },
])

/* ---------------- 搜索（防抖 300ms 前端过滤） ---------------- */
const keyword = ref('')
const searchKeyword = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

watch(keyword, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKeyword.value = val.trim()
    currentPage.value = 1
  }, 300)
})
onBeforeUnmount(() => searchTimer && clearTimeout(searchTimer))

/* ---------------- 前端过滤 + 分页 ---------------- */
const PAGE_SIZE = 10
const currentPage = ref(1)

/** 盘点范围展示：位置 × 分类（空 = 全库/全部分类） */
const scopeText = (row: Stocktake) =>
  `${row.locationName || '全库'} × ${row.categoryName || '全部分类'}`

const creatorText = (row: Stocktake) => row.creatorName || String(row.creatorUserId)

/** 五态汇总（进度展示：已盘 = 总数 - 待盘） */
const progressText = (row: Stocktake) => {
  if (row.totalCount === undefined || row.totalCount === null) return '—'
  const total = Number(row.totalCount)
  const pending = Number(row.pendingCount ?? 0)
  return `${total - pending}/${total}`
}

const filtered = computed(() => {
  let list = stocktakes.value
  if (activeTab.value !== 'ALL') list = list.filter((s) => s.status === activeTab.value)
  const kw = searchKeyword.value.toLowerCase()
  if (kw) {
    list = list.filter((s) =>
      [s.name, s.locationName, s.categoryName, s.creatorName, s.remark, scopeText(s)].some((field) =>
        field?.toLowerCase().includes(kw),
      ),
    )
  }
  return list
})

const total = computed(() => filtered.value.length)

const pageData = computed(() =>
  filtered.value.slice((currentPage.value - 1) * PAGE_SIZE, currentPage.value * PAGE_SIZE),
)

/* tab/过滤结果变化时纠正越界页码 */
watch([activeTab, total], () => {
  const maxPage = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
  if (currentPage.value > maxPage) currentPage.value = maxPage
})

/* ---------------- 深链与刷新保持：tab/搜索词/页码同步 URL query ---------------- */
watch(
  () => route.query,
  (q) => {
    if (route.name !== 'stocktakes-list') return
    const tab = typeof q.tab === 'string' ? q.tab : ''
    const validTabs = ['ALL', 'PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']
    const nextTab = validTabs.includes(tab) ? (tab as TabKey) : 'ALL'
    if (nextTab !== activeTab.value) activeTab.value = nextTab
    const kw = typeof q.q === 'string' ? q.q : ''
    if (kw !== keyword.value) keyword.value = kw
    const p = Number(q.page)
    if (Number.isInteger(p) && p >= 1 && p !== currentPage.value) currentPage.value = p
  },
  { immediate: true },
)

watch([activeTab, searchKeyword, currentPage], () => {
  if (route.name !== 'stocktakes-list') return
  const query: Record<string, string> = {}
  if (activeTab.value !== 'ALL') query.tab = activeTab.value
  if (searchKeyword.value) query.q = searchKeyword.value
  if (currentPage.value > 1) query.page = String(currentPage.value)
  const current = JSON.stringify(route.query)
  const next = JSON.stringify(query)
  if (current !== next) router.replace({ query })
})

/* ---------------- 数据加载 ---------------- */
/* keep-alive：首次 onMounted 拉取；之后每次切回本页刷新（状态可能已被他人变更） */
let firstActivation = true
onMounted(() => store.fetchStocktakes())
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  store.fetchStocktakes()
})

/* ---------------- 创建任务 ---------------- */
const modalVisible = ref(false)

/* 深链发起：?compose=1 自动打开创建弹窗（工作台快捷入口；用后清洗） */
watch(
  () => route.query.compose,
  (v) => {
    if (route.name !== 'stocktakes-list' || v !== '1') return
    router.replace({ query: { ...route.query, compose: undefined } })
    modalVisible.value = true
  },
  { immediate: true },
)

/* 弹窗打开 = 有未提交内容：关页签前需确认 */
watch(modalVisible, (v) => tabsStore.setDirty(route.fullPath, v))

const handleCreated = (item: Stocktake) => store.unshiftLocal(item)

/* ---------------- 详情与快捷操作 ---------------- */
const drawerVisible = ref(false)
const currentStocktake = ref<Stocktake>()
const actingId = ref<number>()

const openDetail = (record: Stocktake) => {
  currentStocktake.value = record
  drawerVisible.value = true
}

/* 深链定位：?id=123 打开对应任务详情（工作台/审批中心跳转入口；watch 兼容 keep-alive 缓存后二次深链） */
watch(
  () => route.query.id,
  async (v) => {
    if (route.name !== 'stocktakes-list') return
    const id = Number(v)
    if (!Number.isInteger(id) || id <= 0) return
    router.replace({ query: { ...route.query, id: undefined } })
    try {
      openDetail(await stocktakeApi.getStocktakeById(id))
    } catch {
      /* 404 已由拦截器提示 */
    }
  },
  { immediate: true },
)

/* 详情抽屉内开始/取消/完成/生成调拨成功：列表原地更新 */
const handleUpdated = (item: Stocktake) => store.upsertLocal(item)

/** 列表快捷开始（PENDING → IN_PROGRESS） */
const handleStart = async (row: Stocktake) => {
  if (actingId.value) return
  actingId.value = row.id
  try {
    const updated = await stocktakeApi.start(row.id)
    ElMessage.success('盘点已开始，进入详情可扫码核对')
    store.upsertLocal(updated)
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
  } finally {
    actingId.value = undefined
  }
}

/** 列表快捷取消（仅创建人，后端 403 校验） */
const handleCancel = async (row: Stocktake) => {
  if (actingId.value) return
  try {
    await ElMessageBox.confirm(
      `确认取消盘点任务「${row.name}」吗？明细与资产数据保持不变。`,
      '取消盘点',
      { type: 'warning', confirmButtonText: '取消任务', cancelButtonText: '再想想' },
    )
  } catch {
    return /* 用户取消 */
  }
  actingId.value = row.id
  try {
    const updated = await stocktakeApi.cancel(row.id)
    ElMessage.success('盘点任务已取消')
    store.upsertLocal(updated)
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
  } finally {
    actingId.value = undefined
  }
}

/** 当前登录人是创建人（列表取消按钮出现条件） */
const isCreator = (row: Stocktake) => row.creatorUserId === userStore.me?.userId

/* 详情/创建弹窗打开时挂起表格快捷键 */
const anyOverlayOpen = computed(() => modalVisible.value || drawerVisible.value)
watch(anyOverlayOpen, (v) => tabsStore.setDirty(route.fullPath, v))

/* ---------------- 输入与触发：Ctrl+F 聚焦搜索 ---------------- */
const searchInputEl = ref<HTMLElement>()

const onWindowKeydown = (e: KeyboardEvent) => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') {
    e.preventDefault()
    if (!anyOverlayOpen.value) searchInputEl.value?.querySelector('input')?.focus()
  }
}
onMounted(() => window.addEventListener('keydown', onWindowKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onWindowKeydown))

/* ---------------- 展示工具 ---------------- */
const formatText = (_row: unknown, _column: unknown, cellValue: unknown) =>
  cellValue === undefined || cellValue === null || cellValue === '' ? '—' : cellValue

/** 状态 tag：优先后端 statusLabel，回退本地 meta */
const statusLabel = (row: Stocktake) =>
  row.statusLabel || STOCKTAKE_STATUS_META[row.status].label
</script>

<template>
  <div class="page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">盘点管理</h2>
        <span class="page-subtitle">创建按范围快照 → 开始 → 扫码核对（相符/位置不符/盘亏/盘盈）→ 完成 → 报告与触发调拨归位</span>
      </div>

      <!-- 状态 tabs -->
      <div class="tabs">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          class="tab"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          <span>{{ tab.label }}</span>
          <span class="tab-count">({{ tab.count }})</span>
        </div>
      </div>

      <!-- 工具栏：创建任务 + 搜索 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" @click="modalVisible = true">
            <span class="btn-plus">+</span>
            <span>创建盘点任务</span>
          </el-button>
        </div>
        <el-input
          ref="searchInputEl"
          v-model="keyword"
          class="search-box"
          placeholder="搜索任务名称 / 范围 / 创建人 / 备注（Ctrl+F）"
          clearable
        />
      </div>

      <!-- 盘点任务表格：双击行开详情 -->
      <el-table
        v-loading="loading"
        :data="pageData"
        row-key="id"
        border
        highlight-current-row
        empty-text="暂无盘点任务"
        @row-dblclick="openDetail"
      >
        <el-table-column prop="name" label="任务名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag :type="STOCKTAKE_STATUS_META[row.status as StocktakeStatus].tagType" effect="light">
              {{ statusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="盘点范围" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ scopeText(row) }}</template>
        </el-table-column>
        <el-table-column label="已盘/总数" width="96" align="center">
          <template #default="{ row }">{{ progressText(row) }}</template>
        </el-table-column>
        <el-table-column label="相符" width="66" align="center">
          <template #default="{ row }">{{ row.matchedCount ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="不符" width="66" align="center">
          <template #default="{ row }">
            <span :class="{ 'mismatch-num': (row.mismatchCount ?? 0) > 0 }">{{ row.mismatchCount ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="盘亏" width="66" align="center">
          <template #default="{ row }">
            <span :class="{ 'loss-num': (row.notFoundCount ?? 0) > 0 }">{{ row.notFoundCount ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="盘盈" width="66" align="center">
          <template #default="{ row }">{{ row.extraCount ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="创建人" min-width="100">
          <template #default="{ row }">{{ creatorText(row) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" :formatter="formatText" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="primary"
              :loading="actingId === row.id"
              @click.stop="handleStart(row)"
            >
              开始
            </el-button>
            <el-button
              v-if="(row.status === 'PENDING' || row.status === 'IN_PROGRESS') && isCreator(row)"
              link
              type="danger"
              :loading="actingId === row.id"
              @click.stop="handleCancel(row)"
            >
              取消
            </el-button>
            <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <span class="pagination-info">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="PAGE_SIZE"
          :total="total"
          layout="prev, pager, next"
          background
        />
      </div>
    </div>

    <!-- 创建盘点任务 -->
    <StocktakeCreateModal v-model:visible="modalVisible" @success="handleCreated" />

    <!-- 盘点详情（含扫码核对/完成/报告/生成调拨） -->
    <StocktakeDetailDrawer v-model:visible="drawerVisible" :stocktake="currentStocktake" @updated="handleUpdated" />
  </div>
</template>

<style scoped>
.page-container {
  background: #ffffff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.page-header {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  margin: 0;
}

.page-subtitle {
  font-size: 13px;
  color: #86909c;
  flex: 1;
}

/* tabs（对齐原型：胶囊样式） */
.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.tab {
  height: 34px;
  padding: 0 12px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  background: #f3f4f6;
  color: #6b7280;
  cursor: pointer;
  user-select: none;
}

.tab.active {
  background: #165dff;
  color: #ffffff;
}

.tab-count {
  font-size: 13px;
}

/* 工具栏 */
.toolbar {
  min-height: 60px;
  padding: 14px;
  background: #fafbfc;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.toolbar-left {
  display: flex;
  gap: 8px;
  align-items: center;
}

.btn-plus {
  font-size: 15px;
  line-height: 1;
  margin-right: 2px;
}

.search-box {
  width: 300px;
  flex-shrink: 0;
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.pagination-info {
  font-size: 13px;
  color: #6b7280;
}

/* 差异计数高亮：位置不符（橙）/ 盘亏（红） */
.mismatch-num {
  color: #e6a23c;
  font-weight: 600;
}

.loss-num {
  color: #f56c6c;
  font-weight: 600;
}

/* 双击行可查看详情：指针光标提示可交互 */
:deep(.el-table__row) {
  cursor: pointer;
}
</style>
