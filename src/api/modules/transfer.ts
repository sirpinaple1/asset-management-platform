import { request } from '@/api/config/request'
import type {
  TransferApplyForm,
  TransferOrder,
  TransferQuery,
} from '@/api/interface/transfer'

/**
 * M05 调拨单 API（契约依据：asset-backend docs/modules/M05-调拨单.md）。
 * 后端 M05 尚未实现，联调时以实际 Controller 为准核对。
 */
export const transferApi = {
  /** 发起调拨（一次可调多台资产，指定调入部门/位置），返回新建单据（发起人取当前登录用户） */
  apply: (data: TransferApplyForm) =>
    request<TransferOrder>({ url: '/v1/transfers', method: 'post', data }),

  /** 调拨单列表（支持 status/date/dept 筛选，含明细行与资产名称） */
  getTransfers: (params?: TransferQuery) =>
    request<TransferOrder[]>({ url: '/v1/transfers', method: 'get', params }),

  /** 调拨单详情（含明细行与资产名称） */
  getTransferById: (id: number) =>
    request<TransferOrder>({ url: `/v1/transfers/${id}`, method: 'get' }),

  /** 调入方确认收到（资产归属更新：位置/部门/负责人，写持有与操作日志） */
  confirm: (id: number) =>
    request<TransferOrder>({ url: `/v1/transfers/${id}/confirm`, method: 'post' }),

  /** 调入方拒绝接收（文档未定义 body，按无参调用） */
  reject: (id: number) =>
    request<TransferOrder>({ url: `/v1/transfers/${id}/reject`, method: 'post' }),

  /** 撤销（仅 PENDING 状态可撤，发起方或管理员；资产不变） */
  cancel: (id: number) =>
    request<TransferOrder>({ url: `/v1/transfers/${id}/cancel`, method: 'post' }),
}
