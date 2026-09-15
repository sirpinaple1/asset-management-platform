/**
 * M06 实物信息变更单（AOC）类型。
 * 契约依据：asset-backend ChangeOrderController / dto/change/*（M06 已实现）。
 * 一单 = N 台资产 + 一组统一新值，各 new_* 字段 null = 不变更（至少一项）；
 * 明细行按"资产 × 实际变化字段"展开，value_before/value_after 存展示值（位置/公司存名称、使用人存姓名）。
 */

/** 变更单状态（change_order.status；信息修正单据，确认执行允许发起人自己操作） */
export type ChangeStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED'

/** 可变更的实物信息字段（change_order_item.field_name，白名单见后端 ChangeField 枚举） */
export type ChangeFieldName =
  | 'user_id'
  | 'user_department'
  | 'location_id'
  | 'location_detail'
  | 'company_id'

/** 变更单主表（ChangeResp：列表与详情共用，列表也回填 items；主表带 new_* 变更后目标值） */
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
  /** B1 定向待办：指定处理人 ID（NULL/不传 → 共享池） */
  assigneeUserId?: number
  /** 指定处理人姓名（发起时快照） */
  assigneeUserName?: string
  /** 变更后使用人 ID（null = 不变更） */
  newUserId?: number
  /** 变更后使用人姓名（发起时快照） */
  newUserName?: string
  /** 变更后使用人部门（null = 不变更） */
  newUserDepartment?: string
  /** 变更后位置 ID（null = 不变更） */
  newLocationId?: number
  /** 变更后位置名称（查询回填） */
  newLocationName?: string
  /** 变更后存放位置明细（null = 不变更） */
  newLocationDetail?: string
  /** 变更后归属公司 ID（null = 不变更） */
  newCompanyId?: number
  /** 变更后归属公司名称（查询回填） */
  newCompanyName?: string
  /** 确认执行人 ID */
  confirmerUserId?: number
  /** 确认执行人姓名（确认时快照） */
  confirmerName?: string
  confirmTime?: string
  companyId?: number
  /** 明细行（每台资产的每个实际变更字段一行，含变更前/后对比） */
  items?: ChangeItem[]
  createdAt: string
  updatedAt: string
}

/** 变更明细行（change_order_item；value_before/value_after 为展示值快照） */
export interface ChangeItem {
  id: number
  orderId: number
  assetId: number
  /** 资产编码（查询回填） */
  assetBarcode?: string
  /** 资产名称（查询回填） */
  assetName?: string
  /** 资产序列号（查询回填） */
  assetSn?: string
  /** 变更字段名（user_id / user_department / location_id / location_detail / company_id） */
  fieldName: ChangeFieldName | string
  /** 字段展示名（后端 ChangeField 枚举回填，空时回退本地 meta） */
  fieldLabel?: string
  /** 变更前值（展示值；null = 未设置） */
  valueBefore?: string
  /** 变更后值（展示值；null = 未设置） */
  valueAfter?: string
  createdAt: string
}

/**
 * 发起变更提交体（POST /v1/change-orders；发起人由后端 UserContext 取，前端不传）。
 * 一单 = 指定资产列表 + 一组统一新值，各字段 null = 不变更；
 * 五个 new_* 至少一项非空（服务端 400 校验，前端同步预检）。
 */
export interface ChangeApplyForm {
  /** 变更的资产 ID 列表（一次可多台，服务端去重） */
  assetIds: number[]
  /** 变更后使用人 ID（comm_public_basic 用户，后端无法回查姓名） */
  newUserId?: number
  /** 变更后使用人姓名（前端已知时快照传入；空则后端回退展示用户 ID） */
  newUserName?: string
  /** 变更后使用人部门 */
  newUserDepartment?: string
  /** 变更后位置（asset_location.id） */
  newLocationId?: number
  /** 变更后存放位置明细 */
  newLocationDetail?: string
  /** 变更后归属公司（company.id） */
  newCompanyId?: number
  /** 变更原因（选填） */
  reason?: string
  /** B1 定向待办：指定处理人 ID（NULL/不传 → 共享池） */
  assigneeUserId?: number
  /** 指定处理人姓名（发起时快照） */
  assigneeUserName?: string
}

/** 列表查询参数（GET /v1/change-orders，支持 status/userId/assetId/date 筛选） */
export interface ChangeQuery {
  status?: ChangeStatus
  /** 发起人 ID */
  userId?: number
  /** 资产 ID（查该资产的变更历史） */
  assetId?: number
  /** 申请日期（yyyy-MM-dd） */
  date?: string
}

/** 状态展示配置（tabs / 表格 tag 共用；对齐后端 ChangeStatus 枚举 label） */
export const CHANGE_STATUS_META: Record<
  ChangeStatus,
  { label: string; tagType: 'warning' | 'success' | 'info' }
> = {
  PENDING: { label: '待确认', tagType: 'warning' },
  CONFIRMED: { label: '已执行', tagType: 'success' },
  CANCELLED: { label: '已撤销', tagType: 'info' },
}

/** 变更字段展示配置（对齐后端 ChangeField 枚举 label；fieldLabel 回退用） */
export const CHANGE_FIELD_META: Record<string, { label: string }> = {
  user_id: { label: '使用人' },
  user_department: { label: '使用部门' },
  location_id: { label: '区域' },
  location_detail: { label: '存放位置明细' },
  company_id: { label: '归属公司' },
}
