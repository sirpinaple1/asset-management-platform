<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { stocktakeApi } from '@/api/modules/stocktake'
import { useBasedataStore } from '@/stores/basedata'
import { useUserStore } from '@/stores/user'
import type {
  Stocktake,
  StocktakeItem,
  StocktakeItemStatus,
  StocktakeReport,
} from '@/api/interface/stocktake'
import {
  STOCKTAKE_ITEM_STATUS_META,
  STOCKTAKE_STATUS_META,
} from '@/api/interface/stocktake'
import type { Location } from '@/api/interface/basedata'
import type { TransferOrder } from '@/api/interface/transfer'
import { TRANSFER_SOURCE_META } from '@/api/interface/transfer'
import { buildTree } from '@/utils/tree'

/**
 * 盘点任务详情抽屉：任务信息 + 五态实时统计 + 明细（五态筛选/人工确认/按条码扫码）
 * + 报告（汇总与差异明细）+ 流转操作（开始/完成/取消/生成调拨）。
 * 权限（对齐后端 M07）：取消仅创建人（403）；开始/扫码/完成不限创建人；
 * 生成调拨须已完成且未生成过（后端按 transfer_order.stocktake_id 防重 409）。
 */
const props = defineProps<{
  visible: boolean
  /** 盘点任务（列表行；打开后以详情接口数据为准） */
  stocktake?: Stocktake
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  /** 开始/取消/完成/生成调拨成功：回传最新任务，列表原地更新 */
  (e: 'updated', item: Stocktake): void
}>()

const userStore = useUserStore()
const basedataStore = useBasedataStore()

const detail = ref<Stocktake>()
const loading = ref(false)
const acting = ref(false)

/* ---------------- 报告 tab（进入报告页拉取，进行中可看实时统计） ---------------- */
const activeTab = ref<'items' | 'report'>('items')
const report = ref<StocktakeReport>()

const statusMeta = computed(() =>
  detail.value ? STOCKTAKE_STATUS_META[detail.value.status] : undefined,
)

/** 创建人本人（取消按钮出现条件，后端 403 校验） */
const isCreator = computed(
  () => !!detail.value && detail.value.creatorUserId === userStore.me?.userId,
)
const canStart = computed(() => detail.value?.status === 'PENDING')
const canCancel = computed(
  () =>
    !!detail.value &&
    (detail.value.status === 'PENDING' || detail.value.status === 'IN_PROGRESS') &&
    isCreator.value,
)
const inProgress = computed(() => detail.value?.status === 'IN_PROGRESS')
const canComplete = computed(() => detail.value?.status === 'IN_PROGRESS')
const canCreateTransfer = computed(
  () => detail.value?.status === 'COMPLETED' && (detail.value.mismatchCount ?? 0) > 0,
)

/* ---------------- 明细：五态筛选（前端过滤） + 分页 ---------------- */
type ItemTabKey = 'ALL' | StocktakeItemStatus
const itemTab = ref<ItemTabKey>('ALL')
const PAGE_SIZE = 10
const itemPage = ref(1)

const items = computed(() => detail.value?.items ?? [])

const itemStatusCount = (status: StocktakeItemStatus) =>
  items.value.filter((it) => it.status === status).length

const itemTabs = computed(() => [
  { key: 'ALL' as ItemTabKey, label: '全部', count: items.value.length },
  { key: 'PENDING' as ItemTabKey, label: '待盘', count: itemStatusCount('PENDING') },
  { key: 'MATCHED' as ItemTabKey, label: '账实相符', count: itemStatusCount('MATCHED') },
  { key: 'LOCATION_MISMATCH' as ItemTabKey, label: '位置不符', count: itemStatusCount('LOCATION_MISMATCH') },
  { key: 'NOT_FOUND' as ItemTabKey, label: '盘亏', count: itemStatusCount('NOT_FOUND') },
  { key: 'EXTRA' as ItemTabKey, label: '盘盈', count: itemStatusCount('EXTRA') },
])

const filteredItems = computed(() =>
  itemTab.value === 'ALL' ? items.value : items.value.filter((it) => it.status === itemTab.value),
)

const itemTotal = computed(() => filteredItems.value.length)

const pageItems = computed(() =>
  filteredItems.value.slice((itemPage.value - 1) * PAGE_SIZE, itemPage.value * PAGE_SIZE),
)

/* tab/过滤结果变化时纠正越界页码 */
watch([itemTab, itemTotal], () => {
  const maxPage = Math.max(1, Math.ceil(itemTotal.value / PAGE_SIZE))
  if (itemPage.value > maxPage) itemPage.value = maxPage
})

/* ---------------- 数据加载 ---------------- */
const loadDetail = async () => {
  if (!props.stocktake) return
  loading.value = true
  try {
    detail.value = await stocktakeApi.getStocktakeById(props.stocktake.id)
  } finally {
    loading.value = false
  }
}

/* 打开时拉取详情（含明细行与统计）；关闭时清态 */
watch(
  () => props.visible,
  async (val) => {
    if (val && props.stocktake) {
      detail.value = props.stocktake
      activeTab.value = 'items'
      itemTab.value = 'ALL'
      itemPage.value = 1
      report.value = undefined
      generatedTransfers.value = []
      barcodeInput.barcode = ''
      barcodeInput.locationId = undefined
      void loadDetail()
      if (!basedataStore.locations.length) void basedataStore.fetchLocations()
    } else if (!val) {
      detail.value = undefined
      report.value = undefined
      generatedTransfers.value = []
      confirmVisible.value = false
    }
  },
)

/* 进入报告 tab 时拉取报告（进行中 = 实时统计） */
watch(activeTab, (tab) => {
  if (tab === 'report' && detail.value) void loadReport()
})

const loadReport = async () => {
  if (!detail.value) return
  try {
    report.value = await stocktakeApi.getReport(detail.value.id)
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
  }
}

const handleClose = () => emit('update:visible', false)

/* ---------------- 流转操作 ---------------- */
/** 开始盘点：PENDING → IN_PROGRESS */
const handleStart = async () => {
  if (!detail.value || acting.value) return
  acting.value = true
  try {
    const updated = await stocktakeApi.start(detail.value.id)
    ElMessage.success('盘点已开始，可扫码或逐条确认明细')
    detail.value = updated
    emit('updated', updated)
    void loadDetail() /* 详情接口含明细与统计 */
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
  } finally {
    acting.value = false
  }
}

/** 取消盘点：仅 PENDING/IN_PROGRESS 且创建人（后端 403 校验），明细与资产不变 */
const handleCancel = async () => {
  if (!detail.value || acting.value) return
  try {
    await ElMessageBox.confirm(
      `确认取消盘点任务「${detail.value.name}」吗？明细与资产数据保持不变。`,
      '取消盘点',
      { type: 'warning', confirmButtonText: '取消任务', cancelButtonText: '再想想' },
    )
  } catch {
    return /* 用户取消 */
  }
  acting.value = true
  try {
    const updated = await stocktakeApi.cancel(detail.value.id)
    ElMessage.success('盘点任务已取消')
    detail.value = updated
    emit('updated', updated)
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
  } finally {
    acting.value = false
  }
}

/** 完成盘点：剩余待盘明细批量记盘亏并写资产日志 */
const handleComplete = async () => {
  if (!detail.value || acting.value) return
  const pending = detail.value.pendingCount ?? itemStatusCount('PENDING')
  try {
    await ElMessageBox.confirm(
      pending > 0
        ? `还有 ${pending} 台资产待盘，完成盘点将把它们批量记为盘亏（并逐台写资产日志）。确认完成吗？`
        : '全部明细已盘完，确认完成盘点吗？',
      '完成盘点',
      { type: 'warning', confirmButtonText: '完成盘点', cancelButtonText: '取消' },
    )
  } catch {
    return /* 用户取消 */
  }
  acting.value = true
  try {
    const updated = await stocktakeApi.complete(detail.value.id)
    ElMessage.success('盘点已完成')
    detail.value = updated
    emit('updated', updated)
    void loadDetail()
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
  } finally {
    acting.value = false
  }
}

/* ---------------- 生成调拨（位置不符归位） ---------------- */
const generating = ref(false)
/** 本次会话已生成的调拨单（成功后展示；重复生成由后端 409 防重） */
const generatedTransfers = ref<TransferOrder[]>([])

const handleCreateTransfers = async () => {
  if (!detail.value || generating.value) return
  const count = detail.value.mismatchCount ?? 0
  try {
    await ElMessageBox.confirm(
      `将对 ${count} 条位置不符明细按实际位置分组生成调拨单（来源=盘点触发），用于资产归位。确认生成吗？`,
      '生成调拨单',
      { type: 'warning', confirmButtonText: '生成', cancelButtonText: '取消' },
    )
  } catch {
    return /* 用户取消 */
  }
  generating.value = true
  try {
    const transfers = await stocktakeApi.createTransfers(detail.value.id)
    generatedTransfers.value = transfers
    ElMessage.success(`已生成 ${transfers.length} 张调拨单，请到「资产调拨」中处理`)
    activeTab.value = 'report'
    void loadDetail()
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示（含"已生成过"409 防重）
  } finally {
    generating.value = false
  }
}

/* ---------------- 明细人工确认（相符 / 位置不符 / 盘亏） ---------------- */
const confirmVisible = ref(false)
const confirmSubmitting = ref(false)
const confirmFormRef = ref<FormInstance>()
const confirmTarget = ref<StocktakeItem>()
const confirmForm = reactive({
  notFound: false,
  actualLocationId: undefined as number | undefined,
  remark: '',
})

const locationTree = computed(() => buildTree<Location>(basedataStore.locations))

const openConfirm = (item: StocktakeItem) => {
  confirmTarget.value = item
  confirmForm.notFound = false
  /* 默认实际位置 = 系统记录位置（直接确认 = 账实相符） */
  confirmForm.actualLocationId = item.expectedLocationId
  confirmForm.remark = ''
  confirmVisible.value = true
  if (!basedataStore.locations.length) void basedataStore.fetchLocations()
}

const submitConfirm = async () => {
  if (!detail.value || !confirmTarget.value || confirmSubmitting.value) return
  if (!confirmForm.notFound && !confirmForm.actualLocationId) {
    ElMessage.warning('请选择实际位置，或勾选"未找到实物"')
    return
  }
  confirmSubmitting.value = true
  try {
    const updatedItem = await stocktakeApi.scanItem(detail.value.id, confirmTarget.value.id, {
      notFound: confirmForm.notFound || undefined,
      actualLocationId: confirmForm.notFound ? undefined : confirmForm.actualLocationId,
      remark: confirmForm.remark.trim() || undefined,
    })
    const label =
      updatedItem.statusLabel || STOCKTAKE_ITEM_STATUS_META[updatedItem.status].label
    ElMessage.success(`已确认：${label}`)
    confirmVisible.value = false
    void loadDetail() /* 刷新明细与统计 */
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示（含已盘 409 防重盘）
  } finally {
    confirmSubmitting.value = false
  }
}

/* ---------------- 按条码扫码（PDA 入口，Web 手输条码同用） ---------------- */
const barcodeSubmitting = ref(false)
const barcodeInput = reactive({
  barcode: '',
  locationId: undefined as number | undefined,
})

/** 扫码结果提示：五态区分（盘盈 = 范围外已登记资产自动补行） */
const notifyScanResult = (item: StocktakeItem) => {
  const label = item.statusLabel || STOCKTAKE_ITEM_STATUS_META[item.status].label
  if (item.status === 'LOCATION_MISMATCH') {
    ElMessage.warning(`位置不符：${item.assetBarcode || ''} 实际在 ${item.actualLocationName || '—'}`)
  } else if (item.status === 'EXTRA') {
    ElMessage.success(`盘盈：${item.assetBarcode || ''}（不在本任务范围，已补记明细）`)
  } else {
    ElMessage.success(`${label}：${item.assetBarcode || ''}`)
  }
}

const submitBarcode = async () => {
  if (!detail.value || barcodeSubmitting.value) return
  const barcode = barcodeInput.barcode.trim()
  if (!barcode) {
    ElMessage.warning('请输入/扫描资产条码')
    return
  }
  barcodeSubmitting.value = true
  try {
    const item = await stocktakeApi.scanByBarcode(detail.value.id, {
      barcode,
      actualLocationId: barcodeInput.locationId,
    })
    notifyScanResult(item)
    barcodeInput.barcode = ''
    void loadDetail() /* 刷新明细与统计 */
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示（未登记 404 / 报废 409 / 已盘 409）
  } finally {
    barcodeSubmitting.value = false
  }
}

/* ---------------- 展示工具 ---------------- */
/** 空值统一显示占位符 */
const dash = (v?: string | number | null) =>
  v === undefined || v === null || v === '' ? '—' : String(v)

const creatorText = computed(() =>
  detail.value
    ? detail.value.creatorName || String(detail.value.creatorUserId)
    : '—',
)

const scopeText = computed(() => {
  if (!detail.value) return '—'
  const loc = detail.value.locationName || '全库'
  const cat = detail.value.categoryName || '全部分类'
  return `${loc} × ${cat}`
})

/** 明细行状态 tag：优先后端 statusLabel，回退本地 meta */
const itemStatusLabel = (row: StocktakeItem) =>
  row.statusLabel || STOCKTAKE_ITEM_STATUS_META[row.status].label

/** 实际位置与系统位置对比（位置不符时高亮） */
const isMismatch = (row: StocktakeItem) => row.status === 'LOCATION_MISMATCH'

/* 五态统计卡片（详情接口回填；进入报告 tab 后以报告接口为准） */
const stats = computed(() => {
  const src = activeTab.value === 'report' && report.value ? report.value : detail.value
  if (!src) return []
  return [
    { key: 'totalCount', label: '明细总数', value: src.totalCount ?? 0 },
    { key: 'pendingCount', label: '待盘', value: src.pendingCount ?? 0 },
    { key: 'matchedCount', label: '账实相符', value: src.matchedCount ?? 0 },
    { key: 'mismatchCount', label: '位置不符', value: src.mismatchCount ?? 0 },
    { key: 'notFoundCount', label: '盘亏', value: src.notFoundCount ?? 0 },
    { key: 'extraCount', label: '盘盈', value: src.extraCount ?? 0 },
  ]
})

/** 报告差异明细表列（位置不符/盘亏/盘盈三组共用） */
const diffColumns = computed(() => [
  { prop: 'assetBarcode', label: '资产编码', minWidth: 130 },
  { prop: 'assetName', label: '资产名称', minWidth: 140 },
])
</script>

<template>
  <el-drawer
    :model-value="visible"
    :title="detail ? `盘点任务 ${detail.name}` : '盘点任务详情'"
    size="760px"
    @update:model-value="handleClose"
  >
    <div v-loading="loading">
      <template v-if="detail">
        <div class="detail-status">
          <el-tag v-if="statusMeta" :type="statusMeta.tagType" size="large" effect="light">
            {{ detail.statusLabel || statusMeta.label }}
          </el-tag>
          <span class="detail-type">盘点（范围快照 · 五态核对 · 触发调拨）</span>
        </div>

        <el-descriptions :column="2" border size="default" class="detail-desc">
          <el-descriptions-item label="任务名称" :span="2">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="盘点范围" :span="2">{{ scopeText }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ creatorText }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ dash(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ dash(detail.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ dash(detail.completeTime) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ dash(detail.remark) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 五态实时统计 -->
        <div class="stats-row">
          <div v-for="stat in stats" :key="stat.key" class="stat-card">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
        </div>

        <!-- 进行中：按条码扫码（PDA 入口） -->
        <div v-if="inProgress" class="barcode-bar">
          <el-input
            v-model="barcodeInput.barcode"
            placeholder="扫码/输入资产条码后回车（范围外已登记资产记盘盈）"
            clearable
            @keyup.enter="submitBarcode"
          />
          <el-tree-select
            v-model="barcodeInput.locationId"
            :data="locationTree"
            node-key="id"
            :props="{ label: 'name' }"
            check-strictly
            clearable
            filterable
            placeholder="实际位置（不选=系统位置）"
            class="barcode-location"
          />
          <el-button type="primary" :loading="barcodeSubmitting" @click="submitBarcode">扫码</el-button>
        </div>

        <el-tabs v-model="activeTab" class="detail-tabs">
          <!-- 盘点明细 -->
          <el-tab-pane label="盘点明细" name="items">
            <div class="item-tabs">
              <div
                v-for="tab in itemTabs"
                :key="tab.key"
                class="item-tab"
                :class="{ active: itemTab === tab.key }"
                @click="itemTab = tab.key"
              >
                <span>{{ tab.label }}</span>
                <span class="item-tab-count">({{ tab.count }})</span>
              </div>
            </div>

            <el-table :data="pageItems" row-key="id" border size="small" empty-text="暂无明细">
              <el-table-column prop="assetBarcode" label="资产编码" min-width="130" show-overflow-tooltip>
                <template #default="{ row }">{{ dash(row.assetBarcode || String(row.assetId)) }}</template>
              </el-table-column>
              <el-table-column prop="assetName" label="资产名称" min-width="130" show-overflow-tooltip>
                <template #default="{ row }">{{ dash(row.assetName) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="92">
                <template #default="{ row }">
                  <el-tag :type="STOCKTAKE_ITEM_STATUS_META[row.status as StocktakeItemStatus].tagType" effect="light">
                    {{ itemStatusLabel(row) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="expectedLocationName" label="系统位置" min-width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ dash(row.expectedLocationName || (row.expectedLocationId ? String(row.expectedLocationId) : '')) }}</template>
              </el-table-column>
              <el-table-column prop="actualLocationName" label="实际位置" min-width="110" show-overflow-tooltip>
                <template #default="{ row }">
                  <span :class="{ 'mismatch-actual': isMismatch(row) }">
                    {{ dash(row.actualLocationName || (row.actualLocationId ? String(row.actualLocationId) : '')) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="scannedAt" label="扫码时间" min-width="150">
                <template #default="{ row }">{{ dash(row.scannedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="76" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-if="inProgress && row.status === 'PENDING'"
                    link
                    type="primary"
                    @click="openConfirm(row)"
                  >
                    确认
                  </el-button>
                  <span v-else class="op-done">—</span>
                </template>
              </el-table-column>
            </el-table>

            <div class="item-pagination">
              <span class="pagination-info">共 {{ itemTotal }} 条</span>
              <el-pagination
                v-model:current-page="itemPage"
                :page-size="PAGE_SIZE"
                :total="itemTotal"
                layout="prev, pager, next"
                background
                size="small"
              />
            </div>
          </el-tab-pane>

          <!-- 盘点报告 -->
          <el-tab-pane label="盘点报告" name="report">
            <template v-if="report">
              <div class="report-status">
                <el-tag :type="statusMeta?.tagType" effect="light">
                  {{ report.statusLabel || statusMeta?.label }}
                </el-tag>
                <span class="report-complete">
                  完成时间：{{ dash(report.completeTime) }}（进行中显示实时统计）
                </span>
              </div>

              <div class="items-title">位置不符（{{ report.mismatches?.length ?? 0 }}）</div>
              <el-table :data="report.mismatches || []" row-key="id" border size="small" empty-text="无位置不符明细">
                <el-table-column prop="assetBarcode" label="资产编码" min-width="130" show-overflow-tooltip />
                <el-table-column prop="assetName" label="资产名称" min-width="140" show-overflow-tooltip />
                <el-table-column prop="expectedLocationName" label="系统位置" min-width="110" show-overflow-tooltip />
                <el-table-column prop="actualLocationName" label="实际位置" min-width="110" show-overflow-tooltip />
              </el-table>

              <div class="items-title">盘亏（{{ report.notFounds?.length ?? 0 }}）</div>
              <el-table :data="report.notFounds || []" row-key="id" border size="small" empty-text="无盘亏明细">
                <el-table-column
                  v-for="col in diffColumns"
                  :key="col.prop"
                  :prop="col.prop"
                  :label="col.label"
                  :min-width="col.minWidth"
                  show-overflow-tooltip
                />
                <el-table-column prop="expectedLocationName" label="系统位置" min-width="110" show-overflow-tooltip />
                <el-table-column prop="scannedAt" label="确认时间" min-width="150" />
              </el-table>

              <div class="items-title">盘盈（{{ report.extras?.length ?? 0 }}）</div>
              <el-table :data="report.extras || []" row-key="id" border size="small" empty-text="无盘盈明细">
                <el-table-column
                  v-for="col in diffColumns"
                  :key="col.prop"
                  :prop="col.prop"
                  :label="col.label"
                  :min-width="col.minWidth"
                  show-overflow-tooltip
                />
                <el-table-column prop="actualLocationName" label="实际位置" min-width="110" show-overflow-tooltip />
                <el-table-column prop="scannedAt" label="扫码时间" min-width="150" />
              </el-table>
            </template>
            <div v-else v-loading="true" class="report-loading"></div>

            <!-- 本次生成的调拨单 -->
            <template v-if="generatedTransfers.length">
              <div class="items-title">本次生成的调拨单（{{ generatedTransfers.length }}）</div>
              <el-table :data="generatedTransfers" row-key="id" border size="small">
                <el-table-column prop="serialNo" label="调拨单号" min-width="150" />
                <el-table-column label="来源" width="90">
                  <template #default="{ row }">
                    {{ row.sourceLabel || TRANSFER_SOURCE_META[row.source as keyof typeof TRANSFER_SOURCE_META]?.label || row.source }}
                  </template>
                </el-table-column>
                <el-table-column prop="toLocationName" label="调入位置" min-width="110" show-overflow-tooltip />
                <el-table-column prop="statusLabel" label="状态" width="90" />
              </el-table>
            </template>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <template #footer>
      <template v-if="detail">
        <!-- 待开始：开始盘点 -->
        <el-button v-if="canStart" type="primary" :loading="acting" @click="handleStart">开始盘点</el-button>
        <!-- 进行中：完成盘点（剩余待盘批量记盘亏） -->
        <el-button v-if="canComplete" type="success" :loading="acting" @click="handleComplete">完成盘点</el-button>
        <!-- 已完成且有位置不符：批量生成调拨单归位 -->
        <el-button
          v-if="canCreateTransfer"
          type="warning"
          :loading="generating"
          @click="handleCreateTransfers"
        >
          生成调拨单（{{ detail.mismatchCount }}）
        </el-button>
        <!-- 待开始/进行中 且创建人：取消 -->
        <el-button v-if="canCancel" :loading="acting" @click="handleCancel">取消任务</el-button>
        <el-button @click="handleClose">关闭</el-button>
      </template>
    </template>
  </el-drawer>

  <!-- 明细人工确认（相符/位置不符/盘亏） -->
  <el-dialog
    v-model="confirmVisible"
    title="确认盘点明细"
    width="480px"
    append-to-body
    :close-on-click-modal="false"
  >
    <template v-if="confirmTarget">
      <div class="confirm-asset">
        <span class="confirm-barcode">{{ confirmTarget.assetBarcode || confirmTarget.assetId }}</span>
        <span class="confirm-name">{{ confirmTarget.assetName || '—' }}</span>
        <span class="confirm-expected">系统位置：{{ confirmTarget.expectedLocationName || confirmTarget.expectedLocationId || '—' }}</span>
      </div>
      <el-form ref="confirmFormRef" :model="confirmForm" label-width="96px" @submit.prevent="submitConfirm">
        <el-form-item label="未找到实物">
          <el-switch v-model="confirmForm.notFound" active-text="盘亏" />
        </el-form-item>
        <el-form-item v-if="!confirmForm.notFound" label="实际位置" required>
          <el-tree-select
            v-model="confirmForm.actualLocationId"
            :data="locationTree"
            node-key="id"
            :props="{ label: 'name' }"
            check-strictly
            clearable
            filterable
            placeholder="实物所在位置（默认系统位置=账实相符）"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="confirmForm.remark" type="textarea" :rows="2" placeholder="选填" :maxlength="200" />
        </el-form-item>
        <div class="form-tip">
          实际位置与系统记录一致 = 账实相符；不一致 = 位置不符（完成后可批量生成调拨单归位）；
          未找到实物 = 盘亏。已盘明细不可重盘。
        </div>
      </el-form>
    </template>
    <template #footer>
      <el-button @click="confirmVisible = false">取消</el-button>
      <el-button type="primary" :loading="confirmSubmitting" @click="submitConfirm">确认</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.detail-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.detail-type {
  font-size: var(--text-sm);
  color: var(--color-text-3);
}

.detail-desc {
  margin-bottom: 16px;
}

/* 五态统计卡片 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
  margin-bottom: 16px;
}

.stat-card {
  background: var(--color-bg-3);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  padding: 8px 4px;
  text-align: center;
}

.stat-value {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text-1);
  line-height: 26px;
}

.stat-label {
  font-size: var(--text-xs);
  color: var(--color-text-3);
  margin-top: 2px;
}

/* 条码扫码条 */
.barcode-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.barcode-location {
  width: 200px;
  flex-shrink: 0;
}

.detail-tabs {
  margin-top: 4px;
}

/* 明细五态筛选（胶囊样式，对齐列表页 tabs） */
.item-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.item-tab {
  height: 30px;
  padding: 0 8px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--text-sm);
  background: var(--color-bg-3);
  color: var(--color-text-2);
  cursor: pointer;
  user-select: none;
}

.item-tab.active {
  background: var(--color-primary);
  color: var(--color-text-inverse);
}

.item-tab-count {
  font-size: var(--text-xs);
}

.item-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.pagination-info {
  font-size: var(--text-sm);
  color: var(--color-text-2);
}

.op-done {
  color: var(--color-text-4);
}

/* 位置不符：实际位置高亮 */
.mismatch-actual {
  color: var(--color-warning-text);
  font-weight: 600;
}

/* 报告 */
.report-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.report-complete {
  font-size: var(--text-sm);
  color: var(--color-text-3);
}

.report-loading {
  min-height: 120px;
}

.items-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text-1);
  margin: 16px 0 8px;
}

.items-title:first-of-type {
  margin-top: 0;
}

/* 确认弹窗资产信息 */
.confirm-asset {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  background: var(--color-bg-3);
  border-radius: var(--radius-md);
  padding: 8px 12px;
  margin-bottom: 16px;
}

.confirm-barcode {
  font-weight: 600;
  color: var(--color-text-1);
}

.confirm-name {
  color: var(--color-text-2);
}

.confirm-expected {
  font-size: var(--text-sm);
  color: var(--color-text-3);
}

.full-width {
  width: 100%;
}

.form-tip {
  font-size: var(--text-xs);
  color: var(--color-text-3);
  line-height: 18px;
  margin: -4px 0 0;
  padding-left: 96px;
}
</style>
