<script setup lang="ts">
import { useFormDirtyGuard } from '@/composables/useFormDirtyGuard'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, InputInstance } from 'element-plus'
import { basedataApi } from '@/api/modules/basedata'
import type { Location, LocationForm } from '@/api/interface/basedata'
import { useBasedataStore } from '@/stores/basedata'
import { buildTree } from '@/utils/tree'

const props = defineProps<{
  visible: boolean
  data?: Location
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success', item: Location): void
}>()

const basedataStore = useBasedataStore()

const formRef = ref<FormInstance>()
const nameInputRef = ref<InputInstance>()
const loading = ref(false)
const isEdit = ref(false)

/* 表单未保存关闭拦截：X / Esc / 遮罩关闭时，已有修改则二次确认 */
const { guardBeforeClose } = useFormDirtyGuard({ visible: () => props.visible, form: () => formData })

const formData = reactive<LocationForm>({
  name: '',
  parentId: undefined,
  sortOrder: 0,
  remark: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入位置名称', trigger: 'blur' }],
}

/**
 * 父位置候选树：编辑时剔除自身及其子孙（防环——把自己挂到自己后代下）。
 */
const parentTree = computed(() => {
  const tree = buildTree<Location>(basedataStore.locations)
  if (!props.data) return tree
  const excluded = new Set<number>([props.data.id])
  const prune = (nodes: Array<Location & { children?: unknown[] }>): Array<Location & { children?: unknown[] }> => {
    const result: Array<Location & { children?: unknown[] }> = []
    for (const node of nodes) {
      if (excluded.has(node.id)) continue
      if (node.children?.length) {
        const children = prune(node.children as Array<Location & { children?: unknown[] }>)
        result.push({ ...node, children })
      } else {
        result.push({ ...node, children: undefined })
      }
    }
    return result
  }
  return prune(tree)
})

watch(
  () => props.visible,
  (val) => {
    if (val) {
      isEdit.value = !!props.data
      Object.assign(formData, {
        name: props.data?.name ?? '',
        parentId: props.data?.parentId,
        sortOrder: props.data?.sortOrder ?? 0,
        remark: props.data?.remark ?? '',
      })
      nextTick(() => formRef.value?.clearValidate())
    }
  },
)

const handleClose = () => emit('update:visible', false)

/** 组装提交载荷：文本 trim，空值归一为 undefined（顶级位置不发 parentId） */
const buildPayload = (): LocationForm => ({
  name: formData.name.trim(),
  parentId: formData.parentId || undefined,
  sortOrder: formData.sortOrder ?? 0,
  remark: formData.remark?.trim() || undefined,
})

const handleSubmit = async () => {
  if (loading.value) return /* 防重复提交 */
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    let saved: Location
    if (isEdit.value && props.data) {
      saved = await basedataApi.updateLocation(props.data.id, buildPayload())
      ElMessage.success('更新成功')
    } else {
      saved = await basedataApi.createLocation(buildPayload())
      ElMessage.success('新增成功')
    }
    emit('success', saved)
    handleClose()
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示（如同级重名/环 400）
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
    :title="isEdit ? '编辑位置' : '新增位置'"
    width="520px"
    :close-on-click-modal="false"
    :before-close="guardBeforeClose"
    @update:model-value="handleClose"
    @opened="nameInputRef?.focus()"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px" @submit.prevent="handleSubmit">
      <el-form-item label="位置名称" prop="name">
        <el-input ref="nameInputRef" v-model="formData.name" placeholder="请输入位置名称" :maxlength="100" />
      </el-form-item>
      <el-form-item label="父位置" prop="parentId">
        <el-tree-select
          v-model="formData.parentId"
          :data="parentTree"
          node-key="id"
          :props="{ label: 'name' }"
          check-strictly
          clearable
          filterable
          placeholder="不选则为顶级位置"
          class="full-width"
        />
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
