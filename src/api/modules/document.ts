import { receiptApi } from './receipt'
import { fetchAllApprovalItems } from './approval'
import type { AllocReturnDocItem, DocumentItem } from '@/api/interface/document'
import type { Allocation } from '@/api/interface/receipt'

/**
 * 全系统单据总览聚合 API（超管「全部单据」页）。
 * 复用审批中心聚合（含钉钉退还单）+ 退库/归还记录（持有关系 active=false）；
 * 各列表接口均无用户隔离，聚合结果即全系统视角。
 * 单源失败不阻塞其他源（allSettled 静默跳过，与审批中心一致）。
 */

/** 退库/归还记录：资产名 · 领用退库/借用归还（TRANSFER 闭环为调拨内部记账，不纳入） */
const fromAllocation = (a: Allocation): AllocReturnDocItem => {
  const returnKind: AllocReturnDocItem['returnKind'] = a.type === 'BORROW' ? '归还' : '退库'
  const assetText = a.assetName || a.assetBarcode || '资产'
  return {
    bizType: 'ALLOC_RETURN',
    bizId: a.id,
    assetId: a.assetId,
    assetBarcode: a.assetBarcode,
    assetName: a.assetName,
    holderUserId: a.userId,
    holderName: a.userName,
    returnKind,
    summary: `${assetText} · ${a.type === 'BORROW' ? '借用' : '领用'}${returnKind}`,
    returnedAt: a.returnedAt || a.createdAt,
  }
}

/** 排序键：退库/归还按归还时间，其余按申请时间 */
const timeOf = (it: DocumentItem): string =>
  it.bizType === 'ALLOC_RETURN' ? it.returnedAt : it.createdAt

/** 拉取全部单据并归一化（按时间倒序） */
export async function fetchAllDocumentItems(): Promise<DocumentItem[]> {
  const [approvalItems, allocations] = await Promise.allSettled([
    fetchAllApprovalItems(),
    receiptApi.getAllocations({ active: false }),
  ])
  const items: DocumentItem[] = []
  if (approvalItems.status === 'fulfilled') items.push(...approvalItems.value)
  if (allocations.status === 'fulfilled') {
    items.push(
      ...allocations.value.filter((a) => a.type !== 'TRANSFER').map(fromAllocation),
    )
  }
  return items.sort((a, b) => (timeOf(a) < timeOf(b) ? 1 : -1))
}
