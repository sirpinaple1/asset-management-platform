import { request } from '@/api/config/request'
import type {
  Allocation,
  AllocationQuery,
  ReceiveReceipt,
  ReceiptApplyForm,
  ReceiptQuery,
} from '@/api/interface/receipt'

/**
 * M04 领用/借用单 API（契约依据：asset-backend ReceiveReceiptController / AllocationController）。
 */
export const receiptApi = {
  /** 发起领用/借用申请（一次可申请多台资产），返回新建单据（申请人取当前登录用户） */
  apply: (data: ReceiptApplyForm) =>
    request<ReceiveReceipt>({ url: '/v1/receipts', method: 'post', data }),

  /** 单据列表（支持 type/status/userId/date 筛选，含明细行与资产名称） */
  getReceipts: (params?: ReceiptQuery) =>
    request<ReceiveReceipt[]>({ url: '/v1/receipts', method: 'get', params }),

  /** 单据详情（含明细行与资产名称） */
  getReceiptById: (id: number) =>
    request<ReceiveReceipt>({ url: `/v1/receipts/${id}`, method: 'get' }),

  /** 批准（审批人与申请人不能是同一人；资产转在用并写持有记录） */
  approve: (id: number) =>
    request<ReceiveReceipt>({ url: `/v1/receipts/${id}/approve`, method: 'post' }),

  /** 拒绝（body={reason}，资产回闲置） */
  reject: (id: number, reason: string) =>
    request<ReceiveReceipt>({ url: `/v1/receipts/${id}/reject`, method: 'post', data: { reason } }),

  /** 持有关系列表（支持 assetId/userId/type/active 筛选，含资产名称） */
  getAllocations: (params?: AllocationQuery) =>
    request<Allocation[]>({ url: '/v1/allocations', method: 'get', params }),

  /** 归还（领用退库/借用归还共用：持有关系闭环，资产回闲置，写操作日志） */
  returnAllocation: (id: number, note?: string) =>
    request<void>({ url: `/v1/allocations/${id}/return`, method: 'post', data: { note } }),
}
