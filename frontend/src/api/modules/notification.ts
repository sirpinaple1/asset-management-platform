import { request } from '@/api/config/request'
import type {
  NotificationItem,
  NotificationListReq,
  NotificationListResp,
  NotificationUnreadResp,
} from '@/api/interface/notification'

/** 通知中心 API（对齐后端 NotificationController：/api/v1/notifications） */
export const notificationApi = {
  /** 列表（type 过滤如 DOC_CC=抄送我的；unread=true 只看未读） */
  list: (params: NotificationListReq) =>
    request<NotificationListResp>({ url: '/v1/notifications', method: 'get', params }),

  /** 未读计数（铃铛角标，60s 轮询调用；含抄送类，口径不变） */
  unreadCount: () =>
    request<NotificationUnreadResp>({ url: '/v1/notifications/unread-count', method: 'get' }),

  /** 单条标记已读（仅本人通知；点击条目时调用） */
  markRead: (id: number) =>
    request<NotificationItem>({ url: `/v1/notifications/${id}/read`, method: 'post' }),

  /** 全部标记已读（注意：会把抄送通知一并标读） */
  markAllRead: () =>
    request<{ updated: number }>({ url: '/v1/notifications/read-all', method: 'post' }),
}
