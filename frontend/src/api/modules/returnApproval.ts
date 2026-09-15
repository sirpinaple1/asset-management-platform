import { request } from '@/api/config/request'
import type { ReturnApproval } from '@/api/interface/returnApproval'

/**
 * 钉钉退还审批 API（契约依据：asset-backend ReturnApprovalController）。
 */
export const returnApprovalApi = {
  /** 退还审批列表（含发起人、资产快照明细，按导入时间倒序） */
  getReturnApprovals: () =>
    request<ReturnApproval[]>({ url: '/v1/return-approvals', method: 'get' }),
}
