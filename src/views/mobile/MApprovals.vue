<template>
  <div class="m-page">
    <!-- 顶部 segmented：待我处理 / 我发起的 -->
    <div class="m-seg">
      <button
        v-for="seg in SEGS"
        :key="seg.key"
        class="m-seg-btn"
        :class="{ active: tab === seg.key }"
        type="button"
        @click="tab = seg.key"
      >
        {{ seg.label }}
        <span v-if="seg.key === 'todo' && todoList.length" class="m-seg-count">{{
          todoList.length
        }}</span>
      </button>
    </div>

    <!-- 列表 -->
    <div v-if="approvalStore.loading && !approvalStore.items.length" class="m-empty">加载中…</div>
    <template v-else>
      <div v-if="curList.length === 0" class="m-empty">
        {{ tab === 'todo' ? '没有待处理的单据' : '没有发起过的单据' }}
      </div>
      <div
        v-for="item in curList"
        :key="`${item.bizType}-${item.bizId}`"
        class="m-card"
        @click="detail = item"
      >
        <div class="m-card-head">
          <span class="m-card-tag" :data-type="item.bizType">{{ bizLabel(item.bizType) }}</span>
          <span class="m-card-serial">{{ item.serialNo }}</span>
          <span class="m-card-status" :class="statusClass(item)">{{ statusLabel(item) }}</span>
        </div>
        <div class="m-card-summary">{{ item.summary }}</div>
        <div class="m-card-foot">
          <span>{{ item.applicantName || `用户${item.applicantUserId}` }}</span>
          <span>{{ fmtTime(item.createdAt) }}</span>
        </div>
      </div>
    </template>

    <!-- 详情弹层（全屏） -->
    <div v-if="detail" class="m-detail">
      <div class="m-detail-head">
        <span class="m-card-serial">{{ detail.serialNo }}</span>
        <button class="m-detail-close" type="button" @click="detail = undefined">✕</button>
      </div>
      <div class="m-detail-body">
        <div class="m-row"><span>类型</span><b>{{ bizLabel(detail.bizType) }}</b></div>
        <div class="m-row"><span>状态</span><b>{{ statusLabel(detail) }}</b></div>
        <div class="m-row">
          <span>发起人</span><b>{{ detail.applicantName || `用户${detail.applicantUserId}` }}</b>
        </div>
        <div class="m-row"><span>发起时间</span><b>{{ fmtTime(detail.createdAt) }}</b></div>
        <div v-if="detail.assigneeUserName" class="m-row">
          <span>指定处理人</span><b>{{ detail.assigneeUserName }}</b>
        </div>
        <div class="m-row"><span>摘要</span><b>{{ detail.summary }}</b></div>
        <template v-if="receiptFields">
          <div v-if="receiptFields.department" class="m-row">
            <span>部门</span><b>{{ receiptFields.department }}</b>
          </div>
          <div v-if="receiptFields.locationName" class="m-row">
            <span>去向区域</span><b>{{ receiptFields.locationName }}</b>
          </div>
          <div v-if="receiptFields.reason" class="m-row">
            <span>事由</span><b>{{ receiptFields.reason }}</b>
          </div>
          <div v-if="receiptFields.approveRemark" class="m-row">
            <span>审批意见</span><b>{{ receiptFields.approveRemark }}</b>
          </div>
        </template>
        <template v-if="transferFields">
          <div v-if="transferFields.toLocationName" class="m-row">
            <span>调入位置</span><b>{{ transferFields.toLocationName }}</b>
          </div>
          <div v-if="transferFields.toDepartment" class="m-row">
            <span>调入部门</span><b>{{ transferFields.toDepartment }}</b>
          </div>
          <div v-if="transferFields.reason" class="m-row">
            <span>事由</span><b>{{ transferFields.reason }}</b>
          </div>
        </template>

        <!-- 明细行 -->
        <div v-if="detailItems.length" class="m-detail-items">
          <div class="m-detail-items-title">资产明细（{{ detailItems.length }}）</div>
          <div v-for="it in detailItems" :key="it.id" class="m-item-row">
            <div class="m-item-name">{{ it.assetName || `资产#${it.assetId}` }}</div>
            <div class="m-item-sub">{{ it.assetBarcode }}{{ it.assetSn ? ` · SN ${it.assetSn}` : '' }}</div>
          </div>
        </div>
      </div>

      <!-- 操作：仅"待我处理"且 PENDING -->
      <div v-if="tab === 'todo' && detail.status === 'PENDING'" class="m-detail-actions">
        <button
          v-if="detail.bizType !== 'CHANGE'"
          class="m-btn danger"
          type="button"
          :disabled="acting"
          @click="onReject"
        >
          拒绝
        </button>
        <button class="m-btn primary" type="button" :disabled="acting" @click="onApprove">
          {{ approveText(detail.bizType) }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useApprovalStore } from '@/stores/approval'
import { useUserStore } from '@/stores/user'
import { receiptApi } from '@/api/modules/receipt'
import { transferApi } from '@/api/modules/transfer'
import { changeApi } from '@/api/modules/change'
import {
  APPROVAL_BIZ_META,
  approvalStatusTag,
  isMine,
  isTodoFor,
} from '@/api/interface/approval'
import type { ApprovalBizType, ApprovalItem } from '@/api/interface/approval'
import type { ReceiveReceipt, ReceiptItem } from '@/api/interface/receipt'
import type { TransferOrder } from '@/api/interface/transfer'

const approvalStore = useApprovalStore()
const userStore = useUserStore()

const SEGS = [
  { key: 'todo', label: '待我处理' },
  { key: 'mine', label: '我发起的' },
] as const
const tab = ref<'todo' | 'mine'>('todo')

/** 详情弹层当前单据 */
const detail = ref<ApprovalItem>()
/** 操作防重复提交 */
const acting = ref(false)

const meUserId = () => userStore.me?.userId
const todoList = computed(() => approvalStore.items.filter((it) => isTodoFor(it, meUserId())))
const mineList = computed(() => approvalStore.items.filter((it) => isMine(it, meUserId())))
const curList = computed(() => (tab.value === 'todo' ? todoList.value : mineList.value))

/** 单据明细（领用/借用/调拨均有 items） */
const detailItems = computed<ReceiptItem[]>(() => {
  if (!detail.value) return []
  const raw = detail.value.raw as ReceiveReceipt & { items?: ReceiptItem[] }
  return raw.items || []
})

/** 领用/借用单额外字段 */
const receiptFields = computed((): ReceiveReceipt | undefined => {
  if (!detail.value) return undefined
  if (detail.value.bizType !== 'RECEIVE' && detail.value.bizType !== 'BORROW') return undefined
  return detail.value.raw as ReceiveReceipt
})

/** 调拨单额外字段 */
const transferFields = computed((): TransferOrder | undefined => {
  if (detail.value?.bizType !== 'TRANSFER') return undefined
  return detail.value.raw as TransferOrder
})

const bizLabel = (t: ApprovalBizType) => APPROVAL_BIZ_META[t].label
const statusLabel = (item: ApprovalItem) => approvalStatusTag(item).label
const statusClass = (item: ApprovalItem) => {
  const { tagType } = approvalStatusTag(item)
  return { success: 'ok', warning: 'pending', danger: 'bad', info: 'info' }[tagType]
}
const approveText = (t: ApprovalBizType) =>
  t === 'TRANSFER' ? '确认' : t === 'CHANGE' ? '执行' : '通过'

const fmtTime = (s: string) => {
  const d = new Date(s)
  return Number.isNaN(d.getTime())
    ? s
    : d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

/* ---------------- 审批操作 ---------------- */

const onApprove = async () => {
  if (!detail.value || acting.value) return
  acting.value = true
  try {
    const { bizType, bizId } = detail.value
    if (bizType === 'RECEIVE' || bizType === 'BORROW') await receiptApi.approve(bizId)
    else if (bizType === 'TRANSFER') await transferApi.confirm(bizId)
    else await changeApi.confirm(bizId)
    ElMessage.success('处理成功')
    detail.value = undefined
    await approvalStore.refresh()
  } catch {
    /* axios 拦截器已统一提示 */
  } finally {
    acting.value = false
  }
}

const onReject = async () => {
  if (!detail.value || acting.value) return
  const target = detail.value
  let reason = ''
  const res = window.prompt(`拒绝单据「${target.serialNo}」，请填写拒绝原因：`)
  if (res === null) return
  reason = res.trim()
  if (!reason) {
    ElMessage.warning('请填写拒绝原因')
    return
  }
  acting.value = true
  try {
    if (target.bizType === 'TRANSFER') await transferApi.reject(target.bizId, reason)
    else await receiptApi.reject(target.bizId, reason)
    ElMessage.success('已拒绝')
    detail.value = undefined
    await approvalStore.refresh()
  } catch {
    /* 拦截器已提示 */
  } finally {
    acting.value = false
  }
}

onMounted(() => {
  void approvalStore.refresh()
})
</script>

<style scoped>
.m-page {
  padding: 12px 12px 20px;
}

/* segmented 切换 */
.m-seg {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.m-seg-btn {
  flex: 1;
  height: 36px;
  border: 1px solid var(--el-border-color);
  border-radius: 18px;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font-size: 14px;
  transition: background-color 0.2s, color 0.2s;
}

.m-seg-btn.active {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
  font-weight: 600;
}

.m-seg-count {
  display: inline-block;
  min-width: 18px;
  margin-left: 4px;
  padding: 0 5px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.25);
  font-size: 12px;
}

.m-seg-btn:not(.active) .m-seg-count {
  background: var(--el-color-danger);
  color: #fff;
}

/* 列表卡片 */
.m-card {
  background: var(--el-bg-color);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 10px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: transform 0.1s;
}

.m-card:active {
  transform: scale(0.985);
}

.m-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.m-card-tag {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #fff;
  background: var(--el-color-info);
}

.m-card-tag[data-type='RECEIVE'] { background: var(--el-color-primary); }
.m-card-tag[data-type='BORROW'] { background: #9a67ea; }
.m-card-tag[data-type='TRANSFER'] { background: #e6a23c; }
.m-card-tag[data-type='CHANGE'] { background: #409eff; }

.m-card-serial {
  flex: 1;
  font-size: 13px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-card-status {
  flex-shrink: 0;
  font-size: 12px;
}

.m-card-status.pending { color: var(--el-color-warning); }
.m-card-status.ok { color: var(--el-color-success); }
.m-card-status.bad { color: var(--el-color-danger); }
.m-card-status.info { color: var(--el-text-color-secondary); }

.m-card-summary {
  margin-top: 8px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.m-card-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.m-empty {
  text-align: center;
  padding: 48px 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

/* 详情弹层（全屏覆盖，动画上滑） */
.m-detail {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color-page);
  animation: m-slide-up 0.22s ease-out;
}

@keyframes m-slide-up {
  from { transform: translateY(24px); opacity: 0.6; }
  to { transform: translateY(0); opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .m-detail { animation: none; }
}

.m-detail-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 46px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.m-detail-close {
  border: none;
  background: none;
  font-size: 18px;
  color: var(--el-text-color-secondary);
  padding: 8px 4px 8px 12px;
}

.m-detail-body {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 12px;
}

.m-row {
  display: flex;
  gap: 12px;
  padding: 10px 14px;
  background: var(--el-bg-color);
  border-radius: 8px;
  margin-bottom: 8px;
  font-size: 14px;
}

.m-row span {
  flex-shrink: 0;
  width: 76px;
  color: var(--el-text-color-secondary);
}

.m-row b {
  flex: 1;
  font-weight: 400;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.m-detail-items {
  margin-top: 4px;
}

.m-detail-items-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 8px 2px;
}

.m-item-row {
  background: var(--el-bg-color);
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 6px;
}

.m-item-name {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.m-item-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 底部操作条 */
.m-detail-actions {
  flex-shrink: 0;
  display: flex;
  gap: 12px;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-light);
}

.m-btn {
  flex: 1;
  height: 44px;
  border-radius: 22px;
  border: none;
  font-size: 16px;
  font-weight: 600;
}

.m-btn.primary {
  background: var(--el-color-primary);
  color: #fff;
}

.m-btn.danger {
  background: var(--el-bg-color);
  border: 1px solid var(--el-color-danger);
  color: var(--el-color-danger);
}

.m-btn:disabled {
  opacity: 0.5;
}
</style>
