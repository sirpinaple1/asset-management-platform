<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, InputInstance } from 'element-plus'
import { basedataApi } from '@/api/modules/basedata'
import type { Supplier, SupplierForm } from '@/api/interface/basedata'

const props = defineProps<{
  visible: boolean
  data?: Supplier
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const formRef = ref<FormInstance>()
const nameInputRef = ref<InputInstance>()
const loading = ref(false)
const isEdit = ref(false)

const formData = reactive<SupplierForm>({
  name: '',
  contact: '',
  phone: '',
  address: '',
  remark: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  phone: [
    { pattern: /^[\d\-+() ]+$/, message: '联系电话格式不正确', trigger: 'blur' },
  ],
}

watch(
  () => props.visible,
  (val) => {
    if (val) {
      isEdit.value = !!props.data
      Object.assign(formData, {
        name: props.data?.name ?? '',
        contact: props.data?.contact ?? '',
        phone: props.data?.phone ?? '',
        address: props.data?.address ?? '',
        remark: props.data?.remark ?? '',
      })
      nextTick(() => formRef.value?.clearValidate())
    }
  },
)

const handleClose = () => emit('update:visible', false)

const handleSubmit = async () => {
  if (loading.value) return /* 防重复提交 */
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    if (isEdit.value && props.data) {
      await basedataApi.updateSupplier(props.data.id, { ...formData })
      ElMessage.success('更新成功')
    } else {
      await basedataApi.createSupplier({ ...formData })
      ElMessage.success('新增成功')
    }
    emit('success')
    handleClose()
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
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
    :title="isEdit ? '编辑供应商' : '新增供应商'"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="handleClose"
    @opened="nameInputRef?.focus()"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px" @submit.prevent="handleSubmit">
      <el-form-item label="供应商名称" prop="name">
        <el-input ref="nameInputRef" v-model="formData.name" placeholder="请输入供应商名称" :maxlength="100" />
      </el-form-item>
      <el-form-item label="联系人" prop="contact">
        <el-input v-model="formData.contact" placeholder="请输入联系人" :maxlength="50" />
      </el-form-item>
      <el-form-item label="联系电话" prop="phone">
        <el-input v-model="formData.phone" placeholder="请输入联系电话" :maxlength="30" />
      </el-form-item>
      <el-form-item label="地址" prop="address">
        <el-input v-model="formData.address" placeholder="请输入地址" :maxlength="200" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          placeholder="请输入备注"
          :maxlength="500"
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
