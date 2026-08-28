<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, TableInstance } from 'element-plus'
import { assetApi } from '@/api/modules/asset'
import { receiptApi } from '@/api/modules/receipt'
import { useUserStore } from '@/stores/user'
import { useBasedataStore } from '@/stores/basedata'
import UserSelector from '@/components/UserSelector.vue'
import type { Asset } from '@/api/interface/asset'
import type { ReceiveReceipt, ReceiptType } from '@/api/interface/receipt'
import { RECEIPT_TYPE_META } from '@/api/interface/receipt'
import type { Location } from '@/api/interface/basedata'
import { buildTree } from '@/utils/tree'

const props = defineProps<{
  visible: boolean
  /** 单据类型：RECEIVE-领用 BORROW-借用 */
  type: ReceiptType
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success', item: ReceiveReceipt): void
}>()

const userStore = useUserStore()
const basedataStore = useBasedataStore()
const formRef = ref<FormInstance>()
const tableRef = ref<TableInstance>()
const submitting = ref(false)

const typeLabel = computed(() => RECEIPT_TYPE_META[props.type].label)

const locationTree = computed(() => buildTree<Location>(basedataStore.locations))

/* ---------------- 表单：领用区域 + 部门 + 事由 + 指定处理人 ---------------- */
const formData = reactive({
  locationId: undefined as number | undefined,
  department: '',
  reason: '',
  assigneeUserId: undefined as number | undefined,
  assigneeUserName: '',
})

const rules: FormRules = {
  locationId: [{ required: true, message: '请选择领用区域', trigger: 'change' }],
  department: [{ required: true, message: '请输入领用部门', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入领用事由', trigger: 'blur' }],
  assigneeUserId: [{ type: 'number', message: '指定处理人格式不正确', trigger: 'change' }],
}

/* ---------------- 资产选择器：闲置资产服务端分页 + 跨页多选 ---------------- */
const PAGE_SIZE = 10
const tableData = ref<Asset[]>([])
const tableLoading = ref(false)
const currentPage = ref(1)
const total = ref(0)
const keyword = ref('')
const searchKeyword = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

watch(keyword, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKeyword.value = val.trim()
    currentPage.value = 1
    loadAssets()
  }, 300)
})
onBeforeUnmount(() => searchTimer && clearTimeout(searchTimer))

/** 已选资产（跨页保持；id → Asset） */
const selectedAssets = ref<Map<number, Asset>>(new Map())
const selectedList = computed(() => [...selectedAssets.value.values()])

/** 恢复勾选期间跳过 selection-change（防止 data 替换时的清空事件误删已选项） */
let restoring = false

const loadAssets = async () => {
  tableLoading.value = true
  try {
    const resp = await assetApi.getAssets({
      page: currentPage.value,
      size: PAGE_SIZE,
      status: 'IDLE',
      keyword: searchKeyword.value || undefined,
    })
    tableData.value = resp.records
    total.value = resp.total
    // 跨页选择恢复：勾选当前页中已在已选集合的行
    restoring = true
    await nextTick()
    tableData.value.forEach((row) => {
      if (selectedAssets.value.has(row.id)) tableRef.value?.toggleRowSelection(row, true)
    })
    await nextTick()
    restoring = false
  } finally {
    tableLoading.value = false
  }
}

const onSelectionChange = (rows: Asset[]) => {
  if (restoring) return
  const pageIds = new Set(tableData.value.map((a) => a.id))
  const rowIds = new Set(rows.map((a) => a.id))
  pageIds.forEach((id) => {
    if (rowIds.has(id)) {
      const asset = tableData.value.find((a) => a.id === id)
      if (asset) selectedAssets.value.set(id, asset)
    } else {
      selectedAssets.value.delete(id)
    }
  })
}

/** 已选清单中移除（同步清掉主表格勾选态） */
const removeSelected = (id: number) => {
  selectedAssets.value.delete(id)
  const row = tableData.value.find((a) => a.id === id)
  if (row) tableRef.value?.toggleRowSelection(row, false)
}

const onPageChange = (page: number) => {
  currentPage.value = page
  loadAssets()
}

/* ---------------- 打开/关闭 ---------------- */
watch(
  () => props.visible,
  (val) => {
    if (val) {
      formData.locationId = undefined
      formData.department = userStore.me?.dept || ''
      formData.reason = ''
      formData.assigneeUserId = undefined
      formData.assigneeUserName = ''
      selectedAssets.value = new Map()
      keyword.value = ''
      searchKeyword.value = ''
      currentPage.value = 1
      loadAssets()
      // 领用区域树：通常已预载，空时按需拉取
      if (!basedataStore.locations.length) void basedataStore.fetchLocations()
      nextTick(() => formRef.value?.clearValidate())
    }
  },
)

const handleClose = () => emit('update:visible', false)

const handleSubmit = async () => {
  if (submitting.value) return /* 防重复提交 */
  if (!selectedList.value.length) {
    ElMessage.warning(`请至少选择一台资产`)
    return
  }
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const created = await receiptApi.apply({
      type: props.type,
      assetIds: selectedList.value.map((a) => a.id),
      locationId: formData.locationId!,
      department: formData.department,
      reason: formData.reason,
      assigneeUserId: formData.assigneeUserId,
      assigneeUserName: formData.assigneeUserName.trim() || undefined,
    })
    ElMessage.success(`${typeLabel.value}申请已提交，等待审批`)
    emit('success', created)
    handleClose()
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示（如 409 重复占用）
  } finally {
    submitting.value = false
  }
}

/** Ctrl/Cmd+S 提交（Esc 由对话框默认行为关闭） */
const onWindowKeydown = (e: KeyboardEvent) => {
  if (!props.visible) return
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
    e.preventDefault()
    if (!submitting.value) handleSubmit()
  }
}
onMounted(() => window.addEventListener('keydown', onWindowKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onWindowKeydown))
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="`发起${typeLabel}申请`"
    width="920px"
    top="6vh"
    :close-on-click-modal="false"
    @update:model-value="handleClose"
  >
    <div class="apply-layout">
      <!-- 左侧：闲置资产选择器 -->
      <div class="picker">
        <div class="picker-header">
          <span class="picker-title">选择闲置资产</span>
          <el-input
            v-model="keyword"
            class="picker-search"
            placeholder="搜索编码 / 名称 / 序列号"
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

        <el-table
          ref="tableRef"
          v-loading="tableLoading"
          :data="tableData"
          row-key="id"
          border
          height="320"
          empty-text="暂无闲置资产"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="46" :selectable="() => true" />
          <el-table-column prop="barcode" label="资产编码" min-width="130" show-overflow-tooltip />
          <el-table-column prop="name" label="资产名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="modelName" label="型号" min-width="110" show-overflow-tooltip />
          <el-table-column prop="locationName" label="位置" min-width="110" show-overflow-tooltip />
        </el-table>

        <div class="picker-pagination">
          <span class="picker-total">共 {{ total }} 台</span>
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="PAGE_SIZE"
            :total="total"
            layout="prev, pager, next"
            background
            small
            @current-change="onPageChange"
          />
        </div>
      </div>

      <!-- 右侧：表单 + 已选清单 -->
      <div class="form-side">
        <el-form ref="formRef" :model="formData" :rules="rules" label-width="80px" @submit.prevent="handleSubmit">
          <el-form-item label="领用区域" prop="locationId">
            <el-tree-select
              v-model="formData.locationId"
              :data="locationTree"
              node-key="id"
              :props="{ label: 'name' }"
              check-strictly
              clearable
              filterable
              placeholder="审批通过后资产位置更新至此"
              class="full-width"
            />
          </el-form-item>
          <el-form-item label="领用部门" prop="department">
            <el-input v-model="formData.department" placeholder="请输入领用部门" :maxlength="100" />
          </el-form-item>
          <el-form-item label="领用事由" prop="reason">
            <el-input
              v-model="formData.reason"
              type="textarea"
              :rows="3"
              placeholder="请输入领用事由"
              :maxlength="500"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="指定处理人" prop="assigneeUserId">
            <UserSelector
              v-model="formData.assigneeUserId"
              v-model:user-name="formData.assigneeUserName"
              placeholder="选填：定向派单给某人处理，否则进入共享池"
            />
          </el-form-item>
          <div class="form-tip">不指定处理人时，该单进入"共享池"，任何有权限的用户均可处理</div>
        </el-form>

        <div class="selected-header">
          <span>已选资产（{{ selectedList.length }}）</span>
          <el-button v-if="selectedList.length" link type="danger" size="small" @click="selectedAssets.clear()">
            清空
          </el-button>
        </div>
        <div class="selected-list">
          <div v-if="!selectedList.length" class="selected-empty">从左侧勾选资产，可跨页累积</div>
          <el-tag
            v-for="asset in selectedList"
            :key="asset.id"
            class="selected-tag"
            closable
            @close="removeSelected(asset.id)"
          >
            {{ asset.barcode }} {{ asset.name }}
          </el-tag>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!selectedList.length" @click="handleSubmit">
        提交申请{{ selectedList.length ? `（${selectedList.length} 台）` : '' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.apply-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

/* 左侧资产选择器 */
.picker {
  flex: 1.4;
  min-width: 0;
}

.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.picker-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text-1);
  flex-shrink: 0;
}

.picker-search {
  width: 220px;
}

.picker-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.picker-total {
  font-size: var(--text-sm);
  color: var(--color-text-3);
}

/* 右侧表单与已选清单 */
.form-side {
  flex: 1;
  min-width: 0;
}

.full-width {
  width: 100%;
}

.selected-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text-1);
  margin: 4px 0 8px;
}

.selected-list {
  min-height: 160px;
  max-height: 240px;
  overflow: auto;
  padding: 8px;
  background: var(--color-bg-3);
  border-radius: var(--radius-md);
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-content: flex-start;
}

.selected-empty {
  width: 100%;
  text-align: center;
  color: var(--color-text-3);
  font-size: var(--text-sm);
  padding: 60px 0;
}

.selected-tag {
  max-width: 100%;
}

.form-tip {
  font-size: var(--text-xs);
  color: var(--color-text-3);
  line-height: 18px;
  margin: -4px 0 8px;
}

:deep(.selected-tag .el-tag__content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
