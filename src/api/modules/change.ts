import { request } from '@/api/config/request'
import type { ChangeApplyForm, ChangeOrder, ChangeQuery } from '@/api/interface/change'

/**
 * M06 实物信息变更单（AOC）API。
 * 契约依据：asset-backend docs/modules/M06-变更单.md（后端未实现，联调时核对端点与请求体）。
 */
export const changeApi = {
  /** 发起变更（一次可多台资产；五个变更字段至少填一项，后端校验） */
  apply: (data: ChangeApplyForm) =>
    request<ChangeOrder>({ url: '/v1/change-orders', method: 'post', data }),

  /** 变更单列表（支持 status/userId/date 筛选） */
  getChangeOrders: (params?: ChangeQuery) =>
    request<ChangeOrder[]>({ url: '/v1/change-orders', method: 'get', params }),

  /** 变更单详情（含变更前/后对比明细） */
  getChangeOrderById: (id: number) =>
    request<ChangeOrder>({ url: `/v1/change-orders/${id}`, method: 'get' }),

  /** 确认执行（真正更新 asset 字段并写操作日志；沿用 M04/M05"操作人≠发起人"约定，联调核对） */
  confirm: (id: number) =>
    request<ChangeOrder>({ url: `/v1/change-orders/${id}/confirm`, method: 'post' }),

  /** 撤销（仅 PENDING 状态；沿用 M05"仅发起人可撤"约定，联调核对） */
  cancel: (id: number) =>
    request<ChangeOrder>({ url: `/v1/change-orders/${id}/cancel`, method: 'post' }),
}
