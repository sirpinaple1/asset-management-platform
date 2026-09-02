import { defineStore } from 'pinia'
import { ref } from 'vue'
import { transferApi } from '@/api/modules/transfer'
import type { TransferOrder } from '@/api/interface/transfer'

/**
 * M05 调拨单状态管理。
 * 调出方发起 → 调入方确认/拒绝 / 发起方撤销，单列表单据流；
 * 切换页面（keep-alive）时重新拉取以获得最新状态。
 */
export const useTransferStore = defineStore('transfer', () => {
  const transfers = ref<TransferOrder[]>([])
  const loading = ref(false)

  /** 请求序号：快速连续触发时只保留最新一次请求的结果（竞态保护） */
  let fetchSeq = 0

  const fetchTransfers = async () => {
    const seq = ++fetchSeq
    loading.value = true
    try {
      const list = await transferApi.getTransfers()
      if (seq !== fetchSeq) return
      transfers.value = list
    } finally {
      if (seq === fetchSeq) loading.value = false
    }
  }

  /** 提交申请成功：插入列表头部（新建单据排在最前） */
  const unshiftLocal = (item: TransferOrder) => {
    transfers.value = [item, ...transfers.value]
  }

  /** 确认/拒绝/撤销后用响应数据原地合并对应行（免全量刷新闪烁） */
  const upsertLocal = (item: TransferOrder) => {
    const idx = transfers.value.findIndex((it) => it.id === item.id)
    if (idx >= 0) {
      const next = [...transfers.value]
      next[idx] = item
      transfers.value = next
    }
  }

  return {
    transfers,
    loading,
    fetchTransfers,
    unshiftLocal,
    upsertLocal,
  }
})
