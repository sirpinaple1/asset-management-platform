<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { useReceiptStore } from '@/stores/receipt'
import { useTabsStore } from '@/stores/tabs'
import { receiptApi } from '@/api/modules/receipt'
import type { Allocation, ReceiveReceipt, ReceiptStatus, ReceiptType } from '@/api/interface/receipt'
import { RECEIPT_STATUS_META, RECEIPT_TYPE_META } from '@/api/interface/receipt'
import ReceiptApplyModal from './components/ReceiptApplyModal.vue'
import ReceiptDetailDrawer from './components/ReceiptDetailDrawer.vue'

/**
 * M04 领用/借用单列表（通用组件：领用&退库 / 借用&归还 两菜单按 type 复用，
 * keep-alive 缓存由薄包装页面的 defineOptions name 决定）。
 * 双视图：单据记录（申请→审批流） / 持有中资产（退库/归还操作）。
 */
const props = defineProps<{
  type: ReceiptType
}>()

const route = useRoute()
const router = useRouter()
const tabsStore = useTabsStore()
const store = useReceiptStore()

/** 与路由 name 一致（由薄包装页面持有，此处用于 query 守卫） */
const routeName = computed(() => `receipts-${props.type === 'RECEIVE' ? 'receive' : 'borrow'}`)
const typeMeta = computed(() => RECEIPT_TYPE_META[props.type])
const pageTitle = computed(() =>
  props.type === 'RECEIVE' ? '领用&退库' : '借用&归还',
)
/** 归还操作文案：领用=退库，借用=归还 */
const returnLabel = computed(() => (props.type === 'RECEIVE' ? '退库' : '归还'))

const receipts = computed(() => store.receipts)
const loading = computed(() => store.loading)
const allocations = computed(() => store.allocations)
const allocationsLoading = computed(() => store.allocationsLoading)

/* ---------------- 视图切换：单据记录 / 持有中资产 ---------------- */
type ViewKey = 'receipts' | 'allocations'
const view = ref<ViewKey>('receipts')
const viewOptions = computed(() => [
  { value: 'receipts' as ViewKey, label: '单据记录' },
  { value: 'allocations' as ViewKey, label: `持有中资产` },
])

const switchView = (v: ViewKey) => {
  view.value = v
  activeTab.value = 'ALL'
  activeAllocTab.value = 'ALL'
  currentPage.value = 1
}

/* ---------------- 单据视图：状态 tabs（前端过滤 + 计数） ---------------- */
type TabKey = 'ALL' | ReceiptStatus
const activeTab = ref<TabKey>('ALL')

const statusCount = (status: ReceiptStatus) =>
  receipts.value.filter((r) => r.status === status).length

const tabs = computed(() => [
  { key: 'ALL' as TabKey, label: '全部', count: receipts.value.length },
  { key: 'PENDING' as TabKey, label: '待审批', count: statusCount('PENDING') },
  { key: 'APPROVED' as TabKey, label: '已批准', count: statusCount('APPROVED') },
  { key: 'REJECTED' as TabKey, label: '已拒绝', count: statusCount('REJECTED') },
])

/* ---------------- 持有中视图：active tabs ---------------- */
type AllocTabKey = 'ALL' | 'ACTIVE' | 'RETURNED'
const activeAllocTab = ref<AllocTabKey>('ALL')

const activeCount = computed(() => allocations.value.filter((a) => a.active).length)

const allocTabs = computed(() => [
  { key: 'ALL' as AllocTabKey, label: '全部', count: allocations.value.length },
  { key: 'ACTIVE' as AllocTabKey, label: '持有中', count: activeCount.value },
  { key: 'RETURNED' as AllocTabKey, label: '已归还', count: allocations.value.length - activeCount.value },
])

/* ---------------- 搜索（防抖 300ms 前端过滤，两视图共用） ---------------- */
const keyword = ref('')
const searchKeyword = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

const searchPlaceholder = computed(() =>
  view.value === 'receipts'
    ? '搜索单号 / 申请人 / 部门 / 事由（Ctrl+F）'
    : `搜索资产编码 / 名称 / 持有人（Ctrl+F）`,
)

watch(keyword, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKeyword.value = val.trim()
    currentPage.value = 1
  }, 300)
})
onBeforeUnmount(() => searchTimer && clearTimeout(searchTimer))

/* ---------------- 前端过滤 + 分页（两视图共用页码） ---------------- */
const PAGE_SIZE = 10
const currentPage = ref(1)

const receiptFiltered = computed(() => {
  let list = receipts.value
  if (activeTab.value !== 'ALL') list = list.filter((r) => r.status === activeTab.value)
  const kw = searchKeyword.value.toLowerCase()
  if (kw) {
    list = list.filter((r) =>
      [r.serialNo, r.applicantName, r.department, r.reason].some((field) =>
        field?.toLowerCase().includes(kw),
      ),
    )
  }
  return list
})

const allocationFiltered = computed(() => {
  let list = allocations.value
  if (activeAllocTab.value === 'ACTIVE') list = list.filter((a) => a.active)
  else if (activeAllocTab.value === 'RETURNED') list = list.filter((a) => !a.active)
  const kw = searchKeyword.value.toLowerCase()
  if (kw) {
    list = list.filter((a) =>
      [a.assetBarcode, a.assetName, a.assetSn, a.userName, a.department].some((field) =>
        field?.toLowerCase().includes(kw),
      ),
    )
  }
  return list
})

const total = computed(() =>
  view.value === 'receipts' ? receiptFiltered.value.length : allocationFiltered.value.length,
)

const pageData = computed(() => {
  const source = view.value === 'receipts' ? receiptFiltered.value : allocationFiltered.value
  return source.slice((currentPage.value - 1) * PAGE_SIZE, currentPage.value * PAGE_SIZE)
})

/* tab/视图/过滤结果变化时纠正越界页码 */
watch([activeTab, activeAllocTab, view, total], () => {
  const maxPage = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
  if (currentPage.value > maxPage) currentPage.value = maxPage
})

/* ---------------- 深链与刷新保持：view/tab/搜索词/页码同步 URL query ---------------- */
watch(
  () => route.query,
  (q) => {
    if (route.name !== routeName.value) return
    const v = q.view === 'allocations' ? 'allocations' : 'receipts'
    if (v !== view.value) view.value = v

    const tab = typeof q.tab === 'string' ? q.tab : ''
    const validTabs = ['ALL', 'PENDING', 'APPROVED', 'REJECTED']
    const validAllocTabs = ['ALL', 'ACTIVE', 'RETURNED']
    const nextTab = validTabs.includes(tab) ? (tab as TabKey) : 'ALL'
    if (nextTab !== activeTab.value) activeTab.value = nextTab
    const nextAllocTab = validAllocTabs.includes(tab) ? (tab as AllocTabKey) : 'ALL'
    if (nextAllocTab !== activeAllocTab.value) activeAllocTab.value = nextAllocTab

    const kw = typeof q.q === 'string' ? q.q : ''
    if (kw !== keyword.value) keyword.value = kw
    const p = Number(q.page)
    if (Number.isInteger(p) && p >= 1 && p !== currentPage.value) currentPage.value = p
  },
  { immediate: true },
)

watch([view, activeTab, activeAllocTab, searchKeyword, currentPage], () => {
  if (route.name !== routeName.value) return
  const query: Record<string, string> = {}
  if (view.value === 'allocations') query.view = 'allocations'
  const tab = view.value === 'receipts' ? activeTab.value : activeAllocTab.value
  if (tab !== 'ALL') query.tab = tab
  if (searchKeyword.value) query.q = searchKeyword.value
  if (currentPage.value > 1) query.page = String(currentPage.value)
  const current = JSON.stringify(route.query)
  const next = JSON.stringify(query)
  if (current !== next) router.replace({ query })
})

/* ---------------- 数据加载 ---------------- */
const loadData = () =>
  view.value === 'receipts'
    ? store.fetchReceipts(props.type)
    : store.fetchAllocations(props.type)

/* keep-alive：首次 onMounted 拉取；之后每次切回本页刷新（状态可能已被他人变更） */
let firstActivation = true
onMounted(loadData)
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  loadData()
})

/* 切视图按需拉取（另一视图可能从未加载过） */
watch(view, (v) => {
  if (v === 'receipts' && !receipts.value.length && !loading.value) store.fetchReceipts(props.type)
  if (v === 'allocations' && !allocations.value.length && !allocationsLoading.value)
    store.fetchAllocations(props.type)
})

/* ---------------- 新建申请 ---------------- */
const modalVisible = ref(false)

/* 弹窗打开 = 有未提交内容：关页签前需确认 */
watch(modalVisible, (v) => tabsStore.setDirty(route.fullPath, v))

const handleApplied = (item: ReceiveReceipt) => store.unshiftLocal(item)

/* ---------------- 详情与审批 ---------------- */
const drawerVisible = ref(false)
const currentReceipt = ref<ReceiveReceipt>()

const openDetail = (record: ReceiveReceipt) => {
  currentReceipt.value = record
  drawerVisible.value = true
}

/* 深链定位：?id=123 打开对应单据详情（审批中心跳转入口；watch 兼容 keep-alive 缓存后二次深链） */
watch(
  () => route.query.id,
  async (v) => {
    if (route.name !== routeName.value) return
    const id = Number(v)
    if (!Number.isInteger(id) || id <= 0) return
    router.replace({ query: { ...route.query, id: undefined } })
    try {
      openDetail(await receiptApi.getReceiptById(id))
    } catch {
      /* 404 已由拦截器提示 */
    }
  },
  { immediate: true },
)

/* 详情抽屉内审批成功：列表原地更新 */
const handleUpdated = (item: ReceiveReceipt) => store.upsertLocal(item)

/* 审批通过后持有关系已生成：切到持有视图时强制重拉 */
watch(drawerVisible, (v) => {
  if (!v) store.fetchAllocations(props.type).catch(() => {})
})

/* ---------------- 退库 / 归还 ---------------- */
const returningId = ref<number>()

const handleReturn = async (row: Allocation) => {
  if (returningId.value) return /* 防重复提交 */
  let note = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `确认对资产「${row.assetBarcode || ''} ${row.assetName || row.assetId}」执行${returnLabel.value}吗？${returnLabel.value}后资产回闲置。`,
      `${returnLabel.value}确认`,
      {
        confirmButtonText: returnLabel.value,
        cancelButtonText: '取消',
        inputPlaceholder: '备注（选填）',
        inputValidator: () => true,
      },
    )
    note = value.trim()
  } catch {
    return /* 用户取消 */
  }
  returningId.value = row.id
  try {
    await receiptApi.returnAllocation(row.id, note || undefined)
    store.markReturnedLocal(row.id)
    ElMessage.success(`${returnLabel.value}成功`)
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
  } finally {
    returningId.value = undefined
  }
}

/* 详情/申请弹窗打开时挂起表格快捷键 */
const anyOverlayOpen = computed(() => modalVisible.value || drawerVisible.value)
watch(anyOverlayOpen, (v) => tabsStore.setDirty(route.fullPath, v))

/* ---------------- 输入与触发：双击详情 / Ctrl+F 聚焦搜索 ---------------- */
const tableRef = ref<TableInstance>()
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
const statusLabel = (row: ReceiveReceipt) =>
  row.statusLabel || RECEIPT_STATUS_META[row.status].label

const itemCount = (row: ReceiveReceipt) =>
  row.items === undefined ? '—' : String(row.items.length)

const applicantText = (row: ReceiveReceipt) =>
  row.applicantName || String(row.applicantUserId)

const approverText = (row: ReceiveReceipt) =>
  row.approverName || (row.approverUserId ? String(row.approverUserId) : '—')

/** 持有人展示：人持有→姓名/ID；部门持有（userId 空，M05 调拨只填部门）→部门（部门持有） */
const holderText = (row: Allocation) =>
  row.userId == null
    ? row.department
      ? `${row.department}（部门持有）`
      : '—'
    : row.userName || String(row.userId)
</script>

<template>
  <div class="page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">{{ pageTitle }}</h2>
        <span class="page-subtitle">{{ typeMeta.label }}单（单号前缀 {{ typeMeta.serialPrefix }}）</span>
        <el-radio-group
          :model-value="view"
          size="small"
          class="view-switch"
          @update:model-value="switchView"
        >
          <el-radio-button
            v-for="opt in viewOptions"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <!-- ===== 单据视图 ===== -->
      <template v-if="view === 'receipts'">
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

        <!-- 工具栏：发起申请 + 搜索 -->
        <div class="toolbar">
          <div class="toolbar-left">
            <el-button type="primary" @click="modalVisible = true">
              <span class="btn-plus">+</span>
              <span>发起{{ typeMeta.label }}申请</span>
            </el-button>
          </div>
          <el-input
            ref="searchInputEl"
            v-model="keyword"
            class="search-box"
            :placeholder="searchPlaceholder"
            clearable
          />
        </div>

        <!-- 单据表格：双击行开详情 -->
        <el-table
          v-loading="loading"
          :data="pageData as ReceiveReceipt[]"
          row-key="id"
          border
          highlight-current-row
          empty-text="暂无单据数据"
          @row-dblclick="openDetail"
        >
          <el-table-column prop="serialNo" label="单号" min-width="150" show-overflow-tooltip />
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <el-tag :type="RECEIPT_STATUS_META[row.status as ReceiptStatus].tagType" effect="light">
                {{ statusLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请人" min-width="110">
            <template #default="{ row }">{{ applicantText(row) }}</template>
          </el-table-column>
          <el-table-column prop="department" label="部门" min-width="120" :formatter="formatText" />
          <el-table-column prop="locationName" label="领用区域" min-width="120" show-overflow-tooltip :formatter="formatText" />
          <el-table-column label="资产数" width="76" align="center">
            <template #default="{ row }">{{ itemCount(row) }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="事由" min-width="160" show-overflow-tooltip :formatter="formatText" />
          <el-table-column label="审批人" width="110">
            <template #default="{ row }">{{ approverText(row) }}</template>
          </el-table-column>
          <el-table-column prop="approveTime" label="审批时间" min-width="160" :formatter="formatText" />
          <el-table-column prop="createdAt" label="申请时间" min-width="160" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- ===== 持有中视图：退库 / 归还 ===== -->
      <template v-else>
        <!-- 持有状态 tabs -->
        <div class="tabs">
          <div
            v-for="tab in allocTabs"
            :key="tab.key"
            class="tab"
            :class="{ active: activeAllocTab === tab.key }"
            @click="activeAllocTab = tab.key"
          >
            <span>{{ tab.label }}</span>
            <span class="tab-count">({{ tab.count }})</span>
          </div>
        </div>

        <!-- 工具栏：搜索 -->
        <div class="toolbar">
          <div class="toolbar-left">
            <span class="toolbar-hint">审批通过的{{ typeMeta.label }}资产在此{{ returnLabel }}</span>
          </div>
          <el-input
            ref="searchInputEl"
            v-model="keyword"
            class="search-box"
            :placeholder="searchPlaceholder"
            clearable
          />
        </div>

        <!-- 持有关系表格 -->
        <el-table
          v-loading="allocationsLoading"
          :data="pageData as Allocation[]"
          row-key="id"
          border
          empty-text="暂无持有资产"
        >
          <el-table-column prop="assetBarcode" label="资产编码" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.assetBarcode || String(row.assetId) }}</template>
          </el-table-column>
          <el-table-column prop="assetName" label="资产名称" min-width="160" show-overflow-tooltip :formatter="formatText" />
          <el-table-column prop="assetSn" label="序列号" min-width="130" show-overflow-tooltip :formatter="formatText" />
          <el-table-column label="持有人" min-width="110">
            <template #default="{ row }">{{ holderText(row) }}</template>
          </el-table-column>
          <el-table-column prop="department" label="部门" min-width="120" :formatter="formatText" />
          <el-table-column prop="allocatedAt" label="发放时间" min-width="160" />
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <el-tag :type="row.active ? 'success' : 'info'" effect="light">
                {{ row.active ? '持有中' : '已归还' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="returnedAt" label="归还时间" min-width="160" :formatter="formatText" />
          <el-table-column prop="note" label="备注" min-width="140" show-overflow-tooltip :formatter="formatText" />
          <el-table-column label="操作" width="96" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.active"
                link
                type="warning"
                :loading="returningId === row.id"
                @click="handleReturn(row)"
              >
                {{ returnLabel }}
              </el-button>
              <span v-else class="op-done">已{{ returnLabel }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>

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

    <!-- 发起申请 -->
    <ReceiptApplyModal v-model:visible="modalVisible" :type="type" @success="handleApplied" />

    <!-- 单据详情（含审批） -->
    <ReceiptDetailDrawer v-model:visible="drawerVisible" :receipt="currentReceipt" @updated="handleUpdated" />
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

.view-switch {
  flex-shrink: 0;
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

.toolbar-hint {
  font-size: 13px;
  color: #86909c;
}

.btn-plus {
  font-size: 15px;
  line-height: 1;
  margin-right: 2px;
}

.search-box {
  width: 280px;
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

/* 持有视图行无详情交互：恢复默认光标 */
:deep(.el-table__row):has(.op-done) {
  cursor: default;
}

.op-done {
  font-size: 13px;
  color: #c0c4cc;
}
</style>
