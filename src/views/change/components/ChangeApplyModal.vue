<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, TableInstance } from 'element-plus'
import { assetApi } from '@/api/modules/asset'
import { changeApi } from '@/api/modules/change'
import { useBasedataStore } from '@/stores/basedata'
import UserSelector from '@/components/UserSelector.vue'
import type { Asset, AssetStatus } from '@/api/interface/asset'
import type { ChangeOrder } from '@/api/interface/change'
import type { Location } from '@/api/interface/basedata'
import { buildTree } from '@/utils/tree'

/**
 * 发起实物信息变更弹窗：左侧资产选择器（闲置/在用可切换，服务端分页、跨页多选）
 * + 右侧变更内容（五个 new_* 字段至少填一项）与已选清单。
 * 一单 = N 台资产 + 一组统一新值，各字段 null = 不变更；
 * 变更前值由服务端逐台快照（与当前值相同的字段不进明细行），确认执行时才真正更新 asset。
 */
const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success', item: ChangeOrder): void
}>()

const basedataStore = useBasedataStore()
const formRef = ref<FormInstance>()
const tableRef = ref<TableInstance>()
const submitting = ref(false)

/* ---------------- 表单：变更字段（new_* 五选一以上）+ 指定处理人 + 变更原因 ---------------- */
const formData = reactive({
  newUserId: undefined as number | undefined,
  newUserName: '',
  newUserDepartment: '',
  newLocationId: undefined as number | undefined,
  newLocationDetail: '',
  newCompanyId: undefined as number | undefined,
  assigneeUserId: undefined as number | undefined,
  assigneeUserName: '',
  reason: '',
})

const locationTree = computed(() => buildTree<Location>(basedataStore.locations))

/** 五个 new_* 至少填一项（服务端 400 同步校验，前端预检减少一次往返） */
const validateAtLeastOne = (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => {
  if (
    !formData.newUserId &&
    !formData.newUserDepartment.trim() &&
    !formData.newLocationId &&
    !formData.newLocationDetail.trim() &&
    !formData.newCompanyId
  ) {
    callback(new Error('请至少指定一项变更内容（使用人/使用部门/区域/存放位置明细/归属公司）'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  newUserId: [
    { type: 'number', message: '使用人格式不正确', trigger: 'change' },
    { validator: validateAtLeastOne, trigger: 'change' },
  ],
  newUserName: [{ max: 100, message: '使用人姓名长度不能超过 100', trigger: 'blur' }],
  newUserDepartment: [
    { max: 100, message: '使用部门长度不能超过 100', trigger: 'blur' },
    { validator: validateAtLeastOne, trigger: 'blur' },
  ],
  newLocationId: [{ validator: validateAtLeastOne, trigger: 'change' }],
  newLocationDetail: [
    { max: 200, message: '存放位置明细长度不能超过 200', trigger: 'blur' },
    { validator: validateAtLeastOne, trigger: 'blur' },
  ],
  newCompanyId: [{ validator: validateAtLeastOne, trigger: 'change' }],
  assigneeUserId: [{ type: 'number', message: '指定处理人格式不正确', trigger: 'change' }],
  reason: [{ max: 500, message: '变更原因长度不能超过 500', trigger: 'blur' }],
}

/* ---------------- 资产选择器：闲置/在用切换 + 服务端分页 + 跨页多选 ---------------- */
/** 可发起变更的资产状态：闲置/在用均可变更实物信息；待确认（单据占用）与报废不可选 */
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
      formData.newUserId = undefined
      formData.newUserName = ''
      formData.newUserDepartment = ''
      formData.newLocationId = undefined
      formData.newLocationDetail = ''
      formData.newCompanyId = undefined
      formData.assigneeUserId = undefined
      formData.assigneeUserName = ''
      formData.reason = ''
      selectedAssets.value = new Map()
      pickerStatus.value = 'IDLE'
      keyword.value = ''
      searchKeyword.value = ''
      currentPage.value = 1
      loadAssets()
      // 区域树 / 公司下拉：列表页通常已预载，空时按需拉取
      if (!basedataStore.locations.length) void basedataStore.fetchLocations()
      if (!basedataStore.companies.length) void basedataStore.fetchCompanies()
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
    const created = await changeApi.apply({
      assetIds: selectedList.value.map((a) => a.id),
      newUserId: formData.newUserId,
      newUserName: formData.newUserName.trim() || undefined,
      newUserDepartment: formData.newUserDepartment.trim() || undefined,
      newLocationId: formData.newLocationId,
      newLocationDetail: formData.newLocationDetail.trim() || undefined,
      newCompanyId: formData.newCompanyId,
      assigneeUserId: formData.assigneeUserId,
      assigneeUserName: formData.assigneeUserName.trim() || undefined,
      reason: formData.reason.trim() || undefined,
    })
    ElMessage.success('变更申请已提交，等待确认执行')
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
    title="发起实物信息变更"
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
                <circle cx="9" cy="9" r="6" stroke="#9CA3AF" stroke-width="1.5" />
                <line x1="13.5" y1="13.5" x2="17" y2="17" stroke="#9CA3AF" stroke-width="1.5" stroke-linecap="round" />
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
          empty-text="暂无可变更资产"
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

      <!-- 右侧：变更内容表单 + 已选清单 -->
      <div class="form-side">
        <el-form ref="formRef" :model="formData" :rules="rules" label-width="96px" @submit.prevent="handleSubmit">
          <el-form-item label="使用人" prop="newUserId">
            <UserSelector
              v-model="formData.newUserId"
              v-model:user-name="formData.newUserName"
              placeholder="选填：确认后资产使用人更新为此人"
            />
          </el-form-item>
          <el-form-item label="使用部门" prop="newUserDepartment">
            <el-input v-model="formData.newUserDepartment" placeholder="变更后的使用部门" :maxlength="100" />
          </el-form-item>
          <el-form-item label="区域" prop="newLocationId">
            <el-tree-select
              v-model="formData.newLocationId"
              :data="locationTree"
              node-key="id"
              :props="{ label: 'name' }"
              check-strictly
              clearable
              filterable
              placeholder="变更后的区域"
              class="full-width"
            />
          </el-form-item>
          <el-form-item label="存放位置明细" prop="newLocationDetail">
            <el-input v-model="formData.newLocationDetail" placeholder="变更后的存放位置明细" :maxlength="200" />
          </el-form-item>
          <el-form-item label="归属公司" prop="newCompanyId">
            <el-select
              v-model="formData.newCompanyId"
              clearable
              filterable
              placeholder="变更后的归属公司"
              class="full-width"
            >
              <el-option
                v-for="company in basedataStore.companies"
                :key="company.id"
                :label="company.name"
                :value="company.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="变更原因" prop="reason">
            <el-input
              v-model="formData.reason"
              type="textarea"
              :rows="2"
              placeholder="请输入变更原因（选填）"
              :maxlength="500"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="指定处理人" prop="assigneeUserId">
            <UserSelector
              v-model="formData.assigneeUserId"
              v-model:user-name="formData.assigneeUserName"
              placeholder="选填：定向派单给某人确认，否则进入共享池"
            />
          </el-form-item>
          <div class="form-tip">仅填写的字段会被变更（至少一项）；与当前值相同的字段不生成明细行，确认执行后生效</div>
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
        提交变更{{ selectedList.length ? `（${selectedList.length} 台）` : '' }}
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
  margin-bottom: 10px;
}

.picker-search {
  width: 200px;
}

.picker-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.picker-total {
  font-size: 13px;
  color: #86909c;
}

/* 右侧表单与已选清单 */
.form-side {
  flex: 1;
  min-width: 0;
}

.full-width {
  width: 100%;
}

.form-tip {
  font-size: 12px;
  color: #86909c;
  line-height: 18px;
  margin: -4px 0 8px;
}

.selected-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  margin: 4px 0 10px;
}

.selected-list {
  min-height: 100px;
  max-height: 160px;
  overflow: auto;
  padding: 10px;
  background: #fafbfc;
  border-radius: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-content: flex-start;
}

.selected-empty {
  width: 100%;
  text-align: center;
  color: #86909c;
  font-size: 13px;
  padding: 34px 0;
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
