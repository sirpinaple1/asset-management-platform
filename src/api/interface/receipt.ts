/**
 * M04 领用/借用单类型（契约依据：asset-backend ReceiveReceiptController / AllocationController + dto/receipt/*）。
 */

/** 单据类型：领用 / 借用（共用单据流，receive_receipt.type） */
export type ReceiptType = 'RECEIVE' | 'BORROW'

/** 单据状态（receive_receipt.status） */
export type ReceiptStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

/** 领用/借用单主表（ReceiveReceiptResp：列表与详情共用，列表也回填 items） */
export interface ReceiveReceipt {
  id: number
  /** 单号：ARE/BOR + yyyyMMdd + 4位序号 */
  serialNo: string
  status: ReceiptStatus
  /** 状态展示名（后端回填） */
  statusLabel?: string
  /** 单据类型：RECEIVE-领用 BORROW-借用 */
  type: ReceiptType
  /** 类型展示名（后端回填） */
  typeLabel?: string
  applicantUserId: number
  /** 申请人姓名（提交时快照，空时回退展示用户 ID） */
  applicantName?: string
  /** 领用部门 */
  department?: string
  /** 领用区域 ID（审批通过后资产位置更新至此；存量单可空） */
  locationId?: number
  /** 领用区域名称（列表/详情回填） */
  locationName?: string
  /** 领用事由 */
  reason?: string
  approverUserId?: number
  /** 审批人姓名（审批时快照） */
  approverName?: string
  approveTime?: string
  /** 审批意见 / 拒绝原因 */
  approveRemark?: string
  /** B1 定向待办：指定处理人 ID（PENDING 时仅该用户出现在"定向给我"分区；NULL=共享池） */
  assigneeUserId?: number
  /** 指定处理人姓名（提交时快照） */
  assigneeUserName?: string
  companyId?: number
  /** 明细行（含资产编码/名称/序列号） */
  items?: ReceiptItem[]
  createdAt: string
  updatedAt: string
}

/** 领用单明细行（receive_receipt_item，详情含资产信息） */
export interface ReceiptItem {
  id: number
  receiptId: number
  assetId: number
  /** 资产编码（后端 JOIN 名称，联调对齐） */
  assetBarcode?: string
  /** 资产名称（后端 JOIN 名称，联调对齐） */
  assetName?: string
  /** 资产序列号（后端 JOIN 名称，联调对齐） */
  assetSn?: string
  createdAt: string
}

/** 发起申请提交体（POST /v1/receipts；申请人由后端 UserContext 取，前端不传） */
export interface ReceiptApplyForm {
  type: ReceiptType
  /** 申请领用/借用的资产 ID 列表（一次可多台） */
  assetIds: number[]
  /** 领用区域（后端 @NotNull 必填，4a69c36）：审批通过后资产位置更新至此，盘点按位置扫资产的依据 */
  locationId: number
  /** 领用部门（后端 @NotBlank 必填） */
  department: string
  /** 领用事由（后端 @NotBlank 必填） */
  reason: string
  /** B1 定向待办：指定处理人 ID（NULL/不传 → 走共享池语义） */
  assigneeUserId?: number
  /** 指定处理人姓名（发起时快照；传了 assigneeUserId 建议一并带上） */
  assigneeUserName?: string
}

/** 列表查询参数（GET /v1/receipts，文档支持 status/date/userId/type 筛选） */
export interface ReceiptQuery {
  type?: ReceiptType
  status?: ReceiptStatus
  userId?: number
  /** 申请日期（yyyy-MM-dd） */
  date?: string
}

/** 状态展示配置（tabs / 表格 tag 共用） */
export const RECEIPT_STATUS_META: Record<
  ReceiptStatus,
  { label: string; tagType: 'warning' | 'success' | 'danger' }
> = {
  PENDING: { label: '待审批', tagType: 'warning' },
  APPROVED: { label: '已批准', tagType: 'success' },
  REJECTED: { label: '已拒绝', tagType: 'danger' },
}

/** 单据类型展示配置 */
export const RECEIPT_TYPE_META: Record<ReceiptType, { label: string; serialPrefix: string }> = {
  RECEIVE: { label: '领用', serialPrefix: 'ARE' },
  BORROW: { label: '借用', serialPrefix: 'BOR' },
}

/** 资产持有关系（AllocationResp：M04 发放 / M05 调拨确认写入，归还经 /return 闭环） */
export interface Allocation {
  id: number
  assetId: number
  assetBarcode?: string
  assetName?: string
  assetSn?: string
  /** 持有人 ID（NULL=部门持有——M05 调拨只填部门时，后端 4dc0d23 / V20260828 起可空） */
  userId?: number
  /** 持有人姓名（发放时快照；部门持有时为空） */
  userName?: string
  /** RECEIVE-领用 BORROW-借用 TRANSFER-调拨（列表接口 type 筛选仅收 RECEIVE/BORROW） */
  type: ReceiptType | 'TRANSFER'
  typeLabel?: string
  department?: string
  /** 发放时间 */
  allocatedAt: string
  /** 归还时间（空=持有中） */
  returnedAt?: string
  /** true-持有中 false-已归还 */
  active: boolean
  note?: string
  companyId?: number
  createdAt: string
}

/** 持有关系查询参数（GET /v1/allocations） */
export interface AllocationQuery {
  assetId?: number
  userId?: number
  type?: ReceiptType
  /** true-持有中 false-已归还 不传-全部 */
  active?: boolean
}
