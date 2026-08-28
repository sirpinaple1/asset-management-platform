<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, InputInstance } from 'element-plus'
import { assetApi } from '@/api/modules/asset'
import type { Asset, AssetForm } from '@/api/interface/asset'
import { useBasedataStore } from '@/stores/basedata'
import { useUserStore } from '@/stores/user'
import { buildTree } from '@/utils/tree'
import type { Category, Location } from '@/api/interface/basedata'

const props = defineProps<{
  visible: boolean
  data?: Asset
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success', item: Asset): void
}>()

const basedataStore = useBasedataStore()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const barcodeInputRef = ref<InputInstance>()
const nameInputRef = ref<InputInstance>()
const loading = ref(false)
const isEdit = ref(false)

/* ---------------- 编辑态字段权限（仅编辑生效，新增不控制） ---------------- */

/** 超级管理员（systemAdmin 角色；me 未加载时按普通用户处理，fail-closed） */
const isSuperAdmin = computed(() => userStore.me?.roles?.includes('systemAdmin') ?? false)

/**
 * 身份字段锁定（编辑时对所有人生效，含超管）：
 * 一物一码均由系统生成、身份信息一旦建立不可修改——
 * 编码/名称/序列号/细则/分类/型号/供应商/归属公司/使用人/管理员/购置日期/金额。
 */
const identityLocked = computed(() => isEdit.value)

/**
 * 运营字段锁定（编辑时仅超管可改）：
 * 当前位置/应归放位置必须随调拨单、领用单、借用单单据驱动变更。
 */
const operationalLocked = computed(() => isEdit.value && !isSuperAdmin.value)

/** 表单状态（字段与后端 AssetReq 一一对应；状态不开放编辑） */
const formData = reactive<AssetForm>({
  barcode: '',
  name: '',
  sn: '',
  spec: '',
  categoryId: undefined,
  modelId: undefined,
  supplierId: undefined,
  locationId: undefined,
  homeLocationId: undefined,
  locationDetail: '',
  userId: undefined,
  userDepartment: '',
  adminUserId: undefined,
  companyId: undefined,
  purchaseDate: '',
  amount: undefined,
  remark: '',
})

/** 校验规则（对齐后端 AssetReq 注解约束；barcode 仅编辑（改码场景）时必填，新增由服务端生成） */
const rules = computed<FormRules>(() => ({
  barcode: isEdit.value
    ? [
        { required: true, message: '请输入资产编码', trigger: 'blur' },
        { max: 100, message: '资产编码长度不能超过 100', trigger: 'blur' },
      ]
    : [],
  name: [
    { required: true, message: '请输入资产名称', trigger: 'blur' },
    { max: 200, message: '资产名称长度不能超过 200', trigger: 'blur' },
  ],
  sn: [{ max: 100, message: '序列号长度不能超过 100', trigger: 'blur' }],
  spec: [{ max: 500, message: '细则长度不能超过 500', trigger: 'blur' }],
  locationDetail: [{ max: 200, message: '存放位置明细长度不能超过 200', trigger: 'blur' }],
  userDepartment: [{ max: 100, message: '使用人部门长度不能超过 100', trigger: 'blur' }],
  remark: [{ max: 500, message: '备注长度不能超过 500', trigger: 'blur' }],
}))

/* ---------------- 基础数据选项 ---------------- */
const categoryTree = computed(() => buildTree<Category>(basedataStore.categories))
const locationTree = computed(() => buildTree<Location>(basedataStore.locations))

/** 型号候选：选中分类时仅显示该分类下的型号（分类→型号联动） */
const modelOptions = computed(() => {
  const cat = formData.categoryId
  return cat ? basedataStore.models.filter((m) => m.categoryId === cat) : basedataStore.models
})

/** 分类变更后，原选中型号不属于新分类时清空，避免提交悬空关联 */
watch(
  () => formData.categoryId,
  (cat) => {
    if (!formData.modelId) return
    const model = basedataStore.models.find((m) => m.id === formData.modelId)
    if (model?.categoryId != null && cat != null && model.categoryId !== cat) {
      formData.modelId = undefined
    }
  },
)

/* ---------------- 打开：回显 + 按需加载基础数据 ---------------- */
watch(
  () => props.visible,
  (val) => {
    if (!val) return
    isEdit.value = !!props.data
    Object.assign(formData, {
      barcode: props.data?.barcode ?? '',
      name: props.data?.name ?? '',
      sn: props.data?.sn ?? '',
      spec: props.data?.spec ?? '',
      categoryId: props.data?.categoryId,
      modelId: props.data?.modelId,
      supplierId: props.data?.supplierId,
      locationId: props.data?.locationId,
      homeLocationId: props.data?.homeLocationId,
      locationDetail: props.data?.locationDetail ?? '',
      userId: props.data?.userId,
      userDepartment: props.data?.userDepartment ?? '',
      adminUserId: props.data?.adminUserId,
      companyId: props.data?.companyId,
      purchaseDate: props.data?.purchaseDate ?? '',
      amount: props.data?.amount,
      remark: props.data?.remark ?? '',
    })
    nextTick(() => formRef.value?.clearValidate())

    // 分类/位置/公司通常已由列表页预载；供应商/型号由本弹窗按需拉取
    if (!basedataStore.suppliers.length) void basedataStore.fetchSuppliers()
    if (!basedataStore.models.length) void basedataStore.fetchModels()
    if (!basedataStore.categories.length) void basedataStore.fetchCategories()
    if (!basedataStore.locations.length) void basedataStore.fetchLocations()
    if (!basedataStore.companies.length) void basedataStore.fetchCompanies()
  },
)

const handleClose = () => emit('update:visible', false)

/**
 * 组装提交载荷：文本 trim，空值归一为 undefined（不发空串/0 金额）；新增不发 barcode（服务端生成）。
 * 权限防线：普通用户编辑时仅提交允许字段（备注/位置明细 + 后端必填的编码/名称原值），
 * 身份/运营字段不发——updateById 跳过 null 列保持原值，杜绝绕过禁用控件篡改。
 */
const buildPayload = (): AssetForm => {
  const full: AssetForm = {
    barcode: isEdit.value ? formData.barcode?.trim() : undefined,
    name: formData.name.trim(),
    sn: formData.sn?.trim() || undefined,
    spec: formData.spec?.trim() || undefined,
    categoryId: formData.categoryId || undefined,
    modelId: formData.modelId || undefined,
    supplierId: formData.supplierId || undefined,
    locationId: formData.locationId || undefined,
    homeLocationId: formData.homeLocationId || undefined,
    locationDetail: formData.locationDetail?.trim() || undefined,
    userId: formData.userId || undefined,
    userDepartment: formData.userDepartment?.trim() || undefined,
    adminUserId: formData.adminUserId || undefined,
    companyId: formData.companyId || undefined,
    purchaseDate: formData.purchaseDate || undefined,
    amount: formData.amount ?? undefined,
    remark: formData.remark?.trim() || undefined,
  }
  if (isEdit.value && !isSuperAdmin.value) {
    return {
      barcode: full.barcode,
      name: full.name,
      locationDetail: full.locationDetail,
      remark: full.remark,
    }
  }
  return full
}

const handleSubmit = async () => {
  if (loading.value) return /* 防重复提交 */
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    let saved: Asset
    if (isEdit.value && props.data) {
      saved = await assetApi.updateAsset(props.data.id, buildPayload())
      ElMessage.success('更新成功')
    } else {
      saved = await assetApi.createAsset(buildPayload())
      /* 编码由服务端生成（分类前缀-日期-序号），回显告知用户 */
      ElMessage.success(`新增成功，资产编码：${saved.barcode}`)
    }
    emit('success', saved)
    handleClose()
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示（如 barcode 冲突 409）
  } finally {
    loading.value = false
  }
}

/** Ctrl/Cmd+S 保存（Esc 由对话框默认行为关闭） */
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
    :title="isEdit ? '编辑资产' : '新增资产'"
    width="680px"
    :close-on-click-modal="false"
    @update:model-value="handleClose"
    @opened="(isEdit ? barcodeInputRef : nameInputRef)?.focus()"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="96px"
      @submit.prevent="handleSubmit"
    >
      <!-- 编辑态字段权限提示（新增不控制） -->
      <el-alert
        v-if="isEdit && !isSuperAdmin"
        type="info"
        :closable="false"
        show-icon
        title="身份信息与运营字段不可直接修改"
        description="资产编码等身份信息建立后锁定；当前位置/应归放位置随调拨、领用、借用单据自动变更；如需修正请联系超级管理员。可填写备注与位置明细反馈现场信息。"
        class="edit-perm-alert"
      />
      <el-alert
        v-else-if="isEdit"
        type="info"
        :closable="false"
        show-icon
        title="身份信息建立后锁定；当前位置/应归放位置可直改（常规变更请走单据）"
        class="edit-perm-alert"
      />
      <div class="form-grid">
        <!-- 基本信息 -->
        <!-- 新增：编码由服务端自动生成（分类前缀-日期-序号），不开放录入；编辑：身份字段锁定（一物一码由系统生成） -->
        <el-form-item v-if="isEdit" label="资产编码" prop="barcode">
          <el-input
            ref="barcodeInputRef"
            v-model="formData.barcode"
            placeholder="请输入资产编码（唯一）"
            :maxlength="100"
            :disabled="identityLocked"
          />
        </el-form-item>
        <el-form-item v-else label="资产编码">
          <el-input model-value="" disabled placeholder="提交后系统自动生成" />
        </el-form-item>
        <el-form-item label="资产名称" prop="name">
          <el-input ref="nameInputRef" v-model="formData.name" placeholder="请输入资产名称" :maxlength="200" :disabled="identityLocked" />
        </el-form-item>
        <el-form-item label="序列号" prop="sn">
          <el-input v-model="formData.sn" placeholder="请输入序列号" :maxlength="100" :disabled="identityLocked" />
        </el-form-item>
        <el-form-item label="细则" prop="spec">
          <el-input v-model="formData.spec" placeholder="如：16G内存/512G固态" :maxlength="500" :disabled="identityLocked" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-tree-select
            v-model="formData.categoryId"
            :data="categoryTree"
            node-key="id"
            :props="{ label: 'name' }"
            check-strictly
            clearable
            filterable
            placeholder="请选择分类"
            class="full-width"
            :disabled="identityLocked"
          />
        </el-form-item>
        <el-form-item label="型号" prop="modelId">
          <el-select
            v-model="formData.modelId"
            clearable
            filterable
            placeholder="请选择型号"
            class="full-width"
            no-data-text="该分类下暂无型号"
            :disabled="identityLocked"
          >
            <el-option
              v-for="m in modelOptions"
              :key="m.id"
              :label="m.modelNumber ? `${m.name}（${m.modelNumber}）` : m.name"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商" prop="supplierId">
          <el-select
            v-model="formData.supplierId"
            clearable
            filterable
            placeholder="请选择供应商"
            class="full-width"
            :disabled="identityLocked"
          >
            <el-option
              v-for="s in basedataStore.suppliers"
              :key="s.id"
              :label="s.name"
              :value="s.id"
            />
          </el-select>
        </el-form-item>

        <!-- 位置（当前位置/应归放位置：编辑时仅超管可改，常规变更走调拨/领用/借用单据） -->
        <el-form-item label="当前位置" prop="locationId">
          <el-tree-select
            v-model="formData.locationId"
            :data="locationTree"
            node-key="id"
            :props="{ label: 'name' }"
            check-strictly
            clearable
            filterable
            placeholder="请选择当前位置"
            class="full-width"
            :disabled="operationalLocked"
          />
        </el-form-item>
        <el-form-item label="应归放位置" prop="homeLocationId">
          <el-tree-select
            v-model="formData.homeLocationId"
            :data="locationTree"
            node-key="id"
            :props="{ label: 'name' }"
            check-strictly
            clearable
            filterable
            placeholder="请选择应归放位置"
            class="full-width"
            :disabled="operationalLocked"
          />
        </el-form-item>
        <el-form-item label="位置明细" prop="locationDetail">
          <el-input v-model="formData.locationDetail" placeholder="如：3 楼 A 区 05 货架" :maxlength="200" />
        </el-form-item>

        <!-- 归属与人员 -->
        <el-form-item label="归属公司" prop="companyId">
          <el-select
            v-model="formData.companyId"
            clearable
            filterable
            placeholder="请选择归属公司"
            class="full-width"
            :disabled="identityLocked"
          >
            <el-option
              v-for="c in basedataStore.companies"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="使用人" prop="userId">
          <el-input-number
            v-model="formData.userId"
            :disabled="identityLocked"
            :min="1"
            :precision="0"
            :controls="false"
            placeholder="用户 ID"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="使用人部门" prop="userDepartment">
          <el-input v-model="formData.userDepartment" placeholder="请输入使用人部门" :maxlength="100" :disabled="identityLocked" />
        </el-form-item>
        <el-form-item label="资产管理员" prop="adminUserId">
          <el-input-number
            v-model="formData.adminUserId"
            :disabled="identityLocked"
            :min="1"
            :precision="0"
            :controls="false"
            placeholder="用户 ID"
            class="full-width"
          />
        </el-form-item>

        <!-- 财务 -->
        <el-form-item label="购置日期" prop="purchaseDate">
          <el-date-picker
            v-model="formData.purchaseDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="identityLocked"
            placeholder="请选择购置日期"
            class="full-width"
            :disabled-date="(d: Date) => d.getTime() > Date.now()"
          />
        </el-form-item>
        <el-form-item label="购入金额" prop="amount">
          <el-input-number
            v-model="formData.amount"
            :disabled="identityLocked"
            :min="0"
            :precision="2"
            :step="100"
            placeholder="元"
            class="full-width"
          />
        </el-form-item>

        <!-- 备注 -->
        <el-form-item label="备注" prop="remark" class="span-2">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="备注信息（不超过 500 字）"
            :maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </div>
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
/* 双列表单布局（长字段备注独占一行） */
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 16px;
}

.span-2 {
  grid-column: span 2;
}

/* 编辑态权限提示条 */
.edit-perm-alert {
  margin-bottom: 16px;
}

.full-width {
  width: 100%;
}

/* el-input-number 无控件模式下的占位文字对齐 */
:deep(.el-input-number .el-input__inner) {
  text-align: left;
}
</style>
