/**
 * 通知中心：notifications 接口类型定义（对齐后端 NotificationType / NotificationResp）。
 */

/** 通知类型（后端 NotificationType 枚举） */
export type NotificationType =
  | 'DOC_SUBMITTED' // 单据待处理（通知处理人）
  | 'DOC_APPROVED' // 单据已通过（通知发起人）
  | 'DOC_REJECTED' // 单据已拒绝（通知发起人）
  | 'DOC_COMPLETED' // 单据已完成/已执行（通知发起人）
  | 'DOC_PROGRESS' // 审批进度更新（两级审批链）
  | 'DOC_CC' // 抄送我的（钉钉实例终态后按模板抄送人通知）
  | 'APPROVAL_CONFIG_ALERT' // 审批链配置告警（systemAdmin）
  | 'DINGTALK_SYNC_ALERT' // 钉钉同步告警（systemAdmin）

/** 通知单条（后端 NotificationResp） */
export interface NotificationItem {
  id: number
  /** 通知类型（NotificationType 枚举值） */
  type: NotificationType | string
  /** 类型展示名（后端返回，如"抄送我的"、"单据待处理"） */
  typeLabel: string
  /** 通知标题 */
  title: string
  /** 业务单据类型：RECEIVE / BORROW / TRANSFER / CHANGE / RETURN（RETURN 且 bizId=0 为退还审批，无系统单据） */
  bizType?: string
  /** 业务单据 id（点击跳转单据详情） */
  bizId?: number
  /** 已读标记：0-未读 1-已读 */
  readFlag: number
  /** 创建时间（ISO8601 字符串） */
  createdAt: string
}

/** 通知分页参数（GET /v1/notifications） */
export interface NotificationListReq {
  page: number
  size: number
  /** true=仅未读（默认 false） */
  unread?: boolean
  /** 类型过滤（可空=全部），如 DOC_CC=抄送我的 */
  type?: string
}

/** 通知分页响应（PageResp<NotificationResp>） */
export interface NotificationListResp {
  records: NotificationItem[]
  total: number
  page?: number
  size?: number
}

/** 未读计数响应（GET /v1/notifications/unread-count，含抄送类） */
export interface NotificationUnreadResp {
  count: number
}

/** 类型 → 标签颜色映射（typeLabel 文案由后端返回，前端只管配色） */
export const NOTIFICATION_TYPE_TAG: Record<string, 'warning' | 'success' | 'danger' | 'info' | 'primary'> = {
  DOC_SUBMITTED: 'warning',
  DOC_APPROVED: 'success',
  DOC_COMPLETED: 'success',
  DOC_REJECTED: 'danger',
  DOC_PROGRESS: 'primary',
  DOC_CC: 'info',
  APPROVAL_CONFIG_ALERT: 'danger',
  DINGTALK_SYNC_ALERT: 'danger',
}
