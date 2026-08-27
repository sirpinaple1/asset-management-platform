import request from '@/utils/request'
import type {
  NotificationItem,
  NotificationListReq,
  NotificationListResp,
  NotificationUnreadResp,
} from '@/api/interface/notification'

/** B2：拉取通知列表（支持分页 + 未读过滤） */
export const notificationApi = {
  /** 列表（默认只拉未读） */
  list: (params: NotificationListReq) =>
    request.get<any, NotificationListResp>('/api/v1/notifications', { params }),

  /** 未读计数（铃铛角标，60s 轮询调用） */
  unreadCount: () => request.get<any, NotificationUnreadResp>('/api/v1/notifications/unread-count'),

  /** 单条标记已读（点击详情时调用） */
  markRead: (id: number) => request.patch<any, NotificationItem>(`/api/v1/notifications/${id}/read`),

  /** 批量标记全部已读（右上角按钮） */
  markAllRead: () => request.patch<any, { readCount: number }>('/api/v1/notifications/mark-all-read'),
}
