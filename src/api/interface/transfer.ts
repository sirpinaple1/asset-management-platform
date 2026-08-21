/**
 * M05 调拨单（ATR）类型。
 * 契约依据：asset-backend docs/modules/M05-调拨单.md + transfer_order 表结构
 * （后端 M05 尚未实现，字段命名对齐 M04 ReceiptResp 模式：statusLabel/applicantName 回填、
 * items 含资产信息；后端落地后需按实际 DTO 核对）。
 */

/** 调拨单状态（transfer_order.status） */
export type TransferStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED' | 'REJECTED'

/** 调拨来源（transfer_order.source） */
export type TransferSource = 'MANUAL' | 'INVENTORY_TRIGGERED'

/** 调拨单主表（TransferResp：列表与详情共用，列表也回填 items） */
export interface TransferOrder {
  id: number
  /** 单号：ATR + yyyyMMdd + 4位序号 */
  serialNo: string
  status: TransferStatus
  /** 状态展示名（后端回填） */
  statusLabel?: string
  /** 来源：MANUAL-手动调拨 INVENTORY_TRIGGERED-盘点触发 */
  source: TransferSource
  /** 来源展示名（后端回填，空时回退本地 meta） */
  sourceLabel?: string
  /** 发起人 ID（调出方） */
  applicantUserId: number
  /** 发起人姓名（提交时快照，空时回退展示用户 ID） */
  applicantName?: string
  /** 调出位置（asset_location.id） */
  fromLocationId?: number
  /** 调出位置名称（后端 JOIN 名称，联调对齐） */
  fromLocationName?: string
  /** 调出方管理员 ID */
  fromUserId?: number
  /** 调出方管理员姓名（联调对齐） */
  fromUserName?: string
  /** 调入位置（asset_location.id） */
  toLocationId?: number
  /** 调入位置名称（后端 JOIN 名称，联调对齐） */
  toLocationName?: string
  /** 调入部门 */
  toDepartment?: string
  /** 调入方负责人 ID */
  toUserId?: number
  /** 调入方负责人姓名（联调对齐） */
  toUserName?: string
  /** 调拨原因 */
  reason?: string
  /** 确认人 ID（调入方） */
  confirmerUserId?: number
  /** 确认人姓名（确认时快照，联调对齐） */
  confirmerName?: string
  confirmTime?: string
  companyId?: number
  /** 明细行（含资产编码/名称/序列号） */
  items?: TransferItem[]
  createdAt: string
  updatedAt: string
}

/** 调拨单明细行（transfer_order_item，详情含资产信息） */
export interface TransferItem {
  id: number
  orderId: number
  assetId: number
  /** 资产编码（后端 JOIN 名称，联调对齐） */
  assetBarcode?: string
  /** 资产名称（后端 JOIN 名称，联调对齐） */
  assetName?: string
  /** 资产序列号（后端 JOIN 名称，联调对齐） */
  assetSn?: string
  createdAt: string
}

/**
 * 发起调拨提交体（POST /v1/transfers；发起人由后端 UserContext 取，前端不传）。
 * 调入部门/调拨原因前端按必填校验（对齐 M04 领用单 department/reason @NotBlank 先例），
 * 后端 M05 落地时若放宽/收紧需同步此处。
 */
export interface TransferApplyForm {
  /** 调拨的资产 ID 列表（一次可多台，闲置/在用均可发起） */
  assetIds: number[]
  /** 调入位置（asset_location.id，选填：按位置调拨场景） */
  toLocationId?: number
  /** 调入部门（必填） */
  toDepartment: string
  /** 调拨原因（必填） */
  reason: string
}

/** 列表查询参数（GET /v1/transfers，文档支持 status/date/dept 筛选） */
export interface TransferQuery {
  status?: TransferStatus
  /** 申请日期（yyyy-MM-dd） */
  date?: string
  /** 调入部门 */
  dept?: string
}

/** 状态展示配置（tabs / 表格 tag 共用） */
export const TRANSFER_STATUS_META: Record<
  TransferStatus,
  { label: string; tagType: 'warning' | 'success' | 'info' | 'danger' }
> = {
  PENDING: { label: '待确认', tagType: 'warning' },
  COMPLETED: { label: '已完成', tagType: 'success' },
  CANCELLED: { label: '已撤销', tagType: 'info' },
  REJECTED: { label: '已拒绝', tagType: 'danger' },
}

/** 来源展示配置 */
export const TRANSFER_SOURCE_META: Record<TransferSource, { label: string }> = {
  MANUAL: { label: '手动调拨' },
  INVENTORY_TRIGGERED: { label: '盘点触发' },
}
