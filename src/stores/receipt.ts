import { defineStore } from 'pinia'
import { ref } from 'vue'
import { receiptApi } from '@/api/modules/receipt'
import type { Allocation, ReceiveReceipt, ReceiptType } from '@/api/interface/receipt'

/**
 * M04 领用/借用单状态管理。
 * 领用（RECEIVE）与借用（BORROW）两菜单共用单据流，按 type 拉取同一接口；
 * 切换页面（keep-alive）时重新拉取以获得最新审批状态。
 */
export const useReceiptStore = defineStore('receipt', () => {
  /** 当前页面的单据列表（按 type 拉取） */
  const receipts = ref<ReceiveReceipt[]>([])
  const loading = ref(false)

  /** 当前页面的持有关系列表（按 type 拉取，含已归还；退库/归还视图数据源） */
  const allocations = ref<Allocation[]>([])
  const allocationsLoading = ref(false)

  /** 请求序号（竞态保护）：领用/借用切换时只保留最新一次请求的结果 */
  let receiptSeq = 0
  let allocSeq = 0

  const fetchReceipts = async (type: ReceiptType) => {
    const seq = ++receiptSeq
    loading.value = true
    try {
      const list = await receiptApi.getReceipts({ type })
      if (seq !== receiptSeq) return
      receipts.value = list
    } finally {
      if (seq === receiptSeq) loading.value = false
    }
  }

  const fetchAllocations = async (type: ReceiptType) => {
    const seq = ++allocSeq
    allocationsLoading.value = true
    try {
      const list = await receiptApi.getAllocations({ type })
      if (seq !== allocSeq) return
      allocations.value = list
    } finally {
      if (seq === allocSeq) allocationsLoading.value = false
    }
  }

  /** 提交申请成功：插入列表头部（新建单据排在最前） */
  const unshiftLocal = (item: ReceiveReceipt) => {
    receipts.value = [item, ...receipts.value]
  }

  /** 审批后用响应数据原地合并对应行（免全量刷新闪烁） */
  const upsertLocal = (item: ReceiveReceipt) => {
    const idx = receipts.value.findIndex((it) => it.id === item.id)
    if (idx >= 0) {
      const next = [...receipts.value]
      next[idx] = item
      receipts.value = next
    }
  }

  /**
   * 归还成功：本地把对应持有关系标记为已归还。
   * 后端 return 端点返回 void，returnedAt 以本地时间近似（下次拉取会被服务端值覆盖）。
   */
  const markReturnedLocal = (allocationId: number) => {
    const idx = allocations.value.findIndex((it) => it.id === allocationId)
    if (idx >= 0) {
      const next = [...allocations.value]
      next[idx] = { ...next[idx], active: false, returnedAt: new Date().toISOString() }
      allocations.value = next
    }
  }

  return {
    receipts,
    loading,
    allocations,
    allocationsLoading,
    fetchReceipts,
    fetchAllocations,
    unshiftLocal,
    upsertLocal,
    markReturnedLocal,
  }
})
