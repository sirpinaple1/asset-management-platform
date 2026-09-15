import { request } from '@/api/config/request'
import type {
  TransferApplyForm,
  TransferOrder,
  TransferQuery,
} from '@/api/interface/transfer'

/**
 * M05 调拨单 API（契约依据：asset-backend 1e0cb87 TransferOrderController）。
 */
export const transferApi = {
  /** 发起调拨（一次可调多台资产；调入位置与调入部门至少一项，后端校验） */
  apply: (data: TransferApplyForm) =>
    request<TransferOrder>({ url: '/v1/transfers', method: 'post', data }),

  /** 调拨单列表（支持 status/source/userId/dept/date 筛选，含明细行与位置名称） */
  getTransfers: (params?: TransferQuery) =>
    request<TransferOrder[]>({ url: '/v1/transfers', method: 'get', params }),

  /** 调拨单详情（含明细行与位置名称） */
  getTransferById: (id: number) =>
    request<TransferOrder>({ url: `/v1/transfers/${id}`, method: 'get' }),

  /** 调入方确认收到（不能由发起人自己确认；资产归属更新+持有转移+写日志） */
  confirm: (id: number) =>
    request<TransferOrder>({ url: `/v1/transfers/${id}/confirm`, method: 'post' }),

  /** 调入方拒绝接收（不能由发起人自己拒绝；body={reason} 必填，记录拒绝原因，资产不变） */
  reject: (id: number, reason: string) =>
    request<TransferOrder>({ url: `/v1/transfers/${id}/reject`, method: 'post', data: { reason } }),

  /** 撤销（仅 PENDING 状态、仅发起人可撤，资产不变） */
  cancel: (id: number) =>
    request<TransferOrder>({ url: `/v1/transfers/${id}/cancel`, method: 'post' }),
}
