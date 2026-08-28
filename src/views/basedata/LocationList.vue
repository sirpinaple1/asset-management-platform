<script setup lang="ts">
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { useBasedataStore } from '@/stores/basedata'
import { useTabsStore } from '@/stores/tabs'
import { buildTree } from '@/utils/tree'
import type { Location } from '@/api/interface/basedata'
import LocationModal from './components/LocationModal.vue'

/** 与路由 name 一致：多页签 keep-alive 缓存键 */
defineOptions({ name: 'basedata-locations' })

const route = useRoute()
const tabsStore = useTabsStore()
const store = useBasedataStore()

const loading = computed(() => store.loading.locations)

/* ---------------- 本地搜索（接口无 keyword，按名称匹配：命中节点及其祖先保留） ---------------- */
const keyword = ref('')

/** 递归过滤：节点名命中保留整个节点；否则仅保留含命中后代的节点 */
const filterTree = (nodes: Location[], kw: string): Location[] => {
  const result: Location[] = []
  for (const node of nodes) {
    if (node.name.includes(kw)) {
      result.push(node)
    } else if (node.children?.length) {
      const children = filterTree(node.children, kw)
      if (children.length) result.push({ ...node, children })
    }
  }
  return result
}

/** 树形表格数据（后端返回扁平列表，前端组树；有关键词时过滤） */
const treeData = computed(() => {
  const tree = buildTree<Location>(store.locations)
  const kw = keyword.value.trim()
  return kw ? filterTree(tree, kw) : tree
})

/* ---------------- CRUD ---------------- */
const modalVisible = ref(false)
const currentRecord = ref<Location>()

/* 弹窗打开 = 有未保存内容：关页签前需确认 */
watch(modalVisible, (v) => tabsStore.setDirty(route.fullPath, v))

const handleAdd = () => {
  currentRecord.value = undefined
  modalVisible.value = true
}

const handleEdit = (record: Location) => {
  currentRecord.value = record
  modalVisible.value = true
}

/* 有子位置/有资产引用时后端 400 拦截，原因由拦截器统一提示 */
const handleDelete = async (row: Location) => {
  try {
    await store.deleteLocation(row.id)
    ElMessage.success('删除成功')
    await store.fetchLocations()
  } catch {
    /* 400 原因已由拦截器提示 */
  }
}

/* 新增/编辑成功：重拉位置（树形结构、path 层级关联由后端权威返回） */
const handleSaved = () => {
  void store.fetchLocations()
}

/** 空值统一显示占位符 */
const formatText = (_row: Location, _column: unknown, cellValue: unknown) =>
  cellValue === undefined || cellValue === null || cellValue === '' ? '—' : cellValue

/* keep-alive：首次挂载拉取；切回本页刷新（数据可能已被其他页签变更） */
let firstActivation = true
onMounted(() => {
  void store.fetchLocations()
})
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  void store.fetchLocations()
})

const tableRef = ref<TableInstance>()
</script>

<template>
  <div class="page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">位置管理</h2>
        <span class="page-subtitle">树形层级位置（公司 / 楼层 / 房间等），path 由后端生成</span>
      </div>

      <!-- 工具栏：新增 + 本地搜索 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" @click="handleAdd">
            <span class="btn-plus">+</span>
            <span>新增位置</span>
          </el-button>
        </div>
        <el-input v-model="keyword" class="search-box" placeholder="搜索位置名称" clearable>
          <template #prefix>
            <svg width="14" height="14" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="9" cy="9" r="6" style="stroke: var(--color-text-4)" stroke-width="1.5" />
              <line x1="13.5" y1="13.5" x2="17" y2="17" style="stroke: var(--color-text-4)" stroke-width="1.5" stroke-linecap="round" />
            </svg>
          </template>
        </el-input>
      </div>

      <!-- 树形表格（row-key + tree-props） -->
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="treeData"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        border
        empty-text="暂无位置数据"
        @row-dblclick="handleEdit"
      >
        <el-table-column prop="name" label="位置名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="code" label="编码" min-width="140" show-overflow-tooltip :formatter="formatText" />
        <el-table-column prop="path" label="层级路径" min-width="220" show-overflow-tooltip :formatter="formatText" />
        <el-table-column prop="sortOrder" label="排序号" width="90" align="center" :formatter="formatText" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip :formatter="formatText" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm
              title="确认删除该位置吗？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="handleDelete(row)"
            >
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <LocationModal v-model:visible="modalVisible" :data="currentRecord" @success="handleSaved" />
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

/* 双击行可编辑：指针光标提示可交互 */
:deep(.el-table__row) {
  cursor: pointer;
}
</style>
