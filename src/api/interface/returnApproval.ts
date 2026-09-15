/**
 * 钉钉退还审批（入口 B）：无系统单据，数据源为 approval_instance 映射记录 + 资产快照。
 * 审批动作在钉钉侧完成，系统仅跟踪状态与执行归还（终审 agree 后逐台闭环持有）。
 */

/** 退还资产快照明细（导入时解析命中） */
export interface ReturnApprovalAsset {
  id: number
  barcode: string
  name: string
  /** 资产当前状态（归还执行后回 IDLE） */
  status: string
}

/** 钉钉退还审批实例 */
export interface ReturnApproval {
  /** approval_instance.id */
  id: number
  /** 钉钉实例标题（如"李四提交的IT资产退还单"） */
  title: string
  /** 发起人（系统用户 id；未绑定内部账号为 null） */
  applicantUserId: number | null
  applicantName: string | null
  /** 钉钉实例状态：RUNNING/COMPLETED/TERMINATED */
  status: string
  /** 审批结果：agree/refuse（未终审为 null） */
  result: string | null
  createdAt: string
  /** 资产明细（存量记录可能为空数组） */
  assets: ReturnApprovalAsset[] | null
}

/** 退还审批状态展示：RUNNING=钉钉审批中，COMPLETED+agree=已归还，refuse=已拒绝，TERMINATED=已撤销 */
export function returnStatusTag(
  r: Pick<ReturnApproval, 'status' | 'result'>,
): { label: string; tagType: 'success' | 'warning' | 'danger' | 'info' } {
  if (r.status === 'RUNNING') return { label: '审批中(钉钉)', tagType: 'warning' }
  if (r.status === 'TERMINATED') return { label: '已撤销', tagType: 'info' }
  return r.result === 'agree'
    ? { label: '已归还', tagType: 'success' }
    : { label: '已拒绝', tagType: 'danger' }
}
