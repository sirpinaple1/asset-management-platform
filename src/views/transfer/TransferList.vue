<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { TableInstance } from 'element-plus'
import { useTransferStore } from '@/stores/transfer'
import { useTabsStore } from '@/stores/tabs'
import { transferApi } from '@/api/modules/transfer'
import type { TransferOrder, TransferStatus } from '@/api/interface/transfer'
import { TRANSFER_STATUS_META, TRANSFER_SOURCE_META } from '@/api/interface/transfer'
import TransferApplyModal from './components/TransferApplyModal.vue'
import TransferDetailDrawer from './components/TransferDetailDrawer.vue'

/**
 * M05 调拨单（ATR）列表：调出方发起 → 调入方确认/拒绝 / 发起方撤销。
 * 状态 tabs + 关键词搜索 + 前端分页 + 深链 query 同步；
 * keep-alive 缓存由本组件 defineOptions name（与路由 name 一致）决定。
 */
defineOptions({ name: 'transfers-list' })

const route = useRoute()
const router = useRouter()
const tabsStore = useTabsStore()
const store = useTransferStore()

const transfers = computed(() => store.transfers)
const loading = computed(() => store.loading)

/* ---------------- 状态 tabs（前端过滤 + 计数） ---------------- */
type TabKey = 'ALL' | TransferStatus
const activeTab = ref<TabKey>('ALL')

const statusCount = (status: TransferStatus) =>
  transfers.value.filter((t) => t.status === status).length

const tabs = computed(() => [
  { key: 'ALL' as TabKey, label: '全部', count: transfers.value.length },
  { key: 'PENDING' as TabKey, label: '待确认', count: statusCount('PENDING') },
  { key: 'COMPLETED' as TabKey, label: '已完成', count: statusCount('COMPLETED') },
  { key: 'REJECTED' as TabKey, label: '已拒绝', count: statusCount('REJECTED') },
  { key: 'CANCELLED' as TabKey, label: '已撤销', count: statusCount('CANCELLED') },
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

const filtered = computed(() => {
  let list = transfers.value
  if (activeTab.value !== 'ALL') list = list.filter((t) => t.status === activeTab.value)
  const kw = searchKeyword.value.toLowerCase()
  if (kw) {
    list = list.filter((t) =>
      [t.serialNo, t.applicantName, t.toDepartment, t.reason, t.toLocationName].some((field) =>
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
    if (route.name !== 'transfers-list') return
    const tab = typeof q.tab === 'string' ? q.tab : ''
    const validTabs = ['ALL', 'PENDING', 'COMPLETED', 'REJECTED', 'CANCELLED']
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
  if (route.name !== 'transfers-list') return
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
onMounted(() => store.fetchTransfers())
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  store.fetchTransfers()
})

/* ---------------- 新建申请 ---------------- */
const modalVisible = ref(false)

/* 弹窗打开 = 有未提交内容：关页签前需确认 */
watch(modalVisible, (v) => tabsStore.setDirty(route.fullPath, v))

const handleApplied = (item: TransferOrder) => store.unshiftLocal(item)

/* ---------------- 详情与流转操作 ---------------- */
const drawerVisible = ref(false)
const currentTransfer = ref<TransferOrder>()

const openDetail = (record: TransferOrder) => {
  currentTransfer.value = record
  drawerVisible.value = true
}

/* 深链定位：?id=123 打开对应单据详情（审批中心跳转入口；watch 兼容 keep-alive 缓存后二次深链） */
watch(
  () => route.query.id,
  async (v) => {
    if (route.name !== 'transfers-list') return
    const id = Number(v)
    if (!Number.isInteger(id) || id <= 0) return
    router.replace({ query: { ...route.query, id: undefined } })
    try {
      openDetail(await transferApi.getTransferById(id))
    } catch {
      /* 404 已由拦截器提示 */
    }
  },
  { immediate: true },
)

/* 详情抽屉内确认/拒绝/撤销成功：列表原地更新 */
const handleUpdated = (item: TransferOrder) => store.upsertLocal(item)

/* 详情/申请弹窗打开时挂起表格快捷键 */
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
const statusLabel = (row: TransferOrder) =>
  row.statusLabel || TRANSFER_STATUS_META[row.status].label

/** 来源：优先后端 sourceLabel，回退本地 meta */
const sourceLabel = (row: TransferOrder) =>
  row.sourceLabel || TRANSFER_SOURCE_META[row.source].label

const itemCount = (row: TransferOrder) =>
  row.items === undefined ? '—' : String(row.items.length)

const applicantText = (row: TransferOrder) =>
  row.applicantName || String(row.applicantUserId)

const confirmerText = (row: TransferOrder) =>
  row.confirmerName || (row.confirmerUserId ? String(row.confirmerUserId) : '—')
</script>

<template>
  <div class="page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">资产调拨</h2>
        <span class="page-subtitle">调拨单（单号前缀 ATR）：调出方发起 → 调入方确认后资产归属更新</span>
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

      <!-- 工具栏：发起调拨 + 搜索 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" @click="modalVisible = true">
            <span class="btn-plus">+</span>
            <span>发起调拨</span>
          </el-button>
        </div>
        <el-input
          ref="searchInputEl"
          v-model="keyword"
          class="search-box"
          placeholder="搜索单号 / 申请人 / 调入部门 / 位置 / 原因（Ctrl+F）"
          clearable
        />
      </div>

      <!-- 调拨单表格：双击行开详情 -->
      <el-table
        v-loading="loading"
        :data="pageData"
        row-key="id"
        border
        highlight-current-row
        empty-text="暂无调拨单数据"
        @row-dblclick="openDetail"
      >
        <el-table-column prop="serialNo" label="单号" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="TRANSFER_STATUS_META[row.status as TransferStatus].tagType" effect="light">
              {{ statusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="96">
          <template #default="{ row }">{{ sourceLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="发起人" min-width="110">
          <template #default="{ row }">{{ applicantText(row) }}</template>
        </el-table-column>
        <el-table-column prop="fromLocationName" label="调出位置" min-width="120" show-overflow-tooltip :formatter="formatText" />
        <el-table-column prop="toLocationName" label="调入位置" min-width="120" show-overflow-tooltip :formatter="formatText" />
        <el-table-column prop="toDepartment" label="调入部门" min-width="120" show-overflow-tooltip :formatter="formatText" />
        <el-table-column label="资产数" width="76" align="center">
          <template #default="{ row }">{{ itemCount(row) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="调拨原因" min-width="150" show-overflow-tooltip :formatter="formatText" />
        <el-table-column label="处理人" width="110">
          <template #default="{ row }">{{ confirmerText(row) }}</template>
        </el-table-column>
        <el-table-column prop="confirmTime" label="处理时间" min-width="160" :formatter="formatText" />
        <el-table-column prop="createdAt" label="申请时间" min-width="160" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

    <!-- 发起调拨 -->
    <TransferApplyModal v-model:visible="modalVisible" @success="handleApplied" />

    <!-- 调拨详情（含确认/拒绝/撤销） -->
    <TransferDetailDrawer v-model:visible="drawerVisible" :transfer="currentTransfer" @updated="handleUpdated" />
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

/* 双击行可查看详情：指针光标提示可交互 */
:deep(.el-table__row) {
  cursor: pointer;
}
</style>
