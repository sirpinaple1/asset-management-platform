/**
 * M07 盘点类型。
 * 契约依据：asset-backend c113b5a StocktakeController / dto/stocktake/*（M07 已实现）。
 * 任务状态机：PENDING → IN_PROGRESS → COMPLETED（剩余待盘批量记盘亏）/ CANCELLED（仅创建人）；
 * 明细五态单向流转（已盘不可重盘）：待盘/账实相符/位置不符/盘亏/盘盈。
 */

/** 盘点任务状态（stocktake.status） */
export type StocktakeStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

/** 盘点明细状态（stocktake_item.status） */
export type StocktakeItemStatus =
  | 'PENDING'
  | 'MATCHED'
  | 'LOCATION_MISMATCH'
  | 'NOT_FOUND'
  | 'EXTRA'

/** 盘点任务主表（StocktakeResp：列表与详情共用，含范围名称与五态统计计数；详情/创建返回时附带 items） */
export interface Stocktake {
  id: number
  /** 盘点任务名称 */
  name: string
  status: StocktakeStatus
  /** 状态展示名（后端回填） */
  statusLabel?: string
  /** 盘点范围-位置 ID（空=全库；含子树） */
  locationId?: number
  /** 盘点范围-位置名称（查询回填，空=全库） */
  locationName?: string
  /** 盘点范围-分类 ID（空=全类） */
  categoryId?: number
  /** 盘点范围-分类名称（查询回填，空=全类） */
  categoryName?: string
  creatorUserId: number
  /** 创建人姓名（提交时快照，空时回退展示用户 ID） */
  creatorName?: string
  startTime?: string
  completeTime?: string
  remark?: string
  companyId?: number
  // ---- 统计（查询回填） ----
  /** 明细总数 */
  totalCount?: number
  pendingCount?: number
  matchedCount?: number
  mismatchCount?: number
  notFoundCount?: number
  extraCount?: number
  /** 明细行（详情/创建返回时回填） */
  items?: StocktakeItem[]
  createdAt: string
  updatedAt: string
}

/** 盘点明细行（StocktakeItemResp：含资产编码/名称/序列号与位置名称） */
export interface StocktakeItem {
  id: number
  stocktakeId: number
  assetId: number
  /** 资产编码（查询回填） */
  assetBarcode?: string
  /** 资产名称（查询回填） */
  assetName?: string
  /** 资产序列号（查询回填） */
  assetSn?: string
  status: StocktakeItemStatus
  /** 状态展示名（后端回填） */
  statusLabel?: string
  /** 系统记录位置 ID */
  expectedLocationId?: number
  /** 系统记录位置名称（查询回填） */
  expectedLocationName?: string
  /** 盘点实际位置 ID（未盘为空） */
  actualLocationId?: number
  /** 盘点实际位置名称（查询回填） */
  actualLocationName?: string
  /** 扫码/确认时间 */
  scannedAt?: string
  /** 盘点人 ID */
  scannedByUserId?: number
  remark?: string
  createdAt: string
}

/**
 * 创建盘点任务提交体（POST /v1/stocktakes；创建人由后端 UserContext 取，前端不传）。
 * 范围（位置/分类）均可空 = 全库盘点，位置范围含其子树；
 * 创建时快照范围内非报废资产生成明细，范围内无资产 400。
 */
export interface StocktakeCreateForm {
  name: string
  /** 盘点范围-位置（空=全库；含子树） */
  locationId?: number
  /** 盘点范围-分类（空=全类） */
  categoryId?: number
  remark?: string
}

/** 明细确认提交体（POST /v1/stocktakes/{id}/items/{itemId}/scan；notFound=false 时 actualLocationId 必填） */
export interface StocktakeScanForm {
  /** 实际位置（与系统记录位置一致=账实相符，否则=位置不符） */
  actualLocationId?: number
  /** 未找到实物（盘亏）；true 时忽略 actualLocationId */
  notFound?: boolean
  remark?: string
}

/** 按条码扫码提交体（POST /v1/stocktakes/{id}/scan，PDA 入口；条码必填） */
export interface StocktakeBarcodeScanForm {
  barcode: string
  /** 实际位置（可空=视为在系统记录位置找到；盘盈时空时取任务范围位置） */
  actualLocationId?: number
  remark?: string
}

/** 列表查询参数（GET /v1/stocktakes，支持 status/userId/date 筛选） */
export interface StocktakeQuery {
  status?: StocktakeStatus
  /** 创建人 ID */
  userId?: number
  /** 创建日期（yyyy-MM-dd） */
  date?: string
}

/** 盘点报告（GET /v1/stocktakes/{id}/report：五态汇总 + 差异明细；进行中可看实时统计） */
export interface StocktakeReport {
  stocktakeId: number
  name: string
  status: StocktakeStatus
  statusLabel?: string
  completeTime?: string
  totalCount?: number
  pendingCount?: number
  matchedCount?: number
  mismatchCount?: number
  notFoundCount?: number
  extraCount?: number
  /** 位置不符明细（可触发调拨归位） */
  mismatches?: StocktakeItem[]
  /** 盘亏明细 */
  notFounds?: StocktakeItem[]
  /** 盘盈明细 */
  extras?: StocktakeItem[]
}

/** 任务状态展示配置（tabs / 表格 tag 共用；对齐后端 StocktakeStatus 枚举 label） */
export const STOCKTAKE_STATUS_META: Record<
  StocktakeStatus,
  { label: string; tagType: 'info' | 'warning' | 'primary' | 'success' }
> = {
  PENDING: { label: '待开始', tagType: 'info' },
  IN_PROGRESS: { label: '进行中', tagType: 'warning' },
  COMPLETED: { label: '已完成', tagType: 'success' },
  CANCELLED: { label: '已取消', tagType: 'info' },
}

/** 明细状态展示配置（对齐后端 StocktakeItemStatus 枚举 label） */
export const STOCKTAKE_ITEM_STATUS_META: Record<
  StocktakeItemStatus,
  { label: string; tagType: 'info' | 'success' | 'warning' | 'danger' | 'primary' }
> = {
  PENDING: { label: '待盘', tagType: 'info' },
  MATCHED: { label: '账实相符', tagType: 'success' },
  LOCATION_MISMATCH: { label: '位置不符', tagType: 'warning' },
  NOT_FOUND: { label: '盘亏', tagType: 'danger' },
  EXTRA: { label: '盘盈', tagType: 'primary' },
}
