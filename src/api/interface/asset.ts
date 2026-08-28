/**
 * M03 资产类型（契约依据：asset-backend AssetController / AssetReq / AssetResp / AssetLogResp）。
 */

export type { PageResp } from '@/api/interface/common'

/** 资产状态（状态机：IDLE/IN_USE→DISCARD 终态；PENDING_CONFIRM 由 M04 单据流转） */
export type AssetStatus = 'IDLE' | 'IN_USE' | 'PENDING_CONFIRM' | 'DISCARD'

/** 资产状态展示配置 */
export const ASSET_STATUS_META: Record<AssetStatus, { label: string; tagType: 'info' | 'success' | 'warning' | 'danger' }> = {
  IDLE: { label: '闲置', tagType: 'info' },
  IN_USE: { label: '在用', tagType: 'success' },
  PENDING_CONFIRM: { label: '待确认', tagType: 'warning' },
  DISCARD: { label: '报废', tagType: 'danger' },
}

/** 可报废状态（状态机：仅 闲置/在用 → 报废） */
export const DISCARDABLE_STATUSES: AssetStatus[] = ['IDLE', 'IN_USE']

/** 资产（asset-backend AssetResp，含关联名称） */
export interface Asset {
  id: number
  /** 资产编码 */
  barcode: string
  name: string
  /** 序列号 */
  sn?: string
  /** 细则（区分同品牌型号但配置不同的资产，如 16G内存/512G固态） */
  spec?: string
  status: AssetStatus
  statusLabel: string
  categoryId?: number
  categoryName?: string
  modelId?: number
  modelName?: string
  supplierId?: number
  supplierName?: string
  locationId?: number
  locationName?: string
  homeLocationId?: number
  homeLocationName?: string
  locationDetail?: string
  userId?: number
  /** 使用人姓名（后端经 UserDirectory 实时反查 sys_user） */
  userName?: string
  userDepartment?: string
  adminUserId?: number
  /** 资产管理员姓名（后端经 UserDirectory 实时反查 sys_user） */
  adminUserName?: string
  companyId?: number
  companyName?: string
  purchaseDate?: string
  amount?: number
  remark?: string
  createdAt: string
  updatedAt: string
}

/** 资产新增/编辑表单（契约：AssetReq。状态不开放编辑——新增固定 IDLE，流转走业务端点） */
export interface AssetForm {
  /**
   * 资产编码（唯一）。新增可不传——由服务端自动生成（分类前缀-日期-序号），传入即忽略；
   * 编辑可传（改码场景），不传保持原编码不变。
   */
  barcode?: string
  /** 资产名称（必填） */
  name: string
  sn?: string
  /** 细则（区分同品牌型号但配置不同的资产） */
  spec?: string
  categoryId?: number
  modelId?: number
  supplierId?: number
  /** 当前位置 */
  locationId?: number
  /** 应归放位置 */
  homeLocationId?: number
  locationDetail?: string
  /** 使用人 ID（comm_public_basic 用户） */
  userId?: number
  userDepartment?: string
  /** 资产管理员 ID（comm_public_basic 用户） */
  adminUserId?: number
  companyId?: number
  /** 购置日期（YYYY-MM-DD） */
  purchaseDate?: string
  /** 购入金额（元） */
  amount?: number
  remark?: string
}

/** 资产分页查询参数（GET /v1/assets 支持的筛选维度） */
export interface AssetPageQuery {
  page?: number
  size?: number
  status?: AssetStatus
  categoryId?: number
  locationId?: number
  companyId?: number
  userId?: number
  adminUserId?: number
  /** 关键词（资产编码/名称/序列号模糊匹配） */
  keyword?: string
  /** 排序字段（camelCase 属性名：barcode/name/purchaseDate 等，待后端支持） */
  orderBy?: string
  /** 排序方向 */
  orderDir?: 'asc' | 'desc'
}

/** 资产操作日志（AssetLogResp：新增/领用/归还/调拨/实物信息变更/盘点处理/报废） */
export interface AssetLog {
  id: number
  assetId: number
  operationType: string
  operatorUserId?: number
  /** 操作人原始文本（M08 迁移保底） */
  operatorLabel?: string
  content: string
  createdAt: string
}
