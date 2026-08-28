<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { InputInstance, TableInstance } from 'element-plus'
import { assetApi, exportAssets } from '@/api/modules/asset'
import type { Asset, AssetStatus } from '@/api/interface/asset'
import { ASSET_STATUS_META, DISCARDABLE_STATUSES } from '@/api/interface/asset'
import { useAssetStore } from '@/stores/asset'
import { useBasedataStore } from '@/stores/basedata'
import { useUserStore } from '@/stores/user'
import { useTabsStore } from '@/stores/tabs'
import { useListInteractions, type ContextMenuItem } from '@/composables/useListInteractions'
import { useColumnConfig, type ColumnDef } from '@/composables/useColumnConfig'
import { buildTree } from '@/utils/tree'
import type { Category, Location } from '@/api/interface/basedata'
import AssetModal from './components/AssetModal.vue'
import AssetDetailDrawer from './components/AssetDetailDrawer.vue'
import ColumnConfigPopover from './components/ColumnConfigPopover.vue'
import ContextMenu from '@/components/ContextMenu.vue'

/** 与路由 name 一致：多页签 keep-alive 缓存键 */
defineOptions({ name: 'assets-list' })

const route = useRoute()
const router = useRouter()
const tabsStore = useTabsStore()
const store = useAssetStore()
const basedataStore = useBasedataStore()
const userStore = useUserStore()

const assets = computed(() => store.assets)
const total = computed(() => store.total)
const loading = computed(() => store.loading)

/* ---------------- 状态 tabs（服务端 status 过滤） ---------------- */
type TabKey = 'ALL' | AssetStatus
const STATUS_TABS: TabKey[] = ['ALL', 'IDLE', 'IN_USE', 'PENDING_CONFIRM', 'DISCARD']
const activeTab = ref<TabKey>('ALL')
const tabs = computed(() =>
  STATUS_TABS.map((key) => ({
    key,
    label: key === 'ALL' ? '全部' : ASSET_STATUS_META[key].label,
  })),
)

/* ---------------- 关键词搜索（防抖 300ms，服务端模糊 编码/名称/序列号） ---------------- */
const keyword = ref('')
const searchKeyword = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

watch(keyword, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKeyword.value = val.trim()
  }, 300)
})
onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
  narrowMql?.removeEventListener('change', onNarrowChange)
})

/* ---------------- 高级筛选（分类/位置/公司，服务端过滤） ---------------- */
const filterCategoryId = ref<number>()
const filterLocationId = ref<number>()
const filterCompanyId = ref<number>()

/** "我的持有"开关：仅查当前登录用户名下的资产（userId = me） */
const onlyMine = ref(false)
const filterUserId = computed(() => (onlyMine.value ? userStore.me?.userId : undefined))

const categoryTree = computed(() => buildTree<Category>(basedataStore.categories))
const locationTree = computed(() => buildTree<Location>(basedataStore.locations))

/* ---------------- 服务端分页 ---------------- */
const pageSize = ref(20)
const currentPage = ref(1)

/* ---------------- 服务端排序（表头点击，orderBy/orderDir 透传后端） ---------------- */
const sortState = ref<{ orderBy?: string; orderDir?: 'asc' | 'desc' }>({})

const onSortChange = ({ prop, order }: { prop?: string; order?: 'ascending' | 'descending' | null }) => {
  if (!prop || !order) sortState.value = {}
  else sortState.value = { orderBy: prop, orderDir: order === 'ascending' ? 'asc' : 'desc' }
}

/** 组装当前查询条件（可选维度统一归一为 undefined，避免发送空参数） */
const buildQuery = () => ({
  page: currentPage.value,
  size: pageSize.value,
  status: activeTab.value === 'ALL' ? undefined : activeTab.value,
  keyword: searchKeyword.value || undefined,
  categoryId: filterCategoryId.value || undefined,
  locationId: filterLocationId.value || undefined,
  companyId: filterCompanyId.value || undefined,
  userId: filterUserId.value,
  orderBy: sortState.value.orderBy,
  orderDir: sortState.value.orderDir,
})

const fetchCurrent = () => store.fetchAssets(buildQuery())

/** 筛选维度变化：回第 1 页（页码本就是 1 时直接拉取） */
watch([activeTab, searchKeyword, filterCategoryId, filterLocationId, filterCompanyId, onlyMine, sortState], () => {
  if (currentPage.value === 1) fetchCurrent()
  else currentPage.value = 1 /* 页码变化触发下方 watcher 拉取 */
})

/** 页码/每页条数变化：直接拉取 */
watch([currentPage, pageSize], () => fetchCurrent())

/* keep-alive：首次挂载拉取；切回本页刷新（状态可能已被单据流转变更） */
let firstActivation = true
onMounted(async () => {
  /* 窄屏检测：表格 ↔ 卡片列表切换 */
  narrowMql = window.matchMedia('(max-width: 768px)')
  isNarrow.value = narrowMql.matches
  narrowMql.addEventListener('change', onNarrowChange)

  /* 确保当前用户信息已加载（onlyMine 过滤依赖 me.userId） */
  void userStore.loadMe()

  /* 深链 ?me=hold → 自动开启"我的持有"过滤 */
  if (route.query.me === 'hold') {
    onlyMine.value = true
  }

  fetchCurrent()
  void basedataStore.fetchCategories()
  void basedataStore.fetchLocations()
  void basedataStore.fetchCompanies()

  /* 深链定位：?id=123 打开对应资产详情（用后清除，避免刷新重复弹窗） */
  const id = Number(route.query.id)
  if (Number.isInteger(id) && id > 0) {
    router.replace({ query: { ...route.query, id: undefined } })
    try {
      openDetail(await assetApi.getAssetById(id))
    } catch {
      /* 404 已由拦截器提示 */
    }
  }
})
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  fetchCurrent()
})

/* ---------------- 深链与刷新保持：筛选/搜索词/页码同步 URL query ---------------- */
watch(
  () => route.query,
  (q) => {
    if (route.name !== 'assets-list') return
    const tab = typeof q.tab === 'string' ? q.tab : ''
    const nextTab = STATUS_TABS.includes(tab as TabKey) ? (tab as TabKey) : 'ALL'
    if (nextTab !== activeTab.value) activeTab.value = nextTab
    const kw = typeof q.q === 'string' ? q.q : ''
    if (kw !== keyword.value) keyword.value = kw
    const toId = (v: unknown) => {
      const n = Number(v)
      return Number.isInteger(n) && n > 0 ? n : undefined
    }
    const cat = toId(q.cat)
    if (cat !== filterCategoryId.value) filterCategoryId.value = cat
    const loc = toId(q.loc)
    if (loc !== filterLocationId.value) filterLocationId.value = loc
    const co = toId(q.co)
    if (co !== filterCompanyId.value) filterCompanyId.value = co
    const mineFlag = q.me === 'hold'
    if (mineFlag !== onlyMine.value) onlyMine.value = mineFlag
    const p = Number(q.page)
    if (Number.isInteger(p) && p >= 1 && p !== currentPage.value) currentPage.value = p
  },
  { immediate: true },
)

watch(
  [activeTab, searchKeyword, currentPage, filterCategoryId, filterLocationId, filterCompanyId, onlyMine],
  () => {
    if (route.name !== 'assets-list') return
    const query: Record<string, string> = {}
    if (activeTab.value !== 'ALL') query.tab = activeTab.value
    if (searchKeyword.value) query.q = searchKeyword.value
    if (filterCategoryId.value) query.cat = String(filterCategoryId.value)
    if (filterLocationId.value) query.loc = String(filterLocationId.value)
    if (filterCompanyId.value) query.co = String(filterCompanyId.value)
    if (onlyMine.value) query.me = 'hold'
    if (currentPage.value > 1) query.page = String(currentPage.value)
    const current = JSON.stringify(route.query)
    const next = JSON.stringify(query)
    if (current !== next) router.replace({ query })
  },
)

/* ---------------- 新增/编辑 ---------------- */
const modalVisible = ref(false)
const editingAsset = ref<Asset>()

/* 弹窗/抽屉打开 = 有未提交内容：关页签前需确认 */
const drawerVisible = ref(false)
const currentAsset = ref<Asset>()
watch(modalVisible, (v) => tabsStore.setDirty(route.fullPath, v))

const handleAdd = () => {
  editingAsset.value = undefined
  modalVisible.value = true
}

const handleEdit = (row: Asset) => {
  editingAsset.value = row
  modalVisible.value = true
}

/** 新增成功：重拉当前页（新资产可能落在当前筛选内）；编辑成功：响应数据原地合并 */
const handleSaved = (item: Asset) => {
  if (editingAsset.value) store.upsertLocal(item)
  else fetchCurrent()
}

/* ---------------- 详情抽屉 ---------------- */
const openDetail = (row: Asset) => {
  currentAsset.value = row
  drawerVisible.value = true
}

/* 详情抽屉 → 编辑：关抽屉开弹窗 */
const handleEditFromDrawer = (asset: Asset) => {
  drawerVisible.value = false
  handleEdit(asset)
}

/* 详情抽屉 → 报废成功：列表刷新（行可能离开当前状态筛选） */
const handleDiscarded = () => fetchCurrent()

/* ---------------- 报废（状态机：仅 闲置/在用 → 报废，终态不可逆） ---------------- */
const canDiscard = (row: Asset) => DISCARDABLE_STATUSES.includes(row.status)

const handleDiscard = async (row: Asset) => {
  if (!canDiscard(row)) return
  try {
    const { value } = await ElMessageBox.prompt(
      `资产 ${row.barcode}（${row.name}）报废后不可恢复，确认报废吗？`,
      '报废确认',
      {
        type: 'warning',
        confirmButtonText: '报废',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '报废原因（选填，不超过 500 字）',
        inputValidator: (v: string) => !v || v.length <= 500 || '报废原因不能超过 500 字',
      },
    )
    await assetApi.discardAsset(row.id, value.trim() || undefined)
    ElMessage.success('已报废')
    fetchCurrent()
  } catch {
    /* 用户取消或错误已由拦截器提示 */
  }
}

/* ---------------- 导出（按当前筛选条件导出全部） ---------------- */
const exporting = ref(false)

const handleExport = async () => {
  if (exporting.value) return
  exporting.value = true
  try {
    const { page: _page, size: _size, ...filters } = buildQuery()
    await exportAssets(filters)
    ElMessage.success('导出成功')
  } catch {
    /* 失败已由拦截器或 exportAssets 内部提示 */
  } finally {
    exporting.value = false
  }
}

/* ---------------- 展示工具 ---------------- */
const formatText = (_row: Asset, _column: unknown, cellValue: unknown) =>
  cellValue === undefined || cellValue === null || cellValue === '' ? '—' : cellValue

/** 使用人：后端经 UserDirectory 实时反查 sys_user 返回 userName；未命中兜底 #id */
const userText = (row: Asset) =>
  row.userId ? row.userName ?? `#${row.userId}` : '—'

/* ---------------- 列配置：显隐 / 拖拽排序 / localStorage 记住偏好 ---------------- */
const COLUMN_DEFAULTS: ColumnDef[] = [
  { key: 'barcode', label: '资产编码', visible: true },
  { key: 'name', label: '资产名称', visible: true },
  { key: 'sn', label: '序列号', visible: true },
  { key: 'spec', label: '细则', visible: true },
  { key: 'status', label: '状态', visible: true },
  { key: 'categoryName', label: '分类', visible: true },
  { key: 'modelName', label: '型号', visible: true },
  { key: 'locationName', label: '当前位置', visible: true },
  { key: 'user', label: '使用人', visible: true },
  { key: 'companyName', label: '归属公司', visible: true },
  { key: 'purchaseDate', label: '购置日期', visible: true },
]
const { columns, visibleColumns, toggle: toggleColumn, reset: resetColumns, move: moveColumn } =
  useColumnConfig('asset.columns.config.v1', COLUMN_DEFAULTS)

/** 列 key → 单元格取值（user/status 为派生字段） */
const columnValue = (row: Asset, key: string): string => {
  if (key === 'user') return userText(row)
  if (key === 'status') return row.statusLabel
  const v = (row as unknown as Record<string, unknown>)[key]
  return v === undefined || v === null || v === '' ? '' : String(v)
}

/* ---------------- 行多选 + 批量操作 ---------------- */
const selectedRows = ref<Asset[]>([])
const batchDiscarding = ref(false)

const onSelectionChange = (rows: Asset[]) => {
  selectedRows.value = rows
}
const toggleRowSelection = (row: Asset) => {
  tableRef.value?.toggleRowSelection(row)
}
const clearSelection = () => {
  tableRef.value?.clearSelection()
}

/** CSV 单元格转义（逗号/引号/换行） */
const csvCell = (v: string) => (/[",\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v)

/** 导出选中行（可见列 → CSV，带 BOM 防 Excel 乱码） */
const exportSelected = () => {
  if (!selectedRows.value.length) return
  const cols = visibleColumns.value
  const header = cols.map((c) => csvCell(c.label)).join(',')
  const lines = selectedRows.value.map((row) => cols.map((c) => csvCell(columnValue(row, c.key))).join(','))
  const csv = `\uFEFF${header}\n${lines.join('\n')}`
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `资产选中导出_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

/** 批量报废：逐项调报废端点（有确认框；不可报废项自动跳过并汇总） */
const handleBatchDiscard = async () => {
  const targets = selectedRows.value.filter(canDiscard)
  if (!targets.length) {
    ElMessage.warning('选中资产均不可报废（待确认/已报废）')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认报废选中的 ${targets.length} 项资产？报废后不可恢复。`,
      '批量报废确认',
      { type: 'warning', confirmButtonText: '报废', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  batchDiscarding.value = true
  let ok = 0
  let fail = 0
  for (const row of targets) {
    try {
      await assetApi.discardAsset(row.id)
      ok++
    } catch {
      fail++
    }
  }
  batchDiscarding.value = false
  ElMessage.success(`批量报废完成：成功 ${ok}${fail ? `，失败 ${fail}` : ''}`)
  clearSelection()
  fetchCurrent()
}

/* ---------------- 响应式：窄屏表格转卡片列表 ---------------- */
const isNarrow = ref(false)
let narrowMql: MediaQueryList | null = null
const onNarrowChange = (e: MediaQueryListEvent) => {
  isNarrow.value = e.matches
}

/* ---------------- 输入与触发：右键菜单 / 键盘导航 / 快捷键 ---------------- */
const tableRef = ref<TableInstance>()
const searchInputRef = ref<InputInstance>()

/** 复制行信息为 TSV（可直接粘贴进 Excel） */
const handleCopyRow = async (row: Asset) => {
  const text = [
    row.barcode,
    row.name,
    row.sn,
    row.statusLabel,
    row.categoryName,
    row.modelName,
    row.locationName,
    row.companyName,
    row.purchaseDate,
  ]
    .map((v) => v ?? '')
    .join('\t')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

/** 资产右键菜单：详情/编辑/复制/报废 */
const CTX_MENU_ITEMS: ContextMenuItem[] = [
  { key: 'detail', label: '详情' },
  { key: 'edit', label: '编辑' },
  { key: 'copy', label: '复制信息' },
  { key: 'discard', label: '报废', danger: true },
]

const anyOverlayOpen = computed(() => modalVisible.value || drawerVisible.value)

const { ctxMenu, ctxMenuItems, onRowContextmenu, onCtxMenuSelect, onTableKeydown, onCurrentChange } =
  useListInteractions<Asset>({
    pageRows: assets,
    selectedRows,
    isModalOpen: anyOverlayOpen,
    tableRef,
    searchInputRef,
    onEditRow: handleEdit,
    onOpenRow: openDetail,
    onSpaceRow: toggleRowSelection,
    onCopyRow: handleCopyRow,
    onDeleteRow: () => {},
    onBatchDelete: handleBatchDiscard,
    ctxMenuItems: CTX_MENU_ITEMS,
    onCtxAction: (key, row) => {
      if (key === 'detail') openDetail(row)
      else if (key === 'discard') handleDiscard(row)
    },
  })
</script>

<template>
  <div class="page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">资产列表</h2>
        <span class="page-subtitle">资产全生命周期台账（新增后默认闲置，报废不可恢复）</span>
      </div>

      <!-- 状态 tabs（服务端过滤） -->
      <div class="tabs">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          class="tab"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          <span>{{ tab.label }}</span>
        </div>
      </div>

      <!-- 工具栏：新增/导出 + 高级筛选 + 搜索 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" @click="handleAdd">
            <span class="btn-plus">+</span>
            <span>新增资产</span>
          </el-button>
          <el-button :loading="exporting" @click="handleExport">导出</el-button>
          <el-button
            :type="onlyMine ? 'primary' : 'default'"
            :plain="onlyMine"
            @click="onlyMine = !onlyMine"
          >
            {{ onlyMine ? '✓ 我的持有' : '我的持有' }}
          </el-button>
          <!-- 列配置：显隐 + 拖拽排序（偏好存 localStorage） -->
          <el-popover placement="bottom-start" :width="240" trigger="click">
            <template #reference>
              <el-button>列配置</el-button>
            </template>
            <ColumnConfigPopover
              :columns="columns"
              :visible-count="visibleColumns.length"
              @toggle="toggleColumn"
              @move="moveColumn"
              @reset="resetColumns"
            />
          </el-popover>
        </div>
        <div class="toolbar-filters">
          <el-tree-select
            v-model="filterCategoryId"
            :data="categoryTree"
            node-key="id"
            :props="{ label: 'name' }"
            check-strictly
            clearable
            filterable
            placeholder="分类"
            class="filter-select"
          />
          <el-tree-select
            v-model="filterLocationId"
            :data="locationTree"
            node-key="id"
            :props="{ label: 'name' }"
            check-strictly
            clearable
            filterable
            placeholder="位置"
            class="filter-select"
          />
          <el-select
            v-model="filterCompanyId"
            clearable
            filterable
            placeholder="归属公司"
            class="filter-select"
          >
            <el-option
              v-for="c in basedataStore.companies"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
          <el-input
            ref="searchInputRef"
            v-model="keyword"
            class="search-box"
            placeholder="搜索编码 / 名称 / 序列号（Ctrl+F）"
            clearable
          >
            <template #prefix>
              <svg width="14" height="14" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
                <circle cx="9" cy="9" r="6" style="stroke: var(--color-text-4)" stroke-width="1.5" />
                <line x1="13.5" y1="13.5" x2="17" y2="17" style="stroke: var(--color-text-4)" stroke-width="1.5" stroke-linecap="round" />
              </svg>
            </template>
          </el-input>
        </div>
      </div>

      <!-- 资产表格：多选批量 / 双击开详情 / 右键菜单 / 方向键+Space+Enter 键盘导航 / 表头排序 -->
      <el-table
        v-if="!isNarrow"
        ref="tableRef"
        v-loading="loading"
        :data="assets"
        row-key="id"
        border
        tabindex="0"
        highlight-current-row
        empty-text="暂无资产数据"
        @row-dblclick="openDetail"
        @row-contextmenu="onRowContextmenu"
        @current-change="onCurrentChange"
        @keydown="onTableKeydown"
        @selection-change="onSelectionChange"
        @sort-change="onSortChange"
      >
        <el-table-column type="selection" width="46" fixed="left" :reserve-selection="true" />
        <template v-for="col in visibleColumns" :key="col.key">
          <el-table-column
            v-if="col.key === 'barcode'"
            prop="barcode" label="资产编码" min-width="150" sortable="custom" show-overflow-tooltip
          />
          <el-table-column
            v-else-if="col.key === 'name'"
            prop="name" label="资产名称" min-width="170" sortable="custom" show-overflow-tooltip
          />
          <el-table-column
            v-else-if="col.key === 'sn'"
            prop="sn" label="序列号" min-width="130" show-overflow-tooltip :formatter="formatText"
          />
          <el-table-column
            v-else-if="col.key === 'spec'"
            prop="spec" label="细则" min-width="140" show-overflow-tooltip :formatter="formatText"
          />
          <el-table-column v-else-if="col.key === 'status'" label="状态" width="100" align="center" sortable="custom" prop="status">
            <template #default="{ row }">
              <el-tag :type="ASSET_STATUS_META[row.status as AssetStatus].tagType" effect="light">
                {{ ASSET_STATUS_META[row.status as AssetStatus].label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            v-else-if="col.key === 'categoryName'"
            prop="categoryName" label="分类" min-width="110" show-overflow-tooltip :formatter="formatText"
          />
          <el-table-column
            v-else-if="col.key === 'modelName'"
            prop="modelName" label="型号" min-width="120" show-overflow-tooltip :formatter="formatText"
          />
          <el-table-column
            v-else-if="col.key === 'locationName'"
            prop="locationName" label="当前位置" min-width="130" show-overflow-tooltip :formatter="formatText"
          />
          <el-table-column v-else-if="col.key === 'user'" label="使用人" width="100" align="center">
            <template #default="{ row }">{{ userText(row) }}</template>
          </el-table-column>
          <el-table-column
            v-else-if="col.key === 'companyName'"
            prop="companyName" label="归属公司" min-width="160" show-overflow-tooltip :formatter="formatText"
          />
          <el-table-column
            v-else-if="col.key === 'purchaseDate'"
            prop="purchaseDate" label="购置日期" width="110" sortable="custom" :formatter="formatText"
          />
        </template>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-tooltip
              v-if="!canDiscard(row)"
              :content="row.status === 'DISCARD' ? '已报废' : '待确认资产不可报废'"
              placement="top"
              :show-after="300"
            >
              <span class="disabled-action">报废</span>
            </el-tooltip>
            <el-button v-else link type="danger" @click="handleDiscard(row)">报废</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 窄屏（≤768px）：表格降级为卡片列表，避免横向滚动崩溃 -->
      <div v-else v-loading="loading" class="asset-cards">
        <div v-if="!assets.length" class="cards-empty">暂无资产数据</div>
        <div
          v-for="row in assets"
          :key="row.id"
          class="asset-card"
          @click="openDetail(row)"
          @contextmenu.prevent="onRowContextmenu(row, null, $event)"
        >
          <div class="card-head">
            <span class="card-name">{{ row.name }}</span>
            <el-tag :type="ASSET_STATUS_META[row.status as AssetStatus].tagType" effect="light">
              {{ ASSET_STATUS_META[row.status as AssetStatus].label }}
            </el-tag>
          </div>
          <div class="card-barcode">{{ row.barcode }}</div>
          <div class="card-meta">
            <span>分类：{{ row.categoryName || '—' }}</span>
            <span>位置：{{ row.locationName || '—' }}</span>
            <span>使用人：{{ userText(row) }}</span>
          </div>
        </div>
      </div>

      <!-- 浮动批量操作条：勾选后出现 -->
      <transition name="batch-bar-slide">
        <div v-if="selectedRows.length" class="batch-bar">
          <span class="batch-bar__count">已选 {{ selectedRows.length }} 项</span>
          <el-button size="small" @click="exportSelected">导出选中</el-button>
          <el-button size="small" type="danger" :loading="batchDiscarding" @click="handleBatchDiscard">
            批量报废{{ selectedRows.filter(canDiscard).length ? `（${selectedRows.filter(canDiscard).length}）` : '' }}
          </el-button>
          <el-button size="small" text @click="clearSelection">取消选择</el-button>
        </div>
      </transition>

      <!-- 分页（服务端分页） -->
      <div class="pagination">
        <span class="pagination-info">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100, 200]"
          layout="sizes, prev, pager, next"
          background
        />
      </div>
    </div>

    <!-- 行右键菜单 -->
    <ContextMenu
      :visible="ctxMenu.visible"
      :x="ctxMenu.x"
      :y="ctxMenu.y"
      :items="ctxMenuItems"
      @select="onCtxMenuSelect"
    />

    <!-- 新增/编辑 -->
    <AssetModal v-model:visible="modalVisible" :data="editingAsset" @success="handleSaved" />

    <!-- 详情（含操作日志/编辑/退库/报废入口） -->
    <AssetDetailDrawer
      v-model:visible="drawerVisible"
      :asset="currentAsset"
      @edit="handleEditFromDrawer"
      @discarded="handleDiscarded"
      @returned="handleDiscarded"
    />
  </div>
</template>

<style scoped>
.page-container {
  background: var(--color-bg-2);
  border-radius: var(--radius-xl);
  padding: 24px;
  box-shadow: var(--shadow-md);
}

.page-header {
  margin-bottom: 16px;
  display: flex;
  align-items: baseline;
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

/* 工具栏（对齐原型：浅底圆角条） */
.toolbar {
  min-height: 60px;
  padding: 16px;
  background: var(--color-bg-3);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  gap: 8px;
}

.btn-plus {
  font-size: 15px;
  line-height: 1;
  margin-right: 2px;
}

.toolbar-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-select {
  width: 150px;
  flex-shrink: 0;
}

.search-box {
  width: 260px;
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
  font-size: var(--text-sm);
  color: var(--color-text-2);
}

/* 不可报废时的禁用文案（对齐 link 按钮形态） */
.disabled-action {
  font-size: var(--text-sm);
  line-height: 32px;
  padding: 0 8px;
  color: var(--color-text-3);
  cursor: not-allowed;
}

/* 双击行可查看详情：指针光标提示可交互 */
:deep(.el-table__row) {
  cursor: pointer;
}

/* 表格键盘导航时焦点可见 */
:deep(.el-table):focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

/* ---------------- 浮动批量操作条 ---------------- */
.batch-bar {
  position: fixed;
  left: 50%;
  bottom: 32px;
  transform: translateX(-50%);
  z-index: 1000;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
}

.batch-bar__count {
  font-size: var(--text-sm);
  color: var(--color-text-1);
  font-weight: 600;
  white-space: nowrap;
}

.batch-bar-slide-enter-active,
.batch-bar-slide-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.batch-bar-slide-enter-from,
.batch-bar-slide-leave-to {
  transform: translateX(-50%) translateY(16px);
  opacity: 0;
}

/* ---------------- 窄屏卡片列表 ---------------- */
.asset-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 120px;
}

.cards-empty {
  padding: 32px 0;
  text-align: center;
  font-size: var(--text-sm);
  color: var(--color-text-4);
}

.asset-card {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 12px 16px;
  cursor: pointer;
}

.asset-card:active {
  background: var(--color-bg-3);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-name {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-barcode {
  font-size: var(--text-xs);
  color: var(--color-text-3);
  margin: 4px 0;
  font-family: 'SF Mono', Menlo, Consolas, monospace;
}

.card-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: var(--text-xs);
  color: var(--color-text-2);
}

/* 窄屏：搜索框占满一行，筛选下拉收窄 */
@media (max-width: 768px) {
  .search-box {
    width: 100%;
  }

  .filter-select {
    width: 128px;
  }
}
</style>
