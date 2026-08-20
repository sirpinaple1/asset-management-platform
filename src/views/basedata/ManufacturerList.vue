<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
const loading = computed(() => store.loading.manufacturers)

const modalVisible = ref(false)
const currentRecord = ref<Manufacturer>()

/* 弹窗打开 = 有未保存内容：关页签前需确认 */
watch(modalVisible, (v) => tabsStore.setDirty(route.fullPath, v))

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
    // 乐观删除：本地立即移除，失败项自动回滚
    const { failedCount, okCount, snapshot } = await store.deleteManufacturersOptimistic(ids)
    if (okCount) {
      showUndoMessage(
        failedCount ? `已删除 ${okCount} 个厂商，${failedCount} 个失败` : `已删除 ${okCount} 个厂商`,
        () => store.undoDeleteManufacturers(snapshot),
      )
    }
  } finally {
    batchLoading.value = false
  }
}

/* ---------------- 前端分页 ---------------- */
const PAGE_SIZE = 10
const currentPage = ref(1)

const filtered = computed(() => {
  const kw = searchKeyword.value.toLowerCase()
  if (!kw) return manufacturers.value
  return manufacturers.value.filter((item) =>
    [item.name, item.contact, item.phone].some((field) => field?.toLowerCase().includes(kw)),
  )
})

const total = computed(() => filtered.value.length)
const pageData = computed(() =>
  filtered.value.slice((currentPage.value - 1) * PAGE_SIZE, currentPage.value * PAGE_SIZE),
)

/* 过滤结果变化时纠正越界页码 */
watch(total, () => {
  const maxPage = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
  if (currentPage.value > maxPage) currentPage.value = maxPage
})

/* ---------------- 深链与刷新保持：搜索词/页码同步 URL query ---------------- */
/* query → 状态：初始化 & 前进/后退/页签切换回本页时恢复 */
watch(
  () => route.query,
  (q) => {
    if (route.name !== 'basedata-manufacturers') return
    const kw = typeof q.q === 'string' ? q.q : ''
    if (kw !== keyword.value) keyword.value = kw
    const p = Number(q.page)
    if (Number.isInteger(p) && p >= 1 && p !== currentPage.value) currentPage.value = p
  },
  { immediate: true },
)

/* 状态 → query：replace 不产生历史记录，刷新/分享 URL 可还原现场 */
watch([searchKeyword, currentPage], () => {
  if (route.name !== 'basedata-manufacturers') return
  const query: Record<string, string> = {}
  if (searchKeyword.value) query.q = searchKeyword.value
  if (currentPage.value > 1) query.page = String(currentPage.value)
  const current = JSON.stringify(route.query)
  const next = JSON.stringify(query)
  if (current !== next) router.replace({ query })
})

/* ---------------- CRUD ---------------- */
const loadData = () => store.fetchManufacturers()

const handleAdd = () => {
  currentRecord.value = undefined
  modalVisible.value = true
}

const handleEdit = (record: Manufacturer) => {
  currentRecord.value = record
  modalVisible.value = true
}

const handleDelete = async (id: number) => {
  // 乐观删除：本地立即移除，失败自动回滚（拦截器提示错误）
  const { failedCount, okCount, snapshot } = await store.deleteManufacturersOptimistic([id])
  if (failedCount || !okCount) return
  showUndoMessage('已删除 1 个厂商', () => store.undoDeleteManufacturers(snapshot))
}

/** 新增/编辑成功：用响应数据原地合并本地列表（免全量刷新闪烁） */
const handleSaved = (item: Manufacturer) => store.upsertManufacturerLocal(item)

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
    pageRows: pageData,
    selectedRows,
    isModalOpen: modalVisible,
    tableRef,
    searchInputRef,
    onEditRow: handleEdit,
    onCopyRow: handleCopyRow,
    onDeleteRow: (row) => handleDelete(row.id),
    onBatchDelete: handleBatchDelete,
  })

onMounted(async () => {
  await loadData()
  /* 深链定位：?id=123 打开对应行编辑（用后清除，避免刷新重复弹窗） */
  const id = Number(route.query.id)
  if (Number.isInteger(id) && id > 0) {
    const row = manufacturers.value.find((m) => m.id === id)
    router.replace({ query: { ...route.query, id: undefined } })
    if (row) handleEdit(row)
    else ElMessage.warning(`未找到 id=${id} 的厂商`)
  }
})
</script>

<template>
  <div class="page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">厂商管理</h2>
      </div>

      <!-- 状态 tabs（骨架：全部；状态字段待后端支持后扩展"已停用"） -->
      <div class="tabs">
        <div class="tab active">
          <span>全部</span>
          <span class="tab-count">({{ total }})</span>
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
          placeholder="搜索名称 / 联系人 / 电话（Ctrl+F）"
          clearable
        >
          <template #prefix>
            <svg width="14" height="14" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="9" cy="9" r="6" stroke="#9CA3AF" stroke-width="1.5" />
              <line x1="13.5" y1="13.5" x2="17" y2="17" stroke="#9CA3AF" stroke-width="1.5" stroke-linecap="round" />
            </svg>
          </template>
        </el-input>
      </div>

      <!-- 表格：双击行编辑 / 右键菜单 / 方向键导航 / 列宽可拖拽 -->
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="pageData"
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
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
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
  background: #ffffff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.page-header {
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
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
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  background: #f3f4f6;
  color: #6b7280;
}

.tab.active {
  background: #165dff;
  color: #ffffff;
}

.tab-count {
  font-size: 13px;
}

/* 工具栏（对齐原型：浅底圆角条） */
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
  font-size: 13px;
  color: #6b7280;
}

/* 双击行可编辑：指针光标提示可交互 */
:deep(.el-table__row) {
  cursor: pointer;
}

/* 表格键盘导航时焦点可见 */
:deep(.el-table):focus-visible {
  outline: 2px solid #165dff;
  outline-offset: -2px;
}
</style>
