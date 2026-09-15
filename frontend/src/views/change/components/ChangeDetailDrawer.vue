<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changeApi } from '@/api/modules/change'
import { useUserStore } from '@/stores/user'
import type { ChangeOrder } from '@/api/interface/change'
import { CHANGE_STATUS_META, CHANGE_FIELD_META } from '@/api/interface/change'

/**
 * 实物信息变更单详情抽屉：单据信息 + 变更后信息（主表 new_* 统一目标值）
 * + 变更前/后对比明细 + 流转操作。
 * 权限（对齐后端 M06）：变更单为信息修正单据，确认执行允许发起人自己操作（区别于 M04/M05 审批流）；
 * 撤销仅 PENDING 且仅发起人可撤（后端 403 校验）。
 */
const props = defineProps<{
  visible: boolean
  /** 变更单（列表行；打开后以详情接口数据为准） */
  changeOrder?: ChangeOrder
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  /** 确认执行/撤销成功：回传最新单据，列表原地更新 */
  (e: 'updated', item: ChangeOrder): void
}>()

const userStore = useUserStore()
const detail = ref<ChangeOrder>()
const loading = ref(false)
const acting = ref(false)

const statusMeta = computed(() =>
  detail.value ? CHANGE_STATUS_META[detail.value.status] : undefined,
)

/** 发起人本人（仅撤销按钮出现条件；确认执行允许发起人自审） */
const isApplicant = computed(
  () => !!detail.value && detail.value.applicantUserId === userStore.me?.userId,
)
const canConfirm = computed(() => detail.value?.status === 'PENDING')
const canCancel = computed(() => detail.value?.status === 'PENDING' && isApplicant.value)

/* 打开时拉取详情（含变更前/后对比明细） */
watch(
  () => props.visible,
  async (val) => {
    if (val && props.changeOrder) {
      loading.value = true
      detail.value = props.changeOrder
      try {
        detail.value = await changeApi.getChangeOrderById(props.changeOrder.id)
      } finally {
        loading.value = false
      }
    } else if (!val) {
      detail.value = undefined
    }
  },
)

const handleClose = () => emit('update:visible', false)

/** 确认执行：批量更新资产实物字段（同一事务写持有关系与 asset_log），不可逆 */
const handleConfirm = async () => {
  if (!detail.value || acting.value) return
  const count = detail.value.items?.length ?? 0
  try {
    await ElMessageBox.confirm(
      `确认执行变更单 ${detail.value.serialNo} 吗？共 ${count} 项变更将写入资产实物信息。`,
      '确认执行',
      { type: 'warning', confirmButtonText: '确认执行', cancelButtonText: '取消' },
    )
  } catch {
    return /* 用户取消 */
  }
  acting.value = true
  try {
    const updated = await changeApi.confirm(detail.value.id)
    ElMessage.success('已执行，资产实物信息已更新')
    emit('updated', updated)
    handleClose()
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
  } finally {
    acting.value = false
  }
}

/** 撤销：仅 PENDING、仅发起人可撤（后端 403 校验），资产不变 */
const handleCancel = async () => {
  if (!detail.value || acting.value) return
  try {
    await ElMessageBox.confirm(
      `确认撤销变更单 ${detail.value.serialNo} 吗？撤销后资产实物信息保持不变。`,
      '撤销变更',
      { type: 'warning', confirmButtonText: '撤销', cancelButtonText: '取消' },
    )
  } catch {
    return /* 用户取消 */
  }
  acting.value = true
  try {
    const updated = await changeApi.cancel(detail.value.id)
    ElMessage.success('已撤销')
    emit('updated', updated)
    handleClose()
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
  } finally {
    acting.value = false
  }
}

/** 空值统一显示占位符 */
const dash = (v?: string | number | null) =>
  v === undefined || v === null || v === '' ? '—' : String(v)

/** 变更字段展示名：优先后端 fieldLabel，回退本地 meta，再回退原值 */
const fieldLabel = (row: { fieldName: string; fieldLabel?: string }) =>
  row.fieldLabel || CHANGE_FIELD_META[row.fieldName]?.label || row.fieldName

/** 涉及资产数（明细按"资产×字段"展开，资产数需去重） */
const assetCount = computed(() =>
  detail.value?.items ? new Set(detail.value.items.map((it) => it.assetId)).size : 0,
)
</script>

<template>
  <el-drawer
    :model-value="visible"
    :title="detail ? `变更单 ${detail.serialNo}` : '变更单详情'"
    size="640px"
    @update:model-value="handleClose"
  >
    <div v-loading="loading">
      <template v-if="detail">
        <div class="detail-status">
          <el-tag v-if="statusMeta" :type="statusMeta.tagType" size="large" effect="light">
            {{ detail.statusLabel || statusMeta.label }}
          </el-tag>
          <span class="detail-type">实物信息变更（AOC）</span>
        </div>

        <el-descriptions :column="2" border size="default" class="detail-desc">
          <el-descriptions-item label="单号" :span="2">{{ detail.serialNo }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ dash(detail.applicantName || String(detail.applicantUserId)) }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ dash(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="变更原因" :span="2">{{ dash(detail.reason) }}</el-descriptions-item>
          <el-descriptions-item label="执行人">{{ dash(detail.confirmerName || (detail.confirmerUserId ? String(detail.confirmerUserId) : '')) }}</el-descriptions-item>
          <el-descriptions-item label="执行时间">{{ dash(detail.confirmTime) }}</el-descriptions-item>
        </el-descriptions>

        <div class="items-title">变更后信息（统一目标值）</div>
        <el-descriptions :column="2" border size="default" class="detail-desc">
          <el-descriptions-item label="使用人">{{ dash(detail.newUserName || (detail.newUserId ? String(detail.newUserId) : '')) }}</el-descriptions-item>
          <el-descriptions-item label="使用部门">{{ dash(detail.newUserDepartment) }}</el-descriptions-item>
          <el-descriptions-item label="区域">{{ dash(detail.newLocationName || (detail.newLocationId ? String(detail.newLocationId) : '')) }}</el-descriptions-item>
          <el-descriptions-item label="存放位置明细">{{ dash(detail.newLocationDetail) }}</el-descriptions-item>
          <el-descriptions-item label="归属公司" :span="2">{{ dash(detail.newCompanyName || (detail.newCompanyId ? String(detail.newCompanyId) : '')) }}</el-descriptions-item>
        </el-descriptions>

        <div class="items-title">变更明细（{{ assetCount }} 台资产 × {{ detail.items?.length ?? 0 }} 项变更）</div>
        <el-table :data="detail.items || []" row-key="id" border size="small" empty-text="无明细数据">
          <el-table-column prop="assetBarcode" label="资产编码" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ dash(row.assetBarcode || String(row.assetId)) }}</template>
          </el-table-column>
          <el-table-column prop="assetName" label="资产名称" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ dash(row.assetName) }}</template>
          </el-table-column>
          <el-table-column label="变更字段" width="104">
            <template #default="{ row }">{{ fieldLabel(row) }}</template>
          </el-table-column>
          <el-table-column prop="valueBefore" label="变更前" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ dash(row.valueBefore) }}</template>
          </el-table-column>
          <el-table-column prop="valueAfter" label="变更后" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="value-after">{{ dash(row.valueAfter) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>

    <template #footer>
      <template v-if="detail">
        <!-- 待确认：任何资产管理员可确认执行（信息修正单据，发起人可自审） -->
        <el-button v-if="canConfirm" type="success" :loading="acting" @click="handleConfirm">确认执行</el-button>
        <el-button v-if="canCancel" :loading="acting" @click="handleCancel">撤销</el-button>
        <el-button @click="handleClose">关闭</el-button>
      </template>
    </template>
  </el-drawer>
</template>

<style scoped>
.detail-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.detail-type {
  font-size: var(--text-sm);
  color: var(--color-text-3);
}

.detail-desc {
  margin-bottom: 20px;
}

.items-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text-1);
  margin-bottom: 8px;
}

/* 变更后值高亮（对比"变更前"一眼识别新值） */
.value-after {
  color: var(--color-primary);
  font-weight: 600;
}
</style>
