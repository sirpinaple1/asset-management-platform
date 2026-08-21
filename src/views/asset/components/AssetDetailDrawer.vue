<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assetApi } from '@/api/modules/asset'
import type { Asset, AssetLog, AssetStatus } from '@/api/interface/asset'
import { ASSET_STATUS_META, DISCARDABLE_STATUSES } from '@/api/interface/asset'

const props = defineProps<{
  visible: boolean
  asset?: Asset
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'edit', asset: Asset): void
  (e: 'discarded'): void
}>()

/** 内部展示副本：报废后本地刷新状态与日志，不依赖父组件重传 */
const current = ref<Asset>()
const logs = ref<AssetLog[]>([])
const logsLoading = ref(false)
const discarding = ref(false)

watch(
  () => [props.visible, props.asset?.id] as const,
  ([visible]) => {
    if (!visible) return
    current.value = props.asset
    if (props.asset) void loadLogs(props.asset.id)
  },
  { immediate: true },
)

const loadLogs = async (assetId: number) => {
  logsLoading.value = true
  try {
    logs.value = await assetApi.getAssetLogs(assetId)
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    logsLoading.value = false
  }
}

const handleClose = () => emit('update:visible', false)

const handleEdit = () => {
  if (current.value) emit('edit', current.value)
}

const canDiscard = computed(
  () => !!current.value && DISCARDABLE_STATUSES.includes(current.value.status),
)

const handleDiscard = async () => {
  if (!current.value || discarding.value) return
  const row = current.value
  try {
    const { value } = await ElMessageBox.prompt(
      `资产 ${row.barcode}（${row.name}）报废后不可恢复，确认报废吗？`,
      '报废确认',
      {
        type: 'warning',
        confirmButtonText: '报废',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '报废原因（选填，不超过 500 字）',
        inputValidator: (v: string) => !v || v.length <= 500 || '报废原因不能超过 500 字',
      },
    )
    discarding.value = true
    await assetApi.discardAsset(row.id, value.trim() || undefined)
    ElMessage.success('已报废')
    /* 本地刷新详情与日志；父组件收到 discarded 后刷新列表 */
    current.value = await assetApi.getAssetById(row.id)
    void loadLogs(row.id)
    emit('discarded')
  } catch {
    /* 用户取消或错误已由拦截器提示 */
  } finally {
    discarding.value = false
  }
}

/* ---------------- 展示工具 ---------------- */
const dash = (v: unknown) =>
  v === undefined || v === null || v === '' ? '—' : String(v)

const formatAmount = (v?: number) =>
  v === undefined || v === null
    ? '—'
    : `¥ ${Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/** 使用人/管理员：M08 用户体系打通前仅展示 ID */
const userText = (id?: number, label?: string) => label || (id ? `#${id}` : '—')

/** 日志节点颜色（按操作类型；未知类型回退 info） */
const LOG_TYPE_COLOR: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  新增: 'primary',
  领用: 'success',
  归还: 'warning',
  调拨: 'warning',
  实物信息变更: 'info',
  盘点处理: 'info',
  报废: 'danger',
}
const logColor = (t: string) => LOG_TYPE_COLOR[t] ?? 'info'
</script>

<template>
  <el-drawer
    :model-value="visible"
    size="560px"
    :close-on-click-modal="false"
    @update:model-value="handleClose"
  >
    <template #header>
      <div class="drawer-header">
        <div class="drawer-title">
          <span class="asset-name">{{ current?.name }}</span>
          <el-tag
            v-if="current"
            :type="ASSET_STATUS_META[current.status as AssetStatus].tagType"
            effect="light"
          >
            {{ ASSET_STATUS_META[current.status as AssetStatus].label }}
          </el-tag>
        </div>
        <span class="asset-barcode">{{ current?.barcode }}</span>
      </div>
    </template>

    <template v-if="current">
      <!-- 全字段详情 -->
      <el-descriptions :column="2" border size="small" class="desc">
        <el-descriptions-item label="序列号">{{ dash(current.sn) }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ dash(current.categoryName) }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ dash(current.modelName) }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ dash(current.supplierName) }}</el-descriptions-item>
        <el-descriptions-item label="当前位置">{{ dash(current.locationName) }}</el-descriptions-item>
        <el-descriptions-item label="应归放位置">{{ dash(current.homeLocationName) }}</el-descriptions-item>
        <el-descriptions-item label="位置明细">{{ dash(current.locationDetail) }}</el-descriptions-item>
        <el-descriptions-item label="归属公司">{{ dash(current.companyName) }}</el-descriptions-item>
        <el-descriptions-item label="使用人">{{ userText(current.userId) }}</el-descriptions-item>
        <el-descriptions-item label="使用人部门">{{ dash(current.userDepartment) }}</el-descriptions-item>
        <el-descriptions-item label="资产管理员">{{ userText(current.adminUserId) }}</el-descriptions-item>
        <el-descriptions-item label="购置日期">{{ dash(current.purchaseDate) }}</el-descriptions-item>
        <el-descriptions-item label="购入金额">{{ formatAmount(current.amount) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ dash(current.remark) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ current.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ current.updatedAt }}</el-descriptions-item>
      </el-descriptions>

      <!-- 操作日志时间线 -->
      <h3 class="section-title">操作日志</h3>
      <div v-loading="logsLoading" class="logs">
        <el-timeline v-if="logs.length">
          <el-timeline-item
            v-for="log in logs"
            :key="log.id"
            :type="logColor(log.operationType)"
            :timestamp="log.createdAt"
          >
            <div class="log-type">{{ log.operationType }}</div>
            <div class="log-content">{{ log.content }}</div>
            <div class="log-operator">操作人：{{ userText(log.operatorUserId, log.operatorLabel) }}</div>
          </el-timeline-item>
        </el-timeline>
        <div v-else-if="!logsLoading" class="logs-empty">暂无操作记录</div>
      </div>
    </template>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" plain :disabled="!current" @click="handleEdit">编辑</el-button>
      <el-button
        v-if="canDiscard"
        type="danger"
        :loading="discarding"
        @click="handleDiscard"
      >
        报废
      </el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.drawer-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.asset-name {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.asset-barcode {
  font-size: 13px;
  color: #86909c;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.desc {
  margin-top: 4px;
}

.section-title {
  margin: 20px 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.logs {
  min-height: 80px;
}

.logs-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #86909c;
}

.log-type {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.log-content {
  margin-top: 2px;
  font-size: 13px;
  color: #4b5563;
  line-height: 1.6;
}

.log-operator {
  margin-top: 2px;
  font-size: 12px;
  color: #9ca3af;
}
</style>
