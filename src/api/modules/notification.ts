import { request } from '@/api/config/request'
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
    request<NotificationListResp>({ url: '/v1/notifications', method: 'get', params }),

  /** 未读计数（铃铛角标，60s 轮询调用） */
  unreadCount: () =>
    request<NotificationUnreadResp>({ url: '/v1/notifications/unread-count', method: 'get' }),

  /** 单条标记已读（点击详情时调用） */
  markRead: (id: number) =>
    request<NotificationItem>({ url: `/v1/notifications/${id}/read`, method: 'patch' }),

  /** 批量标记全部已读（右上角按钮） */
  markAllRead: () =>
    request<{ readCount: number }>({ url: '/v1/notifications/mark-all-read', method: 'patch' }),
}
