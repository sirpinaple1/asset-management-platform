/**
 * 审批中心聚合类型（M04 领用/借用 + M05 调拨 + M06 变更三类单据的统一视图）。
 * B1 定向待办语义：
 * - PENDING + assigneeUserId = 指定处理人 → "定向给我"分区（只有该用户能在自己的待办里看到）
 * - PENDING + assigneeUserId 为空 → 共享池（任何非发起人可见/可处理）
 * - 非 PENDING → 一律不进待办
 * - M06 变更单确认 无 requireNotApplicant 校验，发起人可自审（共享池也显示）。
 */
import type { ReceiveReceipt } from './receipt'
import type { TransferOrder } from './transfer'
import type { ChangeOrder } from './change'
import { RECEIPT_STATUS_META } from './receipt'
import { TRANSFER_STATUS_META } from './transfer'
import { CHANGE_STATUS_META } from './change'

/** 参与审批中心的单据类型 */
export type ApprovalBizType = 'RECEIVE' | 'BORROW' | 'TRANSFER' | 'CHANGE'

/** 审批中心 tab：待我处理 / 我发起的 / 我处理的 */
export type ApprovalTabKey = 'todo' | 'mine' | 'handled'

/** 归一化后的审批条目（三类单据统一结构） */
export interface ApprovalItem {
  bizType: ApprovalBizType
  /** 原始单据 ID（各模块接口的 id） */
  bizId: number
  serialNo: string
  applicantUserId: number
  applicantName?: string
  /** B1 定向待办：指定处理人 ID；NULL/undefined = 共享池 */
  assigneeUserId?: number
  /** 指定处理人姓名（提交时快照） */
  assigneeUserName?: string
  /** 摘要：如"3 台资产 → IT部在用仓" */
  summary: string
  /** 单据状态（PENDING/APPROVED/REJECTED/COMPLETED/CANCELLED/CONFIRMED） */
  status: string
  createdAt: string
  /** 原始单据（行内操作/详情跳转取字段用） */
  raw: ReceiveReceipt | TransferOrder | ChangeOrder
}

/** 待办分区：定向给我 vs 共享池（审批中心待我处理 tab 的两级分区） */
export type TodoBucket = 'directed' | 'pool'

/**
 * 判断一条 PENDING 单据当前是"定向给我"还是"共享池"（非 PENDING 不建议调用）。
 * - assigneeUserId = me   → directed
 * - assigneeUserId != me 但不为空 → 不属于我（调用方通常已过滤）
 * - 其他情况 → pool
 * meUserId 为 undefined 时返回 pool（调用方应已用 isTodoFor 过滤）。
 */
export function todoBucketOf(item: ApprovalItem, meUserId?: number): TodoBucket {
  if (meUserId === undefined) return 'pool'
  return item.assigneeUserId === meUserId ? 'directed' : 'pool'
}

/** 业务类型展示与列表页路径（详情深链 ?id= 的落点） */
export const APPROVAL_BIZ_META: Record<ApprovalBizType, { label: string; listPath: string }> = {
  RECEIVE: { label: '领用', listPath: '/receipts/receive' },
  BORROW: { label: '借用', listPath: '/receipts/borrow' },
  TRANSFER: { label: '调拨', listPath: '/transfers' },
  CHANGE: { label: '变更', listPath: '/changes' },
}

/** tab 展示配置 */
export const APPROVAL_TAB_META: Record<ApprovalTabKey, { label: string }> = {
  todo: { label: '待我处理' },
  mine: { label: '我发起的' },
  handled: { label: '我处理的' },
}

/** 状态 tag 类型（el-tag type） */
type TagType = 'success' | 'warning' | 'danger' | 'info'

/** 状态 tag 展示：优先后端 statusLabel，回退各模块本地 meta */
export function approvalStatusTag(item: ApprovalItem): { label: string; tagType: TagType } {
  switch (item.bizType) {
    case 'RECEIVE':
    case 'BORROW': {
      const raw = item.raw as ReceiveReceipt
      const meta = RECEIPT_STATUS_META[raw.status]
      return { label: raw.statusLabel || meta.label, tagType: meta.tagType }
    }
    case 'TRANSFER': {
      const raw = item.raw as TransferOrder
      const meta = TRANSFER_STATUS_META[raw.status]
      return { label: raw.statusLabel || meta.label, tagType: meta.tagType }
    }
    case 'CHANGE': {
      const raw = item.raw as ChangeOrder
      const meta = CHANGE_STATUS_META[raw.status]
      return { label: raw.statusLabel || meta.label, tagType: meta.tagType }
    }
  }
}

/* ---------------- 三个 tab 的过滤规则（B1 定向待办 + 共享池语义） ---------------- */

/**
 * 待我处理（定向 + 共享池的并集）：
 *  - 非 PENDING → false
 *  - assigneeUserId = me → true（定向给我，发起人也是我也允许——变更单场景）
 *  - assigneeUserId 非空且 ≠ me → false（定向给别人，我看不到）
 *  - assigneeUserId 空（共享池）：
 *      - 变更单：true（允许发起人自审）
 *      - 其他类型：仅当我不是发起人时 true
 */
export function isTodoFor(item: ApprovalItem, meUserId?: number): boolean {
  if (meUserId === undefined) return false
  if (item.status !== 'PENDING') return false
  if (item.assigneeUserId !== undefined && item.assigneeUserId !== null) {
    return item.assigneeUserId === meUserId
  }
  // 共享池分支
  if (item.bizType === 'CHANGE') return true
  return item.applicantUserId !== meUserId
}

/** 我发起的（全状态） */
export function isMine(item: ApprovalItem, meUserId?: number): boolean {
  return meUserId !== undefined && item.applicantUserId === meUserId
}

/** 我处理的：审批人/确认人是我（M04 approverUserId；M05/M06 confirmerUserId，含拒绝记录） */
export function isHandledBy(item: ApprovalItem, meUserId?: number): boolean {
  if (meUserId === undefined) return false
  const raw = item.raw
  if ('approverUserId' in raw) return raw.approverUserId === meUserId
  if ('confirmerUserId' in raw) return raw.confirmerUserId === meUserId
  return false
}
