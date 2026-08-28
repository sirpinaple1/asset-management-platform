<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, TableInstance } from 'element-plus'
import { assetApi } from '@/api/modules/asset'
import { transferApi } from '@/api/modules/transfer'
import { useBasedataStore } from '@/stores/basedata'
import UserSelector from '@/components/UserSelector.vue'
import type { Asset, AssetStatus } from '@/api/interface/asset'
import type { TransferOrder } from '@/api/interface/transfer'
import type { Location } from '@/api/interface/basedata'
import { buildTree } from '@/utils/tree'

/**
 * 发起调拨弹窗：左侧资产选择器（闲置/在用可切换，服务端分页、跨页多选）
 * + 右侧调入信息（位置树选 + 调入部门 + 调拨原因）与已选清单。
 */
const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success', item: TransferOrder): void
}>()

const basedataStore = useBasedataStore()
const formRef = ref<FormInstance>()
const tableRef = ref<TableInstance>()
const submitting = ref(false)

/* ---------------- 表单：调入位置 + 调入部门 + 调入负责人（已升级选人器）+ 指定处理人 + 调拨原因 ---------------- */
const formData = reactive({
  toLocationId: undefined as number | undefined,
  toDepartment: '',
  toUserId: undefined as number | undefined,
  toUserName: '',
  assigneeUserId: undefined as number | undefined,
  assigneeUserName: '',
  reason: '',
})

const locationTree = computed(() => buildTree<Location>(basedataStore.locations))

/** 调入位置与调入部门至少填一项（服务端同步校验，前端预检减少一次 400 往返） */
const validateAtLeastOne = (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => {
  if (!formData.toLocationId && !formData.toDepartment.trim()) {
    callback(new Error('调入位置与调入部门至少填写一项'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  toLocationId: [{ validator: validateAtLeastOne, trigger: 'change' }],
  toDepartment: [
    { max: 100, message: '调入部门长度不能超过 100', trigger: 'blur' },
    { validator: validateAtLeastOne, trigger: 'blur' },
  ],
  toUserId: [{ type: 'number', message: '调入负责人格式不正确', trigger: 'change' }],
  assigneeUserId: [{ type: 'number', message: '指定处理人格式不正确', trigger: 'change' }],
  reason: [{ max: 500, message: '调拨原因长度不能超过 500', trigger: 'blur' }],
}

/* ---------------- 资产选择器：闲置/在用切换 + 服务端分页 + 跨页多选 ---------------- */
/** 可发起调拨的资产状态：闲置（位置调整）/ 在用（随人转部门），待确认与报废不可调 */
type PickerStatus = Extract<AssetStatus, 'IDLE' | 'IN_USE'>
const pickerStatus = ref<PickerStatus>('IDLE')
const pickerStatusOptions: { value: PickerStatus; label: string }[] = [
  { value: 'IDLE', label: '闲置' },
  { value: 'IN_USE', label: '在用' },
]

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

/** 已选资产（跨页/跨状态保持；id → Asset） */
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
      status: pickerStatus.value,
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

/* 切换闲置/在用：回第一页重查（已选项保留，标签可区分） */
watch(pickerStatus, () => {
  currentPage.value = 1
  loadAssets()
})

/* ---------------- 打开/关闭 ---------------- */
watch(
  () => props.visible,
  (val) => {
    if (val) {
      formData.toLocationId = undefined
      formData.toDepartment = ''
      formData.toUserId = undefined
      formData.toUserName = ''
      formData.assigneeUserId = undefined
      formData.assigneeUserName = ''
      formData.reason = ''
      selectedAssets.value = new Map()
      pickerStatus.value = 'IDLE'
      keyword.value = ''
      searchKeyword.value = ''
      currentPage.value = 1
      loadAssets()
      // 调入位置树：列表页通常已预载，空时按需拉取
      if (!basedataStore.locations.length) void basedataStore.fetchLocations()
      nextTick(() => formRef.value?.clearValidate())
    }
  },
)

const handleClose = () => emit('update:visible', false)

const handleSubmit = async () => {
  if (submitting.value) return /* 防重复提交 */
  if (!selectedList.value.length) {
    ElMessage.warning('请至少选择一台资产')
    return
  }
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const created = await transferApi.apply({
      assetIds: selectedList.value.map((a) => a.id),
      toLocationId: formData.toLocationId,
      toDepartment: formData.toDepartment.trim() || undefined,
      toUserId: formData.toUserId,
      toUserName: formData.toUserName.trim() || undefined,
      assigneeUserId: formData.assigneeUserId,
      assigneeUserName: formData.assigneeUserName.trim() || undefined,
      reason: formData.reason.trim() || undefined,
    })
    ElMessage.success('调拨申请已提交，等待调入方确认')
    emit('success', created)
    handleClose()
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
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
    title="发起调拨"
    width="920px"
    top="6vh"
    :close-on-click-modal="false"
    @update:model-value="handleClose"
  >
    <div class="apply-layout">
      <!-- 左侧：资产选择器 -->
      <div class="picker">
        <div class="picker-header">
          <el-radio-group v-model="pickerStatus" size="small">
            <el-radio-button v-for="opt in pickerStatusOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
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
          empty-text="暂无可调拨资产"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="46" :selectable="() => true" />
          <el-table-column prop="barcode" label="资产编码" min-width="130" show-overflow-tooltip />
          <el-table-column prop="name" label="资产名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="modelName" label="型号" min-width="110" show-overflow-tooltip />
          <el-table-column prop="locationName" label="当前位置" min-width="110" show-overflow-tooltip />
        </el-table>

        <div class="picker-pagination">
          <span class="picker-total">共 {{ total }} 台</span>
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="PAGE_SIZE"
            :total="total"
            layout="prev, pager, next"
            background
            size="small"
            @current-change="onPageChange"
          />
        </div>
      </div>

      <!-- 右侧：表单 + 已选清单 -->
      <div class="form-side">
        <el-form ref="formRef" :model="formData" :rules="rules" label-width="96px" @submit.prevent="handleSubmit">
          <el-form-item label="调入位置" prop="toLocationId">
            <el-tree-select
              v-model="formData.toLocationId"
              :data="locationTree"
              node-key="id"
              :props="{ label: 'name' }"
              check-strictly
              clearable
              filterable
              placeholder="与调入部门至少填一项"
              class="full-width"
            />
          </el-form-item>
          <el-form-item label="调入部门" prop="toDepartment">
            <el-input v-model="formData.toDepartment" placeholder="与调入位置至少填一项" :maxlength="100" />
          </el-form-item>
          <el-form-item label="调入负责人" prop="toUserId">
            <UserSelector
              v-model="formData.toUserId"
              v-model:user-name="formData.toUserName"
              placeholder="选填：确认后资产由该负责人持有（在用）"
            />
          </el-form-item>
          <el-form-item label="指定处理人" prop="assigneeUserId">
            <UserSelector
              v-model="formData.assigneeUserId"
              v-model:user-name="formData.assigneeUserName"
              placeholder="选填：定向派单给某人确认，否则进入共享池"
            />
          </el-form-item>
          <el-form-item label="调拨原因" prop="reason">
            <el-input
              v-model="formData.reason"
              type="textarea"
              :rows="3"
              placeholder="请输入调拨原因（选填）"
              :maxlength="500"
              show-word-limit
            />
          </el-form-item>
          <div class="terminal-hint">
            确认后：填负责人 → 资产转其持有（在用）；只填部门 → 部门持有（在用）；只填区域 → 调拨回库（闲置）
          </div>
        </el-form>

        <div class="selected-header">
          <span>已选资产（{{ selectedList.length }}）</span>
          <el-button v-if="selectedList.length" link type="danger" size="small" @click="selectedAssets.clear()">
            清空
          </el-button>
        </div>
        <div class="selected-list">
          <div v-if="!selectedList.length" class="selected-empty">从左侧勾选资产，可跨页/跨状态累积</div>
          <el-tag
            v-for="asset in selectedList"
            :key="asset.id"
            class="selected-tag"
            :type="asset.status === 'IN_USE' ? 'success' : 'info'"
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
        提交调拨{{ selectedList.length ? `（${selectedList.length} 台）` : '' }}
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

.picker-search {
  width: 200px;
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

.terminal-hint {
  font-size: var(--text-xs);
  color: var(--color-text-3);
  line-height: 1.6;
  padding: 8px 0 0;
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

:deep(.selected-tag .el-tag__content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
