import { receiptApi } from './receipt'
import { transferApi } from './transfer'
import { changeApi } from './change'
import type { ApprovalItem } from '@/api/interface/approval'
import type { ReceiveReceipt } from '@/api/interface/receipt'
import type { TransferOrder } from '@/api/interface/transfer'
import type { ChangeOrder } from '@/api/interface/change'

/**
 * 审批中心聚合 API：并行拉取三类单据列表并归一化（纯前端聚合，共享池语义）。
 * 单源失败不阻塞其他源（allSettled 静默跳过，与各列表页独立访问行为一致）。
 */

/** 领用/借用单摘要：N 台资产 · 去向（领用区域优先，回退部门） */
const fromReceipt = (r: ReceiveReceipt): ApprovalItem => {
  const count = r.items?.length
  const target = r.locationName || r.department || ''
  return {
    bizType: r.type,
    bizId: r.id,
    serialNo: r.serialNo,
    applicantUserId: r.applicantUserId,
    applicantName: r.applicantName,
    summary:
      count === undefined ? target || '—' : `${count} 台资产${target ? ` · ${target}` : ''}`,
    status: r.status,
    createdAt: r.createdAt,
    raw: r,
  }
}

/** 调拨单摘要：N 台资产 → 去向（调入位置优先，回退调入部门） */
const fromTransfer = (t: TransferOrder): ApprovalItem => {
  const count = t.items?.length
  const target = t.toLocationName || t.toDepartment || '未指定去向'
  return {
    bizType: 'TRANSFER',
    bizId: t.id,
    serialNo: t.serialNo,
    applicantUserId: t.applicantUserId,
    applicantName: t.applicantName,
    summary: count === undefined ? target : `${count} 台资产 → ${target}`,
    status: t.status,
    createdAt: t.createdAt,
    raw: t,
  }
}

/** 变更单摘要：N 台资产 · 变更 k 项（new* 非空字段计数；资产数按明细去重） */
const fromChange = (c: ChangeOrder): ApprovalItem => {
  const count = c.items ? new Set(c.items.map((it) => it.assetId)).size : undefined
  const fields = [
    c.newUserId,
    c.newUserDepartment,
    c.newLocationId,
    c.newLocationDetail,
    c.newCompanyId,
  ].filter((v) => v !== undefined && v !== null && v !== '').length
  return {
    bizType: 'CHANGE',
    bizId: c.id,
    serialNo: c.serialNo,
    applicantUserId: c.applicantUserId,
    applicantName: c.applicantName,
    summary: count === undefined ? `变更 ${fields} 项` : `${count} 台资产 · 变更 ${fields} 项`,
    status: c.status,
    createdAt: c.createdAt,
    raw: c,
  }
}

/** 拉取三类单据并归一化（按申请时间倒序） */
export async function fetchAllApprovalItems(): Promise<ApprovalItem[]> {
  const [receipts, transfers, changes] = await Promise.allSettled([
    receiptApi.getReceipts(),
    transferApi.getTransfers(),
    changeApi.getChangeOrders(),
  ])
  const items: ApprovalItem[] = []
  if (receipts.status === 'fulfilled') items.push(...receipts.value.map(fromReceipt))
  if (transfers.status === 'fulfilled') items.push(...transfers.value.map(fromTransfer))
  if (changes.status === 'fulfilled') items.push(...changes.value.map(fromChange))
  return items.sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
}
