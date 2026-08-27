/**
 * 通知中心：B2 notifications 接口类型定义。
 * - 仅聚合审批相关通知，不展示普通登录/安全消息。
 */

/** 通知业务类型：审批单据相关；后续可扩展盘点、资产到期等 */
export type NotificationBizType = 'APPROVAL'

/** 通知分类：审批待办提醒 + 审批处理结果反馈 */
export type NotificationCategory = 'TODO_ASSIGNED' | 'TODO_SHARED_POOL_NEW' | 'APPROVAL_RESULT'

/** 通知单条 */
export interface NotificationItem {
  id: number
  bizType: NotificationBizType
  category: NotificationCategory
  /** 关联单据 id，可深链跳转到对应单据详情 */
  refOrderId?: number
  /** 关联单据类型，例如 RECEIVE / TRANSFER / CHANGE / STOCKTAKE（详情跳转对应模块列表页） */
  refOrderBiz?: string
  title: string
  content: string
  read: boolean
  /** 毫秒级时间戳（后端下发 ISO8601 字符串，前端由 axios deserialize） */
  createdAt: string
}

/** 通知分页参数 */
export interface NotificationListReq {
  page: number
  size: number
  /** 仅未读：默认 true；false 代表全部 */
  unreadOnly?: boolean
}

/** 通知分页响应（服务端标准 Page<T> 结构，data.records 为数据行） */
export interface NotificationListResp {
  total: number
  records: NotificationItem[]
}

/** 未读计数响应 */
export interface NotificationUnreadResp {
  unreadCount: number
  /** 定向待办新通知数（更紧急：优先展示在铃铛角标红点上） */
  urgentCount: number
}

/** 分类文案映射（抽屉顶部 chip 分组） */
export const NOTIFICATION_CATEGORY_META: Record<
  NotificationCategory,
  { label: string; type: 'urgent' | 'info' | 'result' }
> = {
  TODO_ASSIGNED: { label: '指定给我', type: 'urgent' },
  TODO_SHARED_POOL_NEW: { label: '共享池新增', type: 'info' },
  APPROVAL_RESULT: { label: '审批结果', type: 'result' },
}
