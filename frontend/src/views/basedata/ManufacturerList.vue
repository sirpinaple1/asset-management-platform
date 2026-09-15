<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { InputInstance, TableInstance } from 'element-plus'
import { useBasedataStore } from '@/stores/basedata'
import { useTabsStore } from '@/stores/tabs'
import { useListInteractions } from '@/composables/useListInteractions'
import { showUndoMessage } from '@/composables/useUndoMessage'
import type { Manufacturer } from '@/api/interface/basedata'
import ManufacturerModal from './components/ManufacturerModal.vue'
import ContextMenu from '@/components/ContextMenu.vue'

/** 与路由 name 一致：多页签 keep-alive 缓存键 */
defineOptions({ name: 'basedata-manufacturers' })

const route = useRoute()
const router = useRouter()
const tabsStore = useTabsStore()
const store = useBasedataStore()
const manufacturers = computed(() => store.manufacturers)
const total = computed(() => store.manufacturerTotal)
const loading = computed(() => store.loading.manufacturers)

const modalVisible = ref(false)
const currentRecord = ref<Manufacturer>()

/* 弹窗打开 = 有未保存内容：关页签前需确认 */
watch(modalVisible, (v) => tabsStore.setDirty(route.fullPath, v))

/* ---------------- 状态 tabs（服务端 status 过滤：全部/启用/停用） ---------------- */
type TabKey = 'ALL' | 'ENABLED' | 'DISABLED'
const STATUS_TABS: TabKey[] = ['ALL', 'ENABLED', 'DISABLED']
const TAB_LABEL: Record<TabKey, string> = { ALL: '全部', ENABLED: '启用', DISABLED: '停用' }
const activeTab = ref<TabKey>('ALL')

/* ---------------- 搜索（防抖 300ms，服务端按名称模糊匹配） ---------------- */
const keyword = ref('')
const searchKeyword = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

watch(keyword, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKeyword.value = val.trim()
  }, 300)
})
onBeforeUnmount(() => searchTimer && clearTimeout(searchTimer))

/* ---------------- 多选 + 批量删除 ---------------- */
const selectedRows = ref<Manufacturer[]>([])
const onSelectionChange = (rows: Manufacturer[]) => {
  selectedRows.value = rows
}

const batchLoading = ref(false)
const handleBatchDelete = async () => {
  if (!selectedRows.value.length) return
  const count = selectedRows.value.length
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${count} 个厂商吗？`, '批量删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return /* 用户取消 */
  }
  batchLoading.value = true
  const ids = selectedRows.value.map((row) => row.id)
  selectedRows.value = []
  try {
    // 乐观删除：本地立即移除，失败项自动回滚；成功后重拉当前页补齐
    const { failedCount, okCount, snapshot } = await store.deleteManufacturersOptimistic(ids)
    if (okCount) {
      fetchCurrent()
      showUndoMessage(
        failedCount ? `已删除 ${okCount} 个厂商，${failedCount} 个失败` : `已删除 ${okCount} 个厂商`,
        async () => {
          await store.undoDeleteManufacturers(snapshot)
          fetchCurrent()
        },
      )
    }
  } finally {
    batchLoading.value = false
  }
}

/* ---------------- 服务端分页（M02.5 契约：keyword 仅匹配名称，id 倒序） ---------------- */
const PAGE_SIZE = 10
const currentPage = ref(1)

const buildQuery = () => ({
  page: currentPage.value,
  size: PAGE_SIZE,
  keyword: searchKeyword.value || undefined,
  status: activeTab.value === 'ALL' ? undefined : activeTab.value === 'ENABLED' ? 1 : 0,
})

const fetchCurrent = () => store.fetchManufacturers(buildQuery())

/* 筛选维度变化：回第 1 页（页码本就是 1 时直接拉取） */
watch([activeTab, searchKeyword], () => {
  if (currentPage.value === 1) fetchCurrent()
  else currentPage.value = 1 /* 页码变化触发下方 watcher 拉取 */
})

/* 页码变化：直接拉取 */
watch(currentPage, () => fetchCurrent())

/* ---------------- 深链与刷新保持：tabs/搜索词/页码同步 URL query ---------------- */
/* query → 状态：初始化 & 前进/后退/页签切换回本页时恢复 */
watch(
  () => route.query,
  (q) => {
    if (route.name !== 'basedata-manufacturers') return
    const tab = typeof q.tab === 'string' ? q.tab : ''
    const nextTab = STATUS_TABS.includes(tab as TabKey) ? (tab as TabKey) : 'ALL'
    if (nextTab !== activeTab.value) activeTab.value = nextTab
    const kw = typeof q.q === 'string' ? q.q : ''
    if (kw !== keyword.value) keyword.value = kw
    const p = Number(q.page)
    if (Number.isInteger(p) && p >= 1 && p !== currentPage.value) currentPage.value = p
  },
  { immediate: true },
)

/* 状态 → query：replace 不产生历史记录，刷新/分享 URL 可还原现场 */
watch([activeTab, searchKeyword, currentPage], () => {
  if (route.name !== 'basedata-manufacturers') return
  const query: Record<string, string> = {}
  if (activeTab.value !== 'ALL') query.tab = activeTab.value
  if (searchKeyword.value) query.q = searchKeyword.value
  if (currentPage.value > 1) query.page = String(currentPage.value)
  const current = JSON.stringify(route.query)
  const next = JSON.stringify(query)
  if (current !== next) router.replace({ query })
})

/* ---------------- CRUD ---------------- */
const handleAdd = () => {
  currentRecord.value = undefined
  modalVisible.value = true
}

const handleEdit = (record: Manufacturer) => {
  currentRecord.value = record
  modalVisible.value = true
}

const handleDelete = async (id: number) => {
  // 乐观删除：本地立即移除，失败自动回滚（拦截器提示错误）；成功后重拉当前页补齐
  const { failedCount, okCount, snapshot } = await store.deleteManufacturersOptimistic([id])
  if (failedCount || !okCount) return
  fetchCurrent()
  showUndoMessage('已删除 1 个厂商', async () => {
    await store.undoDeleteManufacturers(snapshot)
    fetchCurrent()
  })
}

/** 新增成功：重拉当前页（新记录按 id 倒序落在第 1 页）；编辑成功：响应数据原地合并 */
const handleSaved = (item: Manufacturer) => {
  if (currentRecord.value) store.upsertManufacturerLocal(item)
  else fetchCurrent()
}

/** 空值统一显示占位符 */
const formatText = (_row: Manufacturer, _column: unknown, cellValue: unknown) =>
  cellValue === undefined || cellValue === null || cellValue === '' ? '—' : cellValue

/* ---------------- 输入与触发：右键菜单 / 键盘导航 / 快捷键 ---------------- */
const tableRef = ref<TableInstance>()
const searchInputRef = ref<InputInstance>()

/** 复制行信息为 TSV（可直接粘贴进 Excel） */
const handleCopyRow = async (row: Manufacturer) => {
  const text = [row.name, row.contact, row.phone, row.email, row.address, row.remark]
    .map((v) => v ?? '')
    .join('\t')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

const { ctxMenu, ctxMenuItems, onRowContextmenu, onCtxMenuSelect, onTableKeydown, onCurrentChange } =
  useListInteractions<Manufacturer>({
    pageRows: manufacturers,
    selectedRows,
    isModalOpen: modalVisible,
    tableRef,
    searchInputRef,
    onEditRow: handleEdit,
    onCopyRow: handleCopyRow,
    onDeleteRow: (row) => handleDelete(row.id),
    onBatchDelete: handleBatchDelete,
  })

/* keep-alive：首次挂载拉取；切回本页刷新（数据可能已被其他页签变更） */
let firstActivation = true
onMounted(async () => {
  await fetchCurrent()
  /* 深链定位：?id=123 打开对应行编辑（用后清除，避免刷新重复弹窗；仅当前页可见行） */
  const id = Number(route.query.id)
  if (Number.isInteger(id) && id > 0) {
    const row = manufacturers.value.find((m) => m.id === id)
    router.replace({ query: { ...route.query, id: undefined } })
    if (row) handleEdit(row)
    else ElMessage.warning(`未找到 id=${id} 的厂商`)
  }
})
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  fetchCurrent()
})
</script>

<template>
  <div class="page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">厂商管理</h2>
      </div>

      <!-- 状态 tabs（服务端 status 过滤：全部/启用/停用） -->
      <div class="tabs">
        <div
          v-for="tab in STATUS_TABS"
          :key="tab"
          class="tab"
          :class="{ active: activeTab === tab }"
          @click="activeTab = tab"
        >
          <span>{{ TAB_LABEL[tab] }}</span>
        </div>
      </div>

      <!-- 工具栏：批量操作 + 搜索 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" @click="handleAdd">
            <span class="btn-plus">+</span>
            <span>新增厂商</span>
          </el-button>
          <el-button :disabled="!selectedRows.length" :loading="batchLoading" @click="handleBatchDelete">
            批量删除
          </el-button>
          <el-tooltip content="导出功能建设中" placement="top" :show-after="300">
            <el-button disabled>导出</el-button>
          </el-tooltip>
        </div>
        <el-input
          ref="searchInputRef"
          v-model="keyword"
          class="search-box"
          placeholder="搜索名称（Ctrl+F）"
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

      <!-- 表格：双击行编辑 / 右键菜单 / 方向键导航 / 列宽可拖拽 -->
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="manufacturers"
        row-key="id"
        border
        tabindex="0"
        highlight-current-row
        empty-text="暂无厂商数据"
        @selection-change="onSelectionChange"
        @row-dblclick="handleEdit"
        @row-contextmenu="onRowContextmenu"
        @current-change="onCurrentChange"
        @keydown="onTableKeydown"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="name" label="厂商名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" min-width="100" :formatter="formatText" />
        <el-table-column prop="phone" label="联系电话" min-width="130" :formatter="formatText" />
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip :formatter="formatText" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该厂商吗？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页（服务端分页） -->
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

    <!-- 行右键菜单 -->
    <ContextMenu
      :visible="ctxMenu.visible"
      :x="ctxMenu.x"
      :y="ctxMenu.y"
      :items="ctxMenuItems"
      @select="onCtxMenuSelect"
    />

    <ManufacturerModal v-model:visible="modalVisible" :data="currentRecord" @success="handleSaved" />
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
}

.page-title {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text-1);
  margin: 0;
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

.search-box {
  width: 240px;
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

/* 双击行可编辑：指针光标提示可交互 */
:deep(.el-table__row) {
  cursor: pointer;
}

/* 表格键盘导航时焦点可见 */
:deep(.el-table):focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}
</style>
