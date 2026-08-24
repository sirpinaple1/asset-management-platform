<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { receiptApi } from '@/api/modules/receipt'
import { useUserStore } from '@/stores/user'
import type { ReceiveReceipt } from '@/api/interface/receipt'
import { RECEIPT_STATUS_META, RECEIPT_TYPE_META } from '@/api/interface/receipt'

const props = defineProps<{
  visible: boolean
  /** 单据（列表行；打开后以详情接口数据为准） */
  receipt?: ReceiveReceipt
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  /** 审批成功：回传最新单据，列表原地更新 */
  (e: 'updated', item: ReceiveReceipt): void
}>()

const userStore = useUserStore()
const detail = ref<ReceiveReceipt>()
const loading = ref(false)
const approving = ref(false)

const statusMeta = computed(() =>
  detail.value ? RECEIPT_STATUS_META[detail.value.status] : undefined,
)
const typeMeta = computed(() =>
  detail.value ? RECEIPT_TYPE_META[detail.value.type] : undefined,
)

/** 审批人与申请人不能是同一人（业务约束，后端强校验，前端预判禁用） */
const isApplicant = computed(
  () => !!detail.value && detail.value.applicantUserId === userStore.me?.userId,
)
const canApprove = computed(() => detail.value?.status === 'PENDING' && !isApplicant.value)

/* 打开时拉取详情（含明细行） */
watch(
  () => props.visible,
  async (val) => {
    if (val && props.receipt) {
      loading.value = true
      detail.value = props.receipt
      try {
        detail.value = await receiptApi.getReceiptById(props.receipt.id)
      } finally {
        loading.value = false
      }
    } else if (!val) {
      detail.value = undefined
    }
  },
)

const handleClose = () => emit('update:visible', false)

const handleApprove = async () => {
  if (!detail.value || approving.value) return
  try {
    await ElMessageBox.confirm(
      `确认批准单据 ${detail.value.serialNo} 吗？批准后所选资产将转为在用。`,
      '批准确认',
      { type: 'warning', confirmButtonText: '批准', cancelButtonText: '取消' },
    )
  } catch {
    return /* 用户取消 */
  }
  approving.value = true
  try {
    const updated = await receiptApi.approve(detail.value.id)
    ElMessage.success('已批准')
    emit('updated', updated)
    handleClose()
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
  } finally {
    approving.value = false
  }
}

const handleReject = async () => {
  if (!detail.value || approving.value) return
  let reason: string
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝申请', {
      confirmButtonText: '拒绝',
      cancelButtonText: '取消',
      inputPlaceholder: '拒绝原因（必填）',
      inputValidator: (v: string) => (v && v.trim() ? true : '请输入拒绝原因'),
    })
    reason = value.trim()
  } catch {
    return /* 用户取消 */
  }
  approving.value = true
  try {
    const updated = await receiptApi.reject(detail.value.id, reason)
    ElMessage.success('已拒绝')
    emit('updated', updated)
    handleClose()
  } catch {
    // 业务/网络错误已由 axios 拦截器统一提示
  } finally {
    approving.value = false
  }
}

/** 空值统一显示占位符 */
const dash = (v?: string | null) => (v === undefined || v === null || v === '' ? '—' : v)
</script>

<template>
  <el-drawer
    :model-value="visible"
    :title="detail ? `单据 ${detail.serialNo}` : '单据详情'"
    size="560px"
    @update:model-value="handleClose"
  >
    <div v-loading="loading">
      <template v-if="detail">
        <div class="detail-status">
          <el-tag v-if="statusMeta" :type="statusMeta.tagType" size="large" effect="light">
            {{ statusMeta.label }}
          </el-tag>
          <span class="detail-type">{{ typeMeta?.label }}单</span>
        </div>

        <el-descriptions :column="2" border size="default" class="detail-desc">
          <el-descriptions-item label="单号" :span="2">{{ detail.serialNo }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ dash(detail.applicantName || String(detail.applicantUserId)) }}</el-descriptions-item>
          <el-descriptions-item label="申请部门">{{ dash(detail.department) }}</el-descriptions-item>
          <el-descriptions-item label="领用区域">{{ dash(detail.locationName || (detail.locationId ? String(detail.locationId) : '')) }}</el-descriptions-item>
          <el-descriptions-item label="申请时间" :span="2">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="申请事由" :span="2">{{ dash(detail.reason) }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ dash(detail.approverName || (detail.approverUserId ? String(detail.approverUserId) : '')) }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ dash(detail.approveTime) }}</el-descriptions-item>
          <el-descriptions-item label="审批意见" :span="2">{{ dash(detail.approveRemark) }}</el-descriptions-item>
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
        <el-tooltip
          v-if="detail.status === 'PENDING' && isApplicant"
          content="审批人与申请人不能是同一人"
          placement="top"
        >
          <span>
            <el-button type="success" disabled>批准</el-button>
            <el-button type="danger" disabled>拒绝</el-button>
          </span>
        </el-tooltip>
        <template v-else-if="canApprove">
          <el-button type="danger" plain :loading="approving" @click="handleReject">拒绝</el-button>
          <el-button type="success" :loading="approving" @click="handleApprove">批准</el-button>
        </template>
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
