/**
 * M06 实物信息变更单（AOC）类型。
 * 契约依据：asset-backend docs/modules/M06-变更单.md（后端未实现，以下为按模块文档的前端先行契约，
 * 联调时以 ChangeOrderController 实际契约为准核对——重点核对：状态枚举命名、提交体结构、权限规则）。
 * 变更字段范围仅限实物归属类：user_id / user_department / location_id / location_detail / company_id。
 */

/** 变更单状态（change_order.status；模块文档仅约定 确认执行/撤销 两种流转，枚举命名待后端落定） */
export type ChangeStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED'

/** 可变更的实物信息字段（change_order_item.field_name，模块文档约定值） */
export type ChangeFieldName =
  | 'user_id'
  | 'user_department'
  | 'location_id'
  | 'location_detail'
  | 'company_id'

/** 变更单主表（ChangeResp：列表与详情共用，详情含 items 明细） */
export interface ChangeOrder {
  id: number
  /** 单号：AOC + yyyyMMdd + 4位序号 */
  serialNo: string
  status: ChangeStatus
  /** 状态展示名（后端回填） */
  statusLabel?: string
  /** 发起人 ID */
  applicantUserId: number
  /** 发起人姓名（提交时快照，空时回退展示用户 ID） */
  applicantName?: string
  /** 变更原因 */
  reason?: string
  /** 确认执行人 ID */
  confirmerUserId?: number
  /** 确认执行人姓名（确认时快照） */
  confirmerName?: string
  confirmTime?: string
  /** 明细行（每台资产的每个变更字段一行，含变更前/后对比） */
  items?: ChangeItem[]
  createdAt: string
  updatedAt: string
}

/**
 * 变更明细行（change_order_item）。
 * value_before / value_after 为字符串快照（如 user_id 存 ID、location_id 存位置名称展示值，
 * 具体存 ID 还是展示名待联调核对——模块文档示例两种都出现过，展示优先取名称）。
 */
export interface ChangeItem {
  id: number
  orderId: number
  assetId: number
  /** 资产编码（后端 JOIN 名称，联调对齐） */
  assetBarcode?: string
  /** 资产名称（后端 JOIN 名称，联调对齐） */
  assetName?: string
  /** 资产序列号（后端 JOIN 名称，联调对齐） */
  assetSn?: string
  /** 变更字段名（user_id / user_department / location_id / location_detail / company_id） */
  fieldName: ChangeFieldName | string
  /** 变更字段展示名（如"使用人"/"区域"，后端回填，空时回退本地 meta） */
  fieldLabel?: string
  /** 变更前值 */
  valueBefore?: string
  /** 变更后值 */
  valueAfter?: string
  createdAt: string
}

/**
 * 发起变更提交体（POST /v1/change-orders；发起人由后端 UserContext 取，前端不传）。
 * 结构沿用 M04/M05 扁平 DTO 风格（ReceiptApplyForm / TransferApplyForm），仅传值字段视为变更项；
 * 五个变更字段至少填一项（服务端校验，前端同步预检）。
 */
export interface ChangeApplyForm {
  /** 变更的资产 ID 列表（一次可多台，变更前值由服务端逐台快照） */
  assetIds: number[]
  /** 使用人 ID（comm_public_basic 用户） */
  userId?: number
  /** 使用部门 */
  userDepartment?: string
  /** 区域（asset_location.id） */
  locationId?: number
  /** 存放地点（详细位置） */
  locationDetail?: string
  /** 所属公司（company.id） */
  companyId?: number
  /** 变更原因（选填） */
  reason?: string
}

/** 列表查询参数（GET /v1/change-orders，对齐 M04/M05 的筛选维度） */
export interface ChangeQuery {
  status?: ChangeStatus
  /** 发起人 ID */
  userId?: number
  /** 申请日期（yyyy-MM-dd） */
  date?: string
}

/** 状态展示配置（tabs / 表格 tag 共用；待执行=发起后等待确认执行） */
export const CHANGE_STATUS_META: Record<
  ChangeStatus,
  { label: string; tagType: 'warning' | 'success' | 'info' }
> = {
  PENDING: { label: '待执行', tagType: 'warning' },
  CONFIRMED: { label: '已执行', tagType: 'success' },
  CANCELLED: { label: '已撤销', tagType: 'info' },
}

/** 变更字段展示配置（fieldLabel 回退 + 发起弹窗表单共用） */
export const CHANGE_FIELD_META: Record<string, { label: string }> = {
  user_id: { label: '使用人' },
  user_department: { label: '使用部门' },
  location_id: { label: '区域' },
  location_detail: { label: '存放地点' },
  company_id: { label: '所属公司' },
}
