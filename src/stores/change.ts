import { defineStore } from 'pinia'
import { ref } from 'vue'
import { changeApi } from '@/api/modules/change'
import type { ChangeOrder } from '@/api/interface/change'

/**
 * M06 实物信息变更单（AOC）状态管理。
 * 发起 → 确认执行 / 撤销，单列表单据流；
 * 切换页面（keep-alive）时重新拉取以获得最新状态。
 */
export const useChangeStore = defineStore('change', () => {
  const changeOrders = ref<ChangeOrder[]>([])
  const loading = ref(false)

  const fetchChangeOrders = async () => {
    loading.value = true
    try {
      changeOrders.value = await changeApi.getChangeOrders()
    } finally {
      loading.value = false
    }
  }

  /** 提交申请成功：插入列表头部（新建单据排在最前） */
  const unshiftLocal = (item: ChangeOrder) => {
    changeOrders.value = [item, ...changeOrders.value]
  }

  /** 确认执行/撤销后用响应数据原地合并对应行（免全量刷新闪烁） */
  const upsertLocal = (item: ChangeOrder) => {
    const idx = changeOrders.value.findIndex((it) => it.id === item.id)
    if (idx >= 0) {
      const next = [...changeOrders.value]
      next[idx] = item
      changeOrders.value = next
    }
  }

  return {
    changeOrders,
    loading,
    fetchChangeOrders,
    unshiftLocal,
    upsertLocal,
  }
})
