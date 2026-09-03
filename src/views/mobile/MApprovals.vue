<template>
  <div class="m-page">
    <!-- 顶部分段器：待我处理 / 我发起的（滑动指示器） -->
    <div class="m-seg" :data-active="tab === 'mine' ? '1' : '0'">
      <span class="m-seg-indicator" aria-hidden="true" />
      <button
        v-for="seg in SEGS"
        :key="seg.key"
        class="m-seg-btn"
        :class="{ active: tab === seg.key }"
        type="button"
        @click="setTab(seg.key)"
      >
        {{ seg.label }}
        <span v-if="seg.key === 'todo' && todoList.length" class="m-seg-count">{{
          todoList.length
        }}</span>
      </button>
    </div>

    <!-- 列表：骨架屏 → 空态/卡片 -->
    <div v-if="approvalStore.loading && !approvalStore.items.length" class="skeleton-wrap">
      <div v-for="n in 4" :key="n" class="m-skeleton-card" />
    </div>
    <template v-else>
      <div v-if="curList.length === 0" class="m-empty">
        {{ tab === 'todo' ? '没有待处理的单据' : '没有发起过的单据' }}
      </div>
      <div
        v-for="(item, i) in curList"
        :key="`${item.bizType}-${item.bizId}`"
        class="m-card"
        :style="{ '--i': Math.min(i, 8) }"
        @click="openDetail(item)"
      >
        <div class="m-card-head">
          <span class="m-card-tag" :data-type="item.bizType">{{ bizLabel(item.bizType) }}</span>
          <span class="m-card-title">{{ item.serialNo }}</span>
          <span class="m-status" :class="statusClass(item)">{{ statusLabel(item) }}</span>
        </div>
        <div class="m-card-sub summary">{{ item.summary }}</div>
        <div class="m-card-foot">
          <span>{{ item.applicantName || `用户${item.applicantUserId}` }}</span>
          <span>{{ fmtTime(item.createdAt) }}</span>
        </div>
      </div>
    </template>

    <!-- 详情弹层（全屏，iOS 曲线上滑/下滑） -->
    <div v-if="detail" class="m-sheet" :class="detailClosing ? 'out' : 'in'">
      <div class="m-sheet-head">
        <span class="m-sheet-head-title">{{ detail.serialNo }}</span>
        <button class="m-sheet-close" type="button" aria-label="关闭" @click="closeDetail">
          <svg width="16" height="16" viewBox="0 0 20 20" fill="none">
            <line x1="5" y1="5" x2="15" y2="15" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <line x1="15" y1="5" x2="5" y2="15" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </button>
      </div>
      <div class="m-sheet-body">
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
        <div v-if="detailItems.length" class="detail-items">
          <div class="detail-items-title">资产明细（{{ detailItems.length }}）</div>
          <div v-for="it in detailItems" :key="it.id" class="m-card item-card">
            <div class="item-name">{{ it.assetName || `资产#${it.assetId}` }}</div>
            <div class="m-card-sub">{{ it.assetBarcode }}{{ it.assetSn ? ` · SN ${it.assetSn}` : '' }}</div>
          </div>
        </div>
      </div>

      <!-- 操作：仅"待我处理"且 PENDING -->
      <div v-if="tab === 'todo' && detail.status === 'PENDING'" class="m-actions">
        <button
          v-if="detail.bizType !== 'CHANGE'"
          class="m-btn danger-ghost"
          type="button"
          :disabled="acting"
          @click="rejectSheet = true"
        >
          拒绝
        </button>
        <button class="m-btn primary" type="button" :disabled="acting" @click="onApprove">
          {{ acting ? '处理中…' : approveText(detail.bizType) }}
        </button>
      </div>
    </div>

    <!-- 拒绝原因弹层（底部半屏） -->
    <div v-if="rejectSheet" class="reject-sheet" @click.self="rejectSheet = false">
      <div class="reject-panel">
        <div class="reject-head">
          <span class="m-sheet-head-title">拒绝单据</span>
          <button class="m-sheet-close" type="button" aria-label="关闭" @click="rejectSheet = false">
            <svg width="16" height="16" viewBox="0 0 20 20" fill="none">
              <line x1="5" y1="5" x2="15" y2="15" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              <line x1="15" y1="5" x2="5" y2="15" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
          </button>
        </div>
        <div class="m-field reject-field">
          <label class="m-field-label required">拒绝原因</label>
          <textarea
            v-model="rejectReason"
            class="m-textarea"
            maxlength="200"
            placeholder="请填写拒绝原因，将通知发起人"
          ></textarea>
        </div>
        <div class="reject-actions">
          <button class="m-btn ghost" type="button" @click="rejectSheet = false">取消</button>
          <button class="m-btn danger-ghost" type="button" :disabled="!rejectReason.trim() || acting" @click="onReject">
            {{ acting ? '提交中…' : '确认拒绝' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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

const route = useRoute()
const router = useRouter()
const approvalStore = useApprovalStore()
const userStore = useUserStore()

const SEGS = [
  { key: 'todo', label: '待我处理' },
  { key: 'mine', label: '我发起的' },
] as const
type TabKey = (typeof SEGS)[number]['key']

/** tab 与 URL query 双向同步（/m/approvals?tab=mine，MApply 提交后深链跳入） */
const tab = ref<TabKey>(route.query.tab === 'mine' ? 'mine' : 'todo')
watch(
  () => route.query.tab,
  (v) => {
    tab.value = v === 'mine' ? 'mine' : 'todo'
  },
)
const setTab = (k: TabKey) => {
  if (tab.value === k) return
  router.replace({ query: k === 'todo' ? {} : { tab: k } })
}

/** 详情弹层当前单据（closing = 播放退场动画后再卸载） */
const detail = ref<ApprovalItem>()
const detailClosing = ref(false)
/** 操作防重复提交 */
const acting = ref(false)

const openDetail = (item: ApprovalItem) => {
  detail.value = item
  detailClosing.value = false
}

const closeDetail = () => {
  if (detailClosing.value) return
  detailClosing.value = true
  setTimeout(() => {
    detail.value = undefined
    detailClosing.value = false
  }, 240)
}

/* ---------------- 拒绝弹层 ---------------- */
const rejectSheet = ref(false)
const rejectReason = ref('')

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
    closeDetail()
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
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写拒绝原因')
    return
  }
  acting.value = true
  try {
    if (target.bizType === 'TRANSFER') await transferApi.reject(target.bizId, reason)
    else await receiptApi.reject(target.bizId, reason)
    ElMessage.success('已拒绝')
    rejectSheet.value = false
    rejectReason.value = ''
    closeDetail()
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
.skeleton-wrap {
  padding-top: 2px;
}

.m-card-sub.summary {
  font-size: var(--text-base);
  color: var(--color-text-2);
}

.detail-items {
  margin-top: 4px;
}

.detail-items-title {
  font-size: var(--text-sm);
  color: var(--color-text-3);
  margin: 8px 2px;
}

.item-card {
  animation: none;
}

.item-name {
  font-size: var(--text-base);
  color: var(--color-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 拒绝原因弹层（底部半屏） ---------- */
.reject-sheet {
  position: fixed;
  inset: 0;
  z-index: 110;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.4);
}

.reject-panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  background: var(--color-bg-2);
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  padding-bottom: env(safe-area-inset-bottom);
  animation: reject-in 0.3s cubic-bezier(0.32, 0.72, 0, 1);
}

@keyframes reject-in {
  from {
    transform: translateY(100%);
  }
  to {
    transform: none;
  }
}

.reject-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 46px;
  border-bottom: 1px solid var(--color-border-light);
}

.reject-field {
  margin: 12px 16px 0;
}

.reject-actions {
  display: flex;
  gap: 12px;
  padding: 12px 16px 16px;
}

@media (prefers-reduced-motion: reduce) {
  .reject-panel {
    animation: none;
  }
}
</style>
