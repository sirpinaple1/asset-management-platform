<script setup lang="ts">
import { useFormDirtyGuard } from '@/composables/useFormDirtyGuard'
/**
 * 审批链配置新增/编辑弹窗（仅超管）：
 * - 配置类型：部门主管（键=部门路径，从用户目录聚合的候选下拉）/ 领料仓管理员（键=位置树选择，提交位置 id 字符串）
 * - 审批人：UserSelector 远程搜索（B1）
 * - 后端 400（审批人不存在 / 同键重复配置）由拦截器展示
 */
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { basedataApi } from '@/api/modules/basedata'
import { userApi } from '@/api/modules/user'
import type { ApprovalConfig, ApprovalConfigForm, ApprovalConfigType } from '@/api/interface/basedata'
import { useBasedataStore } from '@/stores/basedata'
import { buildTree } from '@/utils/tree'
import type { Location } from '@/api/interface/basedata'
import UserSelector from '@/components/UserSelector.vue'

const props = defineProps<{
  visible: boolean
  data?: ApprovalConfig | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success', item: ApprovalConfig): void
}>()

const basedataStore = useBasedataStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = ref(false)

/* 表单未保存关闭拦截：X / Esc / 遮罩关闭时，已有修改则二次确认 */
const { guardBeforeClose } = useFormDirtyGuard({ visible: () => props.visible, form: () => formData })

const formData = reactive<ApprovalConfigForm & { locationId?: number }>({
  configType: 'DEPT_SUPERVISOR',
  configKey: '',
  approverUserId: undefined,
  remark: '',
  locationId: undefined,
})

/** WAREHOUSE_KEEPER 时 configKey = 位置 id（字符串），选择器双绑转存 */
const isWarehouse = computed(() => formData.configType === 'WAREHOUSE_KEEPER')

const locationTree = computed(() => buildTree<Location>(basedataStore.locations))

/**
 * 部门路径候选：从用户目录（/v1/users 全量分页）聚合 distinct dept。
 * 只保留钉钉标准路径（含 /），并派生逐级上级前缀供"逐级向上回退"配粗粒度键；
 * 未同步用户的旧部门值（无 / 的杂乱写法）不进候选。
 */
const deptCandidates = ref<string[]>([])
const deptLoading = ref(false)
let deptFetched = false

async function fetchDeptCandidates() {
  if (deptFetched) return
  deptLoading.value = true
  try {
    const standard = new Set<string>()
    const PAGE = 100
    for (let page = 1; page <= 30; page++) {
      const resp = await userApi.searchUsers('', page, PAGE)
      for (const u of resp.records || []) {
        const d = u.dept?.trim()
        if (!d || !d.includes('/')) continue
        const segs = d.split('/')
        for (let i = 1; i <= segs.length; i++) standard.add(segs.slice(0, i).join('/'))
      }
      if ((resp.records?.length ?? 0) < PAGE || page * PAGE >= resp.total) break
    }
    deptCandidates.value = [...standard].sort((a, b) => a.localeCompare(b, 'zh'))
    deptFetched = true
  } catch {
    /* 候选拉取失败不阻塞弹窗：仍可手工输入 */
  } finally {
    deptLoading.value = false
  }
}

const rules = computed<FormRules>(() => ({
  configKey: isWarehouse.value
    ? [{ required: false, message: '', trigger: 'change' }]
    : [
        { required: true, message: '请选择部门路径', trigger: 'change' },
        { max: 255, message: '部门路径长度不能超过 255', trigger: 'blur' },
      ],
  locationId: isWarehouse.value
    ? [{ required: true, message: '请选择领用区域', trigger: 'change' }]
    : [{ required: false, message: '', trigger: 'change' }],
  approverUserId: [{ required: true, message: '请选择审批人', trigger: 'change' }],
  remark: [{ max: 500, message: '备注长度不能超过 500', trigger: 'blur' }],
}))

watch(
  () => props.visible,
  (val) => {
    if (!val) return
    isEdit.value = !!props.data
    Object.assign(formData, {
      configType: props.data?.configType ?? 'DEPT_SUPERVISOR',
      configKey: props.data?.configKey ?? '',
      approverUserId: props.data?.approverUserId,
      remark: props.data?.remark ?? '',
      locationId:
        props.data?.configType === 'WAREHOUSE_KEEPER' ? Number(props.data?.configKey) || undefined : undefined,
    })
    nextTick(() => formRef.value?.clearValidate())
    if (!basedataStore.locations.length) void basedataStore.fetchLocations()
    void fetchDeptCandidates()
  },
)

/** 类型切换时清空键（部门路径与位置 id 互不通用） */
watch(
  () => formData.configType,
  () => {
    formData.configKey = ''
    formData.locationId = undefined
    nextTick(() => formRef.value?.clearValidate(['configKey', 'locationId']))
  },
)

const handleClose = () => emit('update:visible', false)

const handleSubmit = async () => {
  if (loading.value) return
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return

  const payload: ApprovalConfigForm = {
    configType: formData.configType,
    configKey: isWarehouse.value ? String(formData.locationId ?? '') : formData.configKey.trim(),
    approverUserId: formData.approverUserId!,
    remark: formData.remark?.trim() || undefined,
  }

  loading.value = true
  try {
    let saved: ApprovalConfig
    if (isEdit.value && props.data) {
      saved = await basedataApi.updateApprovalConfig(props.data.id, payload)
      ElMessage.success('更新成功')
    } else {
      saved = await basedataApi.createApprovalConfig(payload)
      ElMessage.success('新增成功')
    }
    emit('success', saved)
    handleClose()
  } catch {
    /* 拦截器已提示（重复配置/审批人不存在 400、非超管 403） */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑审批链配置' : '新增审批链配置'"
    width="520px"
    :close-on-click-modal="false"
    :before-close="guardBeforeClose"
    @update:model-value="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="96px"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="配置类型" prop="configType">
        <el-radio-group v-model="formData.configType" :disabled="isEdit">
          <el-radio-button value="DEPT_SUPERVISOR">部门主管</el-radio-button>
          <el-radio-button value="WAREHOUSE_KEEPER">领料仓管理员</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <!-- 部门主管：键 = 部门路径（用户目录聚合候选，支持手工输入兜底） -->
      <el-form-item v-if="!isWarehouse" label="部门路径" prop="configKey">
        <el-select
          v-model="formData.configKey"
          filterable
          allow-create
          default-first-option
          :loading="deptLoading"
          placeholder="搜索选择部门，或直接输入路径"
          class="full-width"
        >
          <el-option v-for="d in deptCandidates" :key="d" :label="d" :value="d" />
        </el-select>
        <div class="field-hint">候选为钉钉真实组织架构（含逐级前缀，可配粗粒度）；发起人部门精确匹配，逐级向上回退命中配置</div>
      </el-form-item>

      <!-- 仓管：键 = 位置 id（树选择） -->
      <el-form-item v-else label="领用区域" prop="locationId">
        <el-tree-select
          v-model="formData.locationId"
          :data="locationTree"
          node-key="id"
          :props="{ label: 'name' }"
          check-strictly
          clearable
          filterable
          placeholder="请选择领用区域（位置）"
          class="full-width"
        />
      </el-form-item>

      <el-form-item label="审批人" prop="approverUserId">
        <UserSelector
          v-model="formData.approverUserId"
          :user-name="isEdit ? props.data?.approverUserName : undefined"
          placeholder="搜索工号 / 姓名 / 部门选择审批人"
          class="full-width"
        />
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="备注信息（不超过 500 字）"
          :maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        {{ isEdit ? '保存' : '新增' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.field-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  margin-top: 4px;
}

.full-width {
  width: 100%;
}
</style>
