<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { transferApi } from '@/api/modules/transfer'
import { useUserStore } from '@/stores/user'
import type { TransferOrder } from '@/api/interface/transfer'
import { TRANSFER_STATUS_META, TRANSFER_SOURCE_META } from '@/api/interface/transfer'

/**
 * 调拨单详情抽屉：全字段 + 资产明细 + 流转操作。
 * PENDING 时：调入方（非发起人）可确认/拒绝（拒绝必填原因）；发起人可撤销。
 */
const props = defineProps<{
  visible: boolean
  /** 调拨单（列表行；打开后以详情接口数据为准） */
  transfer?: TransferOrder
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  /** 确认/拒绝/撤销成功：回传最新单据，列表原地更新 */
  (e: 'updated', item: TransferOrder): void
}>()

const userStore = useUserStore()
const detail = ref<TransferOrder>()
const loading = ref(false)
const acting = ref(false)

const statusMeta = computed(() =>
  detail.value ? TRANSFER_STATUS_META[detail.value.status] : undefined,
)
const sourceLabel = computed(() =>
  detail.value
    ? detail.value.sourceLabel || TRANSFER_SOURCE_META[detail.value.source].label
    : '',
)

/** 发起人本人（撤销按钮出现条件；确认/拒绝预判禁用，后端强校验） */
const isApplicant = computed(
  () => !!detail.value && detail.value.applicantUserId === userStore.me?.userId,
)
const canConfirm = computed(() => detail.value?.status === 'PENDING' && !isApplicant.value)
const canCancel = computed(() => detail.value?.status === 'PENDING' && isApplicant.value)

/* 打开时拉取详情（含明细行） */
watch(
  () => props.visible,
  async (val) => {
    if (val && props.transfer) {
      loading.value = true
      detail.value = props.transfer
      try {
        detail.value = await transferApi.getTransferById(props.transfer.id)
      } finally {
        loading.value = false
      }
    } else if (!val) {
      detail.value = undefined
    }
  },
)

const handleClose = () => emit('update:visible', false)

/** 确认收到：资产归属更新（位置/部门/负责人），写持有与操作日志 */
const handleConfirm = async () => {
  if (!detail.value || acting.value) return
  try {
    await ElMessageBox.confirm(
      `确认已收到单据 ${detail.value.serialNo} 的全部资产吗？确认后资产归属将更新为调入方。`,
      '确认收到',
      { type: 'warning', confirmButtonText: '确认收到', cancelButtonText: '取消' },
    )
  } catch {
    return /* 用户取消 */
  }
  acting.value = true
  try {
    const updated = await transferApi.confirm(detail.value.id)
    ElMessage.success('已确认，资产归属已更新')
    emit('updated', updated)
    handleClose()
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
  } finally {
    acting.value = false
  }
}

/** 拒绝接收：body={reason} 必填（后端 TransferRejectReq @NotBlank），资产不变 */
const handleReject = async () => {
  if (!detail.value || acting.value) return
  let reason: string
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝接收', {
      confirmButtonText: '拒绝',
      cancelButtonText: '取消',
      inputPlaceholder: '拒绝原因（必填）',
      inputValidator: (v: string) => (v && v.trim() ? true : '请输入拒绝原因'),
    })
    reason = value.trim()
  } catch {
    return /* 用户取消 */
  }
  acting.value = true
  try {
    const updated = await transferApi.reject(detail.value.id, reason)
    ElMessage.success('已拒绝')
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
      `确认撤销调拨单 ${detail.value.serialNo} 吗？撤销后资产保持原归属不变。`,
      '撤销调拨',
      { type: 'warning', confirmButtonText: '撤销', cancelButtonText: '取消' },
    )
  } catch {
    return /* 用户取消 */
  }
  acting.value = true
  try {
    const updated = await transferApi.cancel(detail.value.id)
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
</script>

<template>
  <el-drawer
    :model-value="visible"
    :title="detail ? `调拨单 ${detail.serialNo}` : '调拨单详情'"
    size="560px"
    @update:model-value="handleClose"
  >
    <div v-loading="loading">
      <template v-if="detail">
        <div class="detail-status">
          <el-tag v-if="statusMeta" :type="statusMeta.tagType" size="large" effect="light">
            {{ detail.statusLabel || statusMeta.label }}
          </el-tag>
          <span class="detail-type">{{ sourceLabel }}</span>
        </div>

        <el-descriptions :column="2" border size="default" class="detail-desc">
          <el-descriptions-item label="单号" :span="2">{{ detail.serialNo }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ dash(detail.applicantName || String(detail.applicantUserId)) }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ dash(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="调出位置">{{ dash(detail.fromLocationName || (detail.fromLocationId ? String(detail.fromLocationId) : '')) }}</el-descriptions-item>
          <el-descriptions-item label="调出管理员">{{ dash(detail.fromUserName || (detail.fromUserId ? String(detail.fromUserId) : '')) }}</el-descriptions-item>
          <el-descriptions-item label="调入位置">{{ dash(detail.toLocationName || (detail.toLocationId ? String(detail.toLocationId) : '')) }}</el-descriptions-item>
          <el-descriptions-item label="调入部门">{{ dash(detail.toDepartment) }}</el-descriptions-item>
          <el-descriptions-item label="调入负责人">{{ dash(detail.toUserName || (detail.toUserId ? String(detail.toUserId) : '')) }}</el-descriptions-item>
          <el-descriptions-item label="调拨原因" :span="2">{{ dash(detail.reason) }}</el-descriptions-item>
          <el-descriptions-item label="确认人">{{ dash(detail.confirmerName || (detail.confirmerUserId ? String(detail.confirmerUserId) : '')) }}</el-descriptions-item>
          <el-descriptions-item label="处理时间">{{ dash(detail.confirmTime) }}</el-descriptions-item>
          <el-descriptions-item label="拒绝原因" :span="2">{{ dash(detail.rejectReason) }}</el-descriptions-item>
        </el-descriptions>

        <div class="items-title">资产明细（{{ detail.items?.length ?? 0 }} 台）</div>
        <el-table :data="detail.items || []" row-key="id" border size="small" empty-text="无明细数据">
          <el-table-column prop="assetBarcode" label="资产编码" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ dash(row.assetBarcode || String(row.assetId)) }}</template>
          </el-table-column>
          <el-table-column prop="assetName" label="资产名称" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ dash(row.assetName) }}</template>
          </el-table-column>
          <el-table-column prop="assetSn" label="序列号" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ dash(row.assetSn) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </div>

    <template #footer>
      <template v-if="detail">
        <!-- 待确认：调入方（非发起人）可确认/拒绝 -->
        <template v-if="canConfirm">
          <el-button type="danger" plain :loading="acting" @click="handleReject">拒绝接收</el-button>
          <el-button type="success" :loading="acting" @click="handleConfirm">确认收到</el-button>
        </template>
        <!-- 待确认：发起人可撤销（确认/拒绝对发起人禁用） -->
        <el-tooltip
          v-else-if="detail.status === 'PENDING' && isApplicant"
          content="确认/拒绝由调入方操作；发起人可撤销"
          placement="top"
        >
          <span>
            <el-button type="success" disabled>确认收到</el-button>
            <el-button type="danger" plain disabled>拒绝接收</el-button>
          </span>
        </el-tooltip>
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
  gap: 10px;
  margin-bottom: 16px;
}

.detail-type {
  font-size: 13px;
  color: #86909c;
}

.detail-desc {
  margin-bottom: 20px;
}

.items-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  margin-bottom: 10px;
}
</style>
