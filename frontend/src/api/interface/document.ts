/**
 * 全系统单据总览类型（超管「全部单据」页）。
 * 覆盖六类记录：审批中心五类（M04 领用/借用 + M05 调拨 + M06 变更 + 钉钉退还审批 RETURN）
 * + 退库/归还闭环记录（ALLOC_RETURN，源数据 = Allocation 持有关系 active=false）。
 * 注意区分两个"退还"：
 * - RETURN（钉钉退还审批）：无系统单据，审批在钉钉侧完成；
 * - ALLOC_RETURN（退库/归还记录）：系统持有关系闭环的动作记录。
 * 数据源均为无用户隔离的列表接口，聚合后即"全系统"视角。
 */
import type { ApprovalBizType, ApprovalItem } from './approval'
import type { ReturnApproval } from './returnApproval'

/** 参与总览的单据类型：审批中心五类 + 退库/归还记录 */
export type DocumentBizType = ApprovalBizType | 'ALLOC_RETURN'

/** 退库/归还记录条目（源数据 = Allocation 持有关系 active=false） */
export interface AllocReturnDocItem {
  bizType: 'ALLOC_RETURN'
  /** 持有关系 ID（allocation.id） */
  bizId: number
  /** 资产 ID（详情跳转 /assets?id= 用） */
  assetId: number
  /** 资产编码（列表"单号"位展示） */
  assetBarcode?: string
  assetName?: string
  /** 退还人（持有关系快照；部门持有时为空） */
  holderUserId?: number
  holderName?: string
  /** 退库（领用退库）/ 归还（借用归还） */
  returnKind: '退库' | '归还'
  /** 摘要：资产名 · 领用退库/借用归还 */
  summary: string
  /** 归还时间（排序与展示键） */
  returnedAt: string
}

/** 总览统一条目：审批中心条目（原样复用，含钉钉退还）+ 退库/归还记录 */
export type DocumentItem = ApprovalItem | AllocReturnDocItem

/** 状态归一分组（总览页状态 tabs） */
export type DocStatusGroup = 'PENDING' | 'PASSED' | 'RETURNED' | 'REJECTED' | 'CANCELLED'

/** 判别：退库/归还记录（Allocation 闭环） */
export function isAllocReturn(item: DocumentItem): item is AllocReturnDocItem {
  return item.bizType === 'ALLOC_RETURN'
}

/**
 * 单据状态归一化到总览分组：
 * - PENDING：三类单据进行中 / 钉钉退还审批中（RUNNING）
 * - PASSED：领用/借用 APPROVED · 调拨 COMPLETED · 变更 CONFIRMED
 * - RETURNED：退库/归还记录 / 钉钉退还终审同意（COMPLETED+agree）
 * - REJECTED / CANCELLED：终态直接对应（钉钉 refuse→REJECTED，TERMINATED→CANCELLED）
 */
export function docStatusGroupOf(item: DocumentItem): DocStatusGroup {
  if (isAllocReturn(item)) return 'RETURNED'
  if (item.bizType === 'RETURN') {
    const ret = item.raw as ReturnApproval
    if (ret.status === 'RUNNING') return 'PENDING'
    if (ret.status === 'TERMINATED') return 'CANCELLED'
    return ret.result === 'agree' ? 'RETURNED' : 'REJECTED'
  }
  switch (item.status) {
    case 'PENDING':
      return 'PENDING'
    case 'APPROVED':
    case 'COMPLETED':
    case 'CONFIRMED':
      return 'PASSED'
    case 'REJECTED':
      return 'REJECTED'
    case 'CANCELLED':
      return 'CANCELLED'
    default:
      return 'PASSED'
  }
}

/** 类型筛选 chips 展示配置（审批中心五类沿用 APPROVAL_BIZ_META 文案） */
export const DOC_TYPE_META: Record<DocumentBizType, { label: string }> = {
  RECEIVE: { label: '领用' },
  BORROW: { label: '借用' },
  TRANSFER: { label: '调拨' },
  CHANGE: { label: '变更' },
  RETURN: { label: '退还' },
  ALLOC_RETURN: { label: '退库归还' },
}
