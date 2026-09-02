import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { notificationApi } from '@/api/modules/notification'
import { setTitleBadge } from '@/utils/tabTitle'

/**
 * 通知未读数共享 store：右上角 + 左下角两个铃铛实例共用同一份 unreadCount 与 60s 轮询，
 * 任一实例标记已读后两处红点即时同步（此前各自独立 ref 导致红点不同步）。
 */
export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)

  /* 浏览器 tab 标题角标：未读数变化（轮询刷新/已读扣减）即时同步，与铃铛红点同源 */
  watch(unreadCount, (n) => setTitleBadge(n), { immediate: true })

  let pollTimer: ReturnType<typeof setInterval> | undefined
  let binderCount = 0

  /** 拉取一次未读数（失败静默：401/503 已由 axios 拦截器统一处理） */
  const refreshCount = async () => {
    try {
      const resp = await notificationApi.unreadCount()
      unreadCount.value = resp.count || 0
    } catch {
      /* 静默 */
    }
  }

  /**
   * 铃铛实例挂载时调用：引用计数共享 60s 轮询（首个实例启动、全部卸载后停止）。
   * 返回卸载函数，供 onBeforeUnmount 调用。
   */
  const bindPolling = () => {
    binderCount += 1
    if (binderCount === 1) {
      void refreshCount()
      pollTimer = setInterval(refreshCount, 60 * 1000)
    }
    return () => {
      binderCount -= 1
      if (binderCount <= 0 && pollTimer) {
        clearInterval(pollTimer)
        pollTimer = undefined
      }
    }
  }

  /** 单条标记已读成功后本地扣减（避免等下一轮轮询） */
  const decreaseUnread = () => {
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }

  /** 全部标记已读成功后清零 */
  const clearUnread = () => {
    unreadCount.value = 0
  }

  return { unreadCount, refreshCount, bindPolling, decreaseUnread, clearUnread }
})
