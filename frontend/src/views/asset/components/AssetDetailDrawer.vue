<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assetApi } from '@/api/modules/asset'
import { receiptApi } from '@/api/modules/receipt'
import type { Allocation } from '@/api/interface/receipt'
import type { Asset, AssetLog, AssetStatus } from '@/api/interface/asset'
import { ASSET_STATUS_META, DISCARDABLE_STATUSES } from '@/api/interface/asset'
import { useUserStore } from '@/stores/user'

const props = defineProps<{
  visible: boolean
  asset?: Asset
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'edit', asset: Asset): void
  (e: 'discarded'): void
  (e: 'returned'): void
}>()

const userStore = useUserStore()

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
    if (props.asset) void loadMyAllocation(props.asset.id)
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

/* ---------------- 退库 / 归还（仅本人持有的领用/借用资产） ---------------- */
/** 当前登录用户在该资产上的活跃持有关系（领用/借用；调拨产生的 TRANSFER 不允许从详情退） */
const myAllocation = ref<Allocation | null>(null)
const returning = ref(false)

const loadMyAllocation = async (assetId: number) => {
  myAllocation.value = null
  try {
    const list = await receiptApi.getAllocations({
      assetId,
      userId: userStore.me?.userId,
      active: true,
    })
    myAllocation.value = list.find(
      (a) => a.active && (a.type === 'RECEIVE' || a.type === 'BORROW'),
    ) ?? null
  } catch {
    /* 查询失败不阻塞详情展示，按钮不出现 */
  }
}

/** 可退：当前用户本人持有的领用（退库）或借用（归还）资产 */
const canReturn = computed(() => !!myAllocation.value && !returning.value)
const returnLabel = computed(() =>
  myAllocation.value?.type === 'BORROW' ? '归还' : '退库',
)

const handleReturn = async () => {
  const alloc = myAllocation.value
  if (!alloc || returning.value) return
  const asset = current.value
  try {
    const { value } = await ElMessageBox.prompt(
      `确认对资产「${alloc.assetBarcode || ''} ${alloc.assetName || asset?.name || ''}」执行${returnLabel.value}吗？${returnLabel.value}后资产回闲置。`,
      `${returnLabel.value}确认`,
      {
        confirmButtonText: returnLabel.value,
        cancelButtonText: '取消',
        inputPlaceholder: '备注（选填）',
        inputValidator: () => true,
      },
    )
    returning.value = true
    await receiptApi.returnAllocation(alloc.id, value.trim() || undefined)
    ElMessage.success(`${returnLabel.value}成功`)
    /* 本地刷新详情与持有关系；父组件收到 returned 后刷新列表 */
    if (asset) {
      current.value = await assetApi.getAssetById(asset.id)
      void loadLogs(asset.id)
      void loadMyAllocation(asset.id)
    }
    emit('returned')
  } catch {
    /* 用户取消或错误已由拦截器提示 */
  } finally {
    returning.value = false
  }
}

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

/** 使用人/管理员/操作人：优先后端实时反查的姓名（userName/adminUserName/operatorLabel），未命中兜底 #id */
const userText = (id?: number, label?: string) =>
  label || (id ? `#${id}` : '—')

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
        <el-descriptions-item label="细则">{{ dash(current.spec) }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ dash(current.categoryName) }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ dash(current.modelName) }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ dash(current.supplierName) }}</el-descriptions-item>
        <el-descriptions-item label="当前位置">{{ dash(current.locationName) }}</el-descriptions-item>
        <el-descriptions-item label="应归放位置">{{ dash(current.homeLocationName) }}</el-descriptions-item>
        <el-descriptions-item label="位置明细">{{ dash(current.locationDetail) }}</el-descriptions-item>
        <el-descriptions-item label="归属公司">{{ dash(current.companyName) }}</el-descriptions-item>
        <el-descriptions-item label="使用人">{{ userText(current.userId, current.userName) }}</el-descriptions-item>
        <el-descriptions-item label="使用人部门">{{ dash(current.userDepartment) }}</el-descriptions-item>
        <el-descriptions-item label="资产管理员">{{ userText(current.adminUserId, current.adminUserName) }}</el-descriptions-item>
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
        v-if="canReturn"
        type="warning"
        :loading="returning"
        @click="handleReturn"
      >
        {{ returnLabel }}
      </el-button>
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
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text-1);
}

.asset-barcode {
  font-size: var(--text-sm);
  color: var(--color-text-3);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.desc {
  margin-top: 4px;
}

.section-title {
  margin: 20px 0 12px;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text-1);
}

.logs {
  min-height: 80px;
}

.logs-empty {
  padding: 24px 0;
  text-align: center;
  font-size: var(--text-sm);
  color: var(--color-text-3);
}

.log-type {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-1);
}

.log-content {
  margin-top: 2px;
  font-size: var(--text-sm);
  color: var(--color-text-2);
  line-height: 1.6;
}

.log-operator {
  margin-top: 2px;
  font-size: var(--text-xs);
  color: var(--color-text-3);
}
</style>
