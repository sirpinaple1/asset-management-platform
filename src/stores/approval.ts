import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchAllApprovalItems } from '@/api/modules/approval'
import { isHandledBy, isMine, isTodoFor } from '@/api/interface/approval'
import type { ApprovalItem } from '@/api/interface/approval'
import { useUserStore } from '@/stores/user'

/**
 * 审批中心聚合 store：三类单据统一条目 + 当前用户视角计数。
 * 计数供全局侧边栏角标与工作台待办卡复用；
 * 刷新时机：审批中心/工作台挂载或切回、行内审批动作成功后（不做全局轮询）。
 */
export const useApprovalStore = defineStore('approval', () => {
  const items = ref<ApprovalItem[]>([])
  const loading = ref(false)
  const loadedAt = ref(0)

  const meUserId = () => useUserStore().me?.userId

  /** 待我处理数（PENDING 且非我发起；变更单含自己发起——后端允许自审） */
  const todoCount = computed(() => items.value.filter((it) => isTodoFor(it, meUserId())).length)

  /** 我发起的进行中数（含钉钉退还单审批中） */
  const mineActiveCount = computed(() =>
    items.value.filter(
      (it) =>
        isMine(it, meUserId()) &&
        (it.status === 'PENDING' || (it.bizType === 'RETURN' && it.status === 'RUNNING')),
    ).length,
  )

  /** 我处理的数（审批人/确认人是我，含拒绝记录） */
  const handledCount = computed(() => items.value.filter((it) => isHandledBy(it, meUserId())).length)

  /** 请求序号：快速连续触发时只保留最新一次请求的结果（竞态保护） */
  let fetchSeq = 0

  const refresh = async (): Promise<ApprovalItem[]> => {
    const seq = ++fetchSeq
    loading.value = true
    try {
      const list = await fetchAllApprovalItems()
      if (seq !== fetchSeq) return list
      items.value = list
      loadedAt.value = Date.now()
      return items.value
    } finally {
      if (seq === fetchSeq) loading.value = false
    }
  }

  return { items, loading, loadedAt, todoCount, mineActiveCount, handledCount, refresh }
})
