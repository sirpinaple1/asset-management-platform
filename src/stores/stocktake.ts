import { defineStore } from 'pinia'
import { ref } from 'vue'
import { stocktakeApi } from '@/api/modules/stocktake'
import type { Stocktake } from '@/api/interface/stocktake'

/**
 * M07 盘点状态管理。
 * 创建 → 开始 → 扫码/人工确认（五态）→ 完成（剩余待盘记盘亏）→ 报告 → 触发调拨；
 * 切换页面（keep-alive）时重新拉取以获得最新状态。
 */
export const useStocktakeStore = defineStore('stocktake', () => {
  const stocktakes = ref<Stocktake[]>([])
  const loading = ref(false)

  /** 请求序号：快速连续触发时只保留最新一次请求的结果（竞态保护） */
  let fetchSeq = 0

  const fetchStocktakes = async () => {
    const seq = ++fetchSeq
    loading.value = true
    try {
      const list = await stocktakeApi.getStocktakes()
      if (seq !== fetchSeq) return
      stocktakes.value = list
    } finally {
      if (seq === fetchSeq) loading.value = false
    }
  }

  /** 创建成功：插入列表头部（新任务排在最前） */
  const unshiftLocal = (item: Stocktake) => {
    stocktakes.value = [item, ...stocktakes.value]
  }

  /** 开始/取消/完成/生成调拨后用响应数据原地合并对应行（免全量刷新闪烁） */
  const upsertLocal = (item: Stocktake) => {
    const idx = stocktakes.value.findIndex((it) => it.id === item.id)
    if (idx >= 0) {
      const next = [...stocktakes.value]
      next[idx] = item
      stocktakes.value = next
    }
  }

  return {
    stocktakes,
    loading,
    fetchStocktakes,
    unshiftLocal,
    upsertLocal,
  }
})
