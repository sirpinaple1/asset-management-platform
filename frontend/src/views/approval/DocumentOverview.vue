<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Component } from 'vue'
import { useUserStore } from '@/stores/user'
import { fetchAllDocumentItems } from '@/api/modules/document'
import type { DocumentBizType, DocumentItem, DocStatusGroup } from '@/api/interface/document'
import { DOC_TYPE_META, docStatusGroupOf, isAllocReturn } from '@/api/interface/document'
import { APPROVAL_BIZ_META, approvalStatusTag } from '@/api/interface/approval'
import IconDocReceive from '@/components/icons/IconDocReceive.vue'
import IconDocBorrow from '@/components/icons/IconDocBorrow.vue'
import IconDocTransfer from '@/components/icons/IconDocTransfer.vue'
import IconDocChange from '@/components/icons/IconDocChange.vue'
import IconDocReturn from '@/components/icons/IconDocReturn.vue'

/**
 * 全部单据（超管总览）：全系统单据统一视图，不按当前登录用户隔离。
 * 覆盖六类记录——领用/借用/调拨/变更/钉钉退还（复用审批中心聚合）+ 退库归还（持有关系闭环）；
 * 状态 tabs + 类型筛选 + 关键词搜索 + 前端分页 + 深链 query 同步；
 * 仅 systemAdmin 可见（菜单已隐藏，此为页内第二道防线）。
 */
defineOptions({ name: 'approvals-all' })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/* ---------------- 超管门禁（me 未加载前不渲染内容，非超管提示无权限） ---------------- */
const isSuperAdmin = computed(() => userStore.me?.roles?.includes('systemAdmin') ?? false)
const meLoaded = computed(() => userStore.me !== null)

/* ---------------- 数据加载（keep-alive：切回本页刷新） ---------------- */
const items = ref<DocumentItem[]>([])
const loading = ref(false)

/** 请求序号：快速连续触发时只保留最新一次请求的结果（竞态保护） */
let fetchSeq = 0

const load = async () => {
  if (!isSuperAdmin.value) return
  const seq = ++fetchSeq
  loading.value = true
  try {
    const list = await fetchAllDocumentItems()
    if (seq !== fetchSeq) return
    items.value = list
  } catch {
    /* 拦截器已提示 */
  } finally {
    if (seq === fetchSeq) loading.value = false
  }
}

let firstActivation = true
onMounted(async () => {
  try {
    await userStore.loadMe()
  } catch {
    /* 401/403/503 已由 axios 拦截器统一提示/跳转 */
  }
  void load()
})
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  void load()
})

/* ---------------- 状态 tabs（前端过滤 + 计数） ---------------- */
type StatusKey = 'ALL' | DocStatusGroup
const STATUS_KEYS: StatusKey[] = ['ALL', 'PENDING', 'PASSED', 'RETURNED', 'REJECTED', 'CANCELLED']
const STATUS_LABELS: Record<StatusKey, string> = {
  ALL: '全部',
  PENDING: '进行中',
  PASSED: '已通过',
  RETURNED: '已退还',
  REJECTED: '已拒绝',
  CANCELLED: '已取消',
}
const activeStatus = ref<StatusKey>('ALL')

const statusTabs = computed(() =>
  STATUS_KEYS.map((key) => ({
    key,
    label: STATUS_LABELS[key],
    count:
      key === 'ALL'
        ? items.value.length
        : items.value.filter((it) => docStatusGroupOf(it) === key).length,
  })),
)

/* ---------------- 类型筛选 chips ---------------- */
type TypeKey = 'ALL' | DocumentBizType
const DOC_TYPE_KEYS: DocumentBizType[] = [
  'RECEIVE',
  'BORROW',
  'TRANSFER',
  'CHANGE',
  'RETURN',
  'ALLOC_RETURN',
]
const TYPE_KEYS: TypeKey[] = ['ALL', ...DOC_TYPE_KEYS]
const activeType = ref<TypeKey>('ALL')

const typeChips = computed(() => [
  { key: 'ALL' as TypeKey, label: '全部类型' },
  ...DOC_TYPE_KEYS.map((key) => ({ key: key as TypeKey, label: DOC_TYPE_META[key].label })),
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
const PAGE_SIZE = 20
const currentPage = ref(1)

/** 各类型参与关键词匹配的字段（钉钉退还按 title→serialNo 匹配） */
const searchFields = (it: DocumentItem): (string | undefined)[] =>
  isAllocReturn(it)
    ? [it.assetBarcode, it.assetName, it.holderName, it.summary]
    : [it.serialNo, it.applicantName, it.summary]

const filtered = computed(() => {
  let list = items.value
  if (activeStatus.value !== 'ALL') {
    list = list.filter((it) => docStatusGroupOf(it) === activeStatus.value)
  }
  if (activeType.value !== 'ALL') list = list.filter((it) => it.bizType === activeType.value)
  const kw = searchKeyword.value.toLowerCase()
  if (kw) {
    list = list.filter((it) =>
      searchFields(it).some((field) => field?.toLowerCase().includes(kw)),
    )
  }
  return list
})

const total = computed(() => filtered.value.length)

const pageData = computed(() =>
  filtered.value.slice((currentPage.value - 1) * PAGE_SIZE, currentPage.value * PAGE_SIZE),
)

/* tab/过滤结果变化时纠正越界页码 */
watch([activeStatus, activeType, total], () => {
  const maxPage = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
  if (currentPage.value > maxPage) currentPage.value = maxPage
})

/* ---------------- 深链与刷新保持：状态/类型/搜索词/页码同步 URL query ---------------- */
watch(
  () => route.query,
  (q) => {
    if (route.name !== 'approvals-all') return
    const status = typeof q.status === 'string' ? q.status : ''
    if (STATUS_KEYS.includes(status as StatusKey)) {
      const next = status as StatusKey
      if (next !== activeStatus.value) activeStatus.value = next
    }
    const type = typeof q.type === 'string' ? q.type : ''
    if (TYPE_KEYS.includes(type as TypeKey)) {
      const next = type as TypeKey
      if (next !== activeType.value) activeType.value = next
    }
    const kw = typeof q.q === 'string' ? q.q : ''
    if (kw !== keyword.value) keyword.value = kw
    const p = Number(q.page)
    if (Number.isInteger(p) && p >= 1 && p !== currentPage.value) currentPage.value = p
  },
  { immediate: true },
)

watch([activeStatus, activeType, searchKeyword, currentPage], () => {
  if (route.name !== 'approvals-all') return
  const query: Record<string, string> = {}
  if (activeStatus.value !== 'ALL') query.status = activeStatus.value
  if (activeType.value !== 'ALL') query.type = activeType.value
  if (searchKeyword.value) query.q = searchKeyword.value
  if (currentPage.value > 1) query.page = String(currentPage.value)
  const current = JSON.stringify(route.query)
  const next = JSON.stringify(query)
  if (current !== next) router.replace({ query })
})

/* ---------------- 展示工具 ---------------- */

const BIZ_ICONS: Record<DocumentBizType, Component> = {
  RECEIVE: IconDocReceive,
  BORROW: IconDocBorrow,
  TRANSFER: IconDocTransfer,
  CHANGE: IconDocChange,
  RETURN: IconDocReturn,
  ALLOC_RETURN: IconDocReturn,
}

/** 类型列：退库/归还按 returnKind 细分，其余用类型名 */
const typeLabel = (row: DocumentItem) =>
  isAllocReturn(row) ? row.returnKind : DOC_TYPE_META[row.bizType].label

const serialText = (row: DocumentItem) =>
  isAllocReturn(row) ? row.assetBarcode || '—' : row.serialNo

/** 发起人 / 退还人（快照缺失时回退展示 ID；钉钉退还未绑定内部账号显示 —） */
const personText = (row: DocumentItem) => {
  if (isAllocReturn(row)) {
    return row.holderName || (row.holderUserId ? String(row.holderUserId) : '—')
  }
  if (row.applicantUserId === 0) return '—'
  return row.applicantName || String(row.applicantUserId)
}

const timeText = (row: DocumentItem) => (isAllocReturn(row) ? row.returnedAt : row.createdAt)

/** 状态 tag：退库/归还固定 success 终态，其余复用审批中心状态映射（含钉钉退还） */
const statusTagOf = (row: DocumentItem) => {
  if (isAllocReturn(row)) {
    return { label: row.returnKind === '归还' ? '已归还' : '已退库', tagType: 'success' as const }
  }
  return approvalStatusTag(row)
}

/** 行 key：类型 + 单据/持有关系 ID（不同来源 ID 空间可能撞号） */
const rowKey = (row: DocumentItem) => `${row.bizType}-${row.bizId}`

/* 详情：四类单据深链各列表页抽屉；退库/归还跳资产详情；钉钉退还无系统单据不跳转 */
const goDetail = (row: DocumentItem) => {
  if (isAllocReturn(row)) {
    router.push({ path: '/assets', query: { id: String(row.assetId) } })
    return
  }
  const meta = APPROVAL_BIZ_META[row.bizType]
  if (!meta.listPath) return
  router.push({ path: meta.listPath, query: { id: String(row.bizId) } })
}

const emptyText = computed(() =>
  activeStatus.value === 'ALL' ? '暂无单据记录' : `暂无${STATUS_LABELS[activeStatus.value]}的单据`,
)

/* ---------------- 输入触发：Ctrl+F 聚焦搜索 ---------------- */
const searchInputEl = ref<HTMLElement>()

const onWindowKeydown = (e: KeyboardEvent) => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') {
    e.preventDefault()
    searchInputEl.value?.querySelector('input')?.focus()
  }
}
onMounted(() => window.addEventListener('keydown', onWindowKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onWindowKeydown))
</script>

<template>
  <div class="page">
    <!-- 非超管：第二道防线（菜单已隐藏，此为直链访问兜底） -->
    <div v-if="!isSuperAdmin && meLoaded" class="page-container">
      <el-alert title="仅系统管理员可访问" type="warning" :closable="false" show-icon />
    </div>

    <!-- me 未加载：骨架占位（避免超管首帧闪"无权限"） -->
    <div v-else-if="!isSuperAdmin" v-loading="true" class="page-container gate-loading"></div>

    <div v-else class="page-container">
      <div class="page-header">
        <h2 class="page-title">全部单据</h2>
        <span class="page-subtitle">
          全系统单据总览（领用 / 借用 / 调拨 / 变更 / 退库归还）· 不按当前用户隔离，仅系统管理员可见
        </span>
      </div>

      <!-- 状态 tabs -->
      <div class="tabs">
        <div
          v-for="tab in statusTabs"
          :key="tab.key"
          class="tab"
          :class="{ active: activeStatus === tab.key }"
          @click="activeStatus = tab.key"
        >
          <span>{{ tab.label }}</span>
          <span class="tab-count">({{ tab.count }})</span>
        </div>
      </div>

      <!-- 工具栏：类型筛选 chips + 搜索 -->
      <div class="toolbar">
        <div class="type-chips">
          <button
            v-for="chip in typeChips"
            :key="chip.key"
            type="button"
            class="type-chip"
            :class="{ active: activeType === chip.key }"
            @click="activeType = chip.key"
          >
            {{ chip.label }}
          </button>
        </div>
        <el-input
          ref="searchInputEl"
          v-model="keyword"
          class="search-box"
          placeholder="搜索单号 / 申请人 / 摘要（Ctrl+F）"
          clearable
        />
      </div>

      <!-- 聚合表格 -->
      <el-table
        v-loading="loading"
        :data="pageData"
        :row-key="rowKey"
        border
        highlight-current-row
        :empty-text="emptyText"
      >
        <el-table-column label="类型" width="92">
          <template #default="{ row }">
            <span class="biz-cell">
              <component :is="BIZ_ICONS[row.bizType as DocumentBizType]" :size="16" />
              <span>{{ typeLabel(row) }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="单号 / 资产编码" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ serialText(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="statusTagOf(row).tagType" effect="light">
              {{ statusTagOf(row).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发起人 / 退还人" min-width="120">
          <template #default="{ row }">{{ personText(row) }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
        <el-table-column label="申请 / 归还时间" min-width="160">
          <template #default="{ row }">{{ timeText(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <!-- 钉钉退还单无系统单据，不提供详情跳转 -->
            <el-button v-if="row.bizType !== 'RETURN'" link type="primary" @click="goDetail(row)">
              详情
            </el-button>
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
  </div>
</template>

<style scoped>
.page-container {
  background: var(--color-bg-2);
  border-radius: var(--radius-xl);
  padding: 24px;
  box-shadow: var(--shadow-md);
}

.gate-loading {
  min-height: 240px;
}

.page-header {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text-1);
  margin: 0;
}

.page-subtitle {
  font-size: var(--text-sm);
  color: var(--color-text-3);
  flex: 1;
}

/* 状态 tabs（对齐审批中心：胶囊样式） */
.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.tab {
  height: 34px;
  padding: 0 12px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--text-base);
  background: var(--color-bg-3);
  color: var(--color-text-2);
  cursor: pointer;
  user-select: none;
}

.tab.active {
  background: var(--color-primary);
  color: var(--color-text-inverse);
}

.tab-count {
  font-size: var(--text-sm);
}

/* 工具栏：类型 chips + 搜索 */
.toolbar {
  min-height: 56px;
  padding: 12px 16px;
  background: var(--color-bg-3);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.type-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.type-chip {
  height: 30px;
  padding: 0 12px;
  border-radius: 15px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-2);
  font-size: var(--text-sm);
  color: var(--color-text-2);
  cursor: pointer;
  transition: all 0.15s ease;
}

.type-chip:hover {
  color: var(--color-primary);
  border-color: var(--color-primary-border);
}

.type-chip.active {
  background: var(--color-primary-bg);
  border-color: var(--color-primary);
  color: var(--color-primary);
  font-weight: 500;
}

.search-box {
  width: 280px;
  flex-shrink: 0;
}

/* 类型列：图标 + 文字 */
.biz-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-2);
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.pagination-info {
  font-size: var(--text-sm);
  color: var(--color-text-2);
}
</style>
