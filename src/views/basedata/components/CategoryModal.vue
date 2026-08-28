<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, InputInstance } from 'element-plus'
import { basedataApi } from '@/api/modules/basedata'
import type { Category, CategoryForm } from '@/api/interface/basedata'
import { useBasedataStore } from '@/stores/basedata'

const props = defineProps<{
  visible: boolean
  data?: Category
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success', item: Category): void
}>()

const basedataStore = useBasedataStore()

const formRef = ref<FormInstance>()
const nameInputRef = ref<InputInstance>()
const loading = ref(false)
const isEdit = ref(false)

const formData = reactive<CategoryForm>({
  name: '',
  parentId: undefined,
  barcodePrefix: '',
  sortOrder: 0,
  remark: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
}

/** 父分类候选：仅一级分类（parentId 为空）可作父；编辑时排除自身 */
const parentOptions = computed(() =>
  basedataStore.categories.filter((c) => c.parentId == null && c.id !== props.data?.id),
)

watch(
  () => props.visible,
  (val) => {
    if (val) {
      isEdit.value = !!props.data
      Object.assign(formData, {
        name: props.data?.name ?? '',
        parentId: props.data?.parentId,
        barcodePrefix: props.data?.barcodePrefix ?? '',
        sortOrder: props.data?.sortOrder ?? 0,
        remark: props.data?.remark ?? '',
      })
      nextTick(() => formRef.value?.clearValidate())
    }
  },
)

const handleClose = () => emit('update:visible', false)

/** 组装提交载荷：文本 trim，空值归一为 undefined（顶级分类不发 parentId） */
const buildPayload = (): CategoryForm => ({
  name: formData.name.trim(),
  parentId: formData.parentId || undefined,
  barcodePrefix: formData.barcodePrefix?.trim() || undefined,
  sortOrder: formData.sortOrder ?? 0,
  remark: formData.remark?.trim() || undefined,
})

const handleSubmit = async () => {
  if (loading.value) return /* 防重复提交 */
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    let saved: Category
    if (isEdit.value && props.data) {
      saved = await basedataApi.updateCategory(props.data.id, buildPayload())
      ElMessage.success('更新成功')
    } else {
      saved = await basedataApi.createCategory(buildPayload())
      ElMessage.success('新增成功')
    }
    emit('success', saved)
    handleClose()
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示（如同级重名 400）
  } finally {
    loading.value = false
  }
}

/** Ctrl/Cmd+S 保存（Enter 由表单 submit.prevent 处理，Esc 由对话框默认行为关闭） */
const onWindowKeydown = (e: KeyboardEvent) => {
  if (!props.visible) return
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
    e.preventDefault()
    if (!loading.value) handleSubmit()
  }
}
onMounted(() => window.addEventListener('keydown', onWindowKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onWindowKeydown))
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑分类' : '新增分类'"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="handleClose"
    @opened="nameInputRef?.focus()"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px" @submit.prevent="handleSubmit">
      <el-form-item label="分类名称" prop="name">
        <el-input ref="nameInputRef" v-model="formData.name" placeholder="请输入分类名称" :maxlength="100" />
      </el-form-item>
      <el-form-item label="父分类" prop="parentId">
        <el-select
          v-model="formData.parentId"
          clearable
          filterable
          placeholder="不选则为顶级分类"
          class="full-width"
        >
          <el-option
            v-for="c in parentOptions"
            :key="c.id"
            :label="c.name"
            :value="c.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="编码前缀" prop="barcodePrefix">
        <el-input v-model="formData.barcodePrefix" placeholder="新增资产编码前缀（空则 SK）" :maxlength="20" />
      </el-form-item>
      <el-form-item label="排序号" prop="sortOrder">
        <el-input-number v-model="formData.sortOrder" :min="0" :precision="0" :step="1" class="full-width" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          placeholder="请输入备注"
          :maxlength="200"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}
</style>
