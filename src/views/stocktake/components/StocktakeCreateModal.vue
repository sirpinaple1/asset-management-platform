<script setup lang="ts">
import { useFormDirtyGuard } from '@/composables/useFormDirtyGuard'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { stocktakeApi } from '@/api/modules/stocktake'
import { useBasedataStore } from '@/stores/basedata'
import type { Stocktake } from '@/api/interface/stocktake'
import type { Category, Location } from '@/api/interface/basedata'
import { buildTree } from '@/utils/tree'

/**
 * 创建盘点任务弹窗：任务名称必填 + 盘点范围（位置树含子树 / 分类树，均可空=全库）+ 备注。
 * 创建时后端按范围快照非报废资产生成明细（范围内无资产 400）；
 * 创建人为当前登录人（后端 UserContext 取，前端不传）。
 */
const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success', item: Stocktake): void
}>()

const basedataStore = useBasedataStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)

/* 表单未保存关闭拦截：X / Esc / 遮罩关闭时，已有修改则二次确认 */
const { guardBeforeClose } = useFormDirtyGuard({ visible: () => props.visible, form: () => formData })

const formData = reactive({
  name: '',
  locationId: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  remark: '',
})

const locationTree = computed(() => buildTree<Location>(basedataStore.locations))
const categoryTree = computed(() => buildTree<Category>(basedataStore.categories))

/** 范围选择是否互斥提示：位置与分类可同时生效（交集），不强制 */
const rules: FormRules = {
  name: [
    { required: true, message: '请输入盘点任务名称', trigger: 'blur' },
    { max: 100, message: '任务名称长度不能超过 100', trigger: 'blur' },
  ],
  remark: [{ max: 500, message: '备注长度不能超过 500', trigger: 'blur' }],
}

/* ---------------- 打开/关闭 ---------------- */
watch(
  () => props.visible,
  (val) => {
    if (val) {
      formData.name = ''
      formData.locationId = undefined
      formData.categoryId = undefined
      formData.remark = ''
      // 位置树 / 分类树：列表页通常已预载，空时按需拉取
      if (!basedataStore.locations.length) void basedataStore.fetchLocations()
      if (!basedataStore.categories.length) void basedataStore.fetchCategories()
      nextTick(() => formRef.value?.clearValidate())
    }
  },
)

const handleClose = () => emit('update:visible', false)

const handleSubmit = async () => {
  if (submitting.value) return /* 防重复提交 */
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const created = await stocktakeApi.create({
      name: formData.name.trim(),
      locationId: formData.locationId,
      categoryId: formData.categoryId,
      remark: formData.remark.trim() || undefined,
    })
    ElMessage.success(`盘点任务已创建（快照 ${created.totalCount ?? 0} 台资产）`)
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
    title="创建盘点任务"
    width="560px"
    :close-on-click-modal="false"
    :before-close="guardBeforeClose"
    @update:model-value="handleClose"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="96px" @submit.prevent="handleSubmit">
      <el-form-item label="任务名称" prop="name">
        <el-input v-model="formData.name" placeholder="如：2026年8月全库盘点" :maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item label="盘点位置" prop="locationId">
        <el-tree-select
          v-model="formData.locationId"
          :data="locationTree"
          node-key="id"
          :props="{ label: 'name' }"
          check-strictly
          clearable
          filterable
          placeholder="不选 = 全库（含子树）"
          class="full-width"
        />
      </el-form-item>
      <el-form-item label="盘点分类" prop="categoryId">
        <el-tree-select
          v-model="formData.categoryId"
          :data="categoryTree"
          node-key="id"
          :props="{ label: 'name' }"
          check-strictly
          clearable
          filterable
          placeholder="不选 = 全部分类"
          class="full-width"
        />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          placeholder="选填"
          :maxlength="500"
          show-word-limit
        />
      </el-form-item>
      <div class="form-tip">
        创建时按范围快照范围内全部非报废资产生成待盘明细（范围内无资产将创建失败）；
        位置与分类可同时指定（取交集）；两者均不选 = 全库盘点。
      </div>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">创建任务</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
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
