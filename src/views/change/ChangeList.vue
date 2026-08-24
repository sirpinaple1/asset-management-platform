<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChangeStore } from '@/stores/change'
import { useTabsStore } from '@/stores/tabs'
import { changeApi } from '@/api/modules/change'
import type { ChangeOrder, ChangeStatus } from '@/api/interface/change'
import { CHANGE_STATUS_META, CHANGE_FIELD_META } from '@/api/interface/change'
import ChangeApplyModal from './components/ChangeApplyModal.vue'
import ChangeDetailDrawer from './components/ChangeDetailDrawer.vue'

/**
 * M06 实物信息变更单（AOC）列表：发起 → 确认执行 / 撤销。
 * 状态 tabs + 关键词搜索 + 前端分页 + 深链 query 同步；
 * keep-alive 缓存由本组件 defineOptions name（与路由 name 一致）决定。
 */
defineOptions({ name: 'changes-list' })

const route = useRoute()
const router = useRouter()
const tabsStore = useTabsStore()
const store = useChangeStore()

const changeOrders = computed(() => store.changeOrders)
const loading = computed(() => store.loading)

/* ---------------- 状态 tabs（前端过滤 + 计数） ---------------- */
type TabKey = 'ALL' | ChangeStatus
const activeTab = ref<TabKey>('ALL')

const statusCount = (status: ChangeStatus) =>
  changeOrders.value.filter((c) => c.status === status).length

const tabs = computed(() => [
  { key: 'ALL' as TabKey, label: '全部', count: changeOrders.value.length },
  { key: 'PENDING' as TabKey, label: '待确认', count: statusCount('PENDING') },
  { key: 'CONFIRMED' as TabKey, label: '已执行', count: statusCount('CONFIRMED') },
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

/** 汇总单据涉及的变更字段（去重，用于列表"变更内容"列） */
const changeSummary = (row: ChangeOrder) => {
  if (!row.items?.length) return '—'
  const labels = [...new Set(row.items.map((it) => it.fieldLabel || CHANGE_FIELD_META[it.fieldName]?.label || it.fieldName))]
  return labels.join('、')
}

const filtered = computed(() => {
  let list = changeOrders.value
  if (activeTab.value !== 'ALL') list = list.filter((c) => c.status === activeTab.value)
  const kw = searchKeyword.value.toLowerCase()
  if (kw) {
    list = list.filter((c) =>
      [c.serialNo, c.applicantName, c.reason, changeSummary(c)].some((field) =>
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
    if (route.name !== 'changes-list') return
    const tab = typeof q.tab === 'string' ? q.tab : ''
    const validTabs = ['ALL', 'PENDING', 'CONFIRMED', 'CANCELLED']
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
  if (route.name !== 'changes-list') return
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
onMounted(() => store.fetchChangeOrders())
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  store.fetchChangeOrders()
})

/* ---------------- 新建申请 ---------------- */
const modalVisible = ref(false)

/* 深链发起：?compose=1 自动打开发起弹窗（工作台快捷入口；用后清洗） */
watch(
  () => route.query.compose,
  (v) => {
    if (route.name !== 'changes-list' || v !== '1') return
    router.replace({ query: { ...route.query, compose: undefined } })
    modalVisible.value = true
  },
  { immediate: true },
)

/* 弹窗打开 = 有未提交内容：关页签前需确认 */
watch(modalVisible, (v) => tabsStore.setDirty(route.fullPath, v))

const handleApplied = (item: ChangeOrder) => store.unshiftLocal(item)

/* ---------------- 详情与流转操作 ---------------- */
const drawerVisible = ref(false)
const currentChange = ref<ChangeOrder>()

const openDetail = (record: ChangeOrder) => {
  currentChange.value = record
  drawerVisible.value = true
}

/* 深链定位：?id=123 打开对应单据详情（审批中心跳转入口；watch 兼容 keep-alive 缓存后二次深链） */
watch(
  () => route.query.id,
  async (v) => {
    if (route.name !== 'changes-list') return
    const id = Number(v)
    if (!Number.isInteger(id) || id <= 0) return
    router.replace({ query: { ...route.query, id: undefined } })
    try {
      openDetail(await changeApi.getChangeOrderById(id))
    } catch {
      /* 404 已由拦截器提示 */
    }
  },
  { immediate: true },
)

/* 详情抽屉内确认执行/撤销成功：列表原地更新 */
const handleUpdated = (item: ChangeOrder) => store.upsertLocal(item)

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
const statusLabel = (row: ChangeOrder) =>
  row.statusLabel || CHANGE_STATUS_META[row.status].label

const applicantText = (row: ChangeOrder) =>
  row.applicantName || String(row.applicantUserId)

const confirmerText = (row: ChangeOrder) =>
  row.confirmerName || (row.confirmerUserId ? String(row.confirmerUserId) : '—')

/** 涉及资产数（明细按"资产×字段"展开，列表行去重计数） */
const assetCount = (row: ChangeOrder) =>
  row.items === undefined ? '—' : String(new Set(row.items.map((it) => it.assetId)).size)
</script>

<template>
  <div class="page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">实物信息变更</h2>
        <span class="page-subtitle">变更单（单号前缀 AOC）：发起 → 确认执行后更新资产实物信息（归属部门/使用人/区域/存放地点/公司）</span>
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

      <!-- 工具栏：发起变更 + 搜索 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" @click="modalVisible = true">
            <span class="btn-plus">+</span>
            <span>发起变更</span>
          </el-button>
        </div>
        <el-input
          ref="searchInputEl"
          v-model="keyword"
          class="search-box"
          placeholder="搜索单号 / 发起人 / 变更内容 / 原因（Ctrl+F）"
          clearable
        />
      </div>

      <!-- 变更单表格：双击行开详情 -->
      <el-table
        v-loading="loading"
        :data="pageData"
        row-key="id"
        border
        highlight-current-row
        empty-text="暂无变更单数据"
        @row-dblclick="openDetail"
      >
        <el-table-column prop="serialNo" label="单号" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="CHANGE_STATUS_META[row.status as ChangeStatus].tagType" effect="light">
              {{ statusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发起人" min-width="110">
          <template #default="{ row }">{{ applicantText(row) }}</template>
        </el-table-column>
        <el-table-column label="变更内容" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ changeSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="资产数" width="76" align="center">
          <template #default="{ row }">{{ assetCount(row) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="变更原因" min-width="150" show-overflow-tooltip :formatter="formatText" />
        <el-table-column label="执行人" width="110">
          <template #default="{ row }">{{ confirmerText(row) }}</template>
        </el-table-column>
        <el-table-column prop="confirmTime" label="执行时间" min-width="160" :formatter="formatText" />
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

    <!-- 发起变更 -->
    <ChangeApplyModal v-model:visible="modalVisible" @success="handleApplied" />

    <!-- 变更详情（含确认执行/撤销） -->
    <ChangeDetailDrawer v-model:visible="drawerVisible" :change-order="currentChange" @updated="handleUpdated" />
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
