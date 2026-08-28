import type { PageResp } from '@/api/interface/common'

export type { PageResp }

/** 公司主体（asset-backend Company） */
export interface Company {
  id: number
  code: string
  name: string
  remark?: string
  createdAt: string
  updatedAt: string
}

/** 厂商分页查询参数（M02.5：厂商/供应商列表已服务端分页） */
export interface BasedataPageQuery {
  page?: number
  size?: number
  /** 名称关键词（后端仅对 name 模糊匹配） */
  keyword?: string
  /** 状态：1-启用 0-停用，缺省=全部 */
  status?: number
}

/** 厂商（asset-backend Manufacturer） */
export interface Manufacturer {
  id: number
  name: string
  contact?: string
  phone?: string
  email?: string
  address?: string
  /** 1-启用 0-停用 */
  status: number
  remark?: string
  createdAt: string
  updatedAt: string
}

/** 厂商新增/编辑表单（status 后端无默认值，新增/编辑都必须显式携带） */
export interface ManufacturerForm {
  name: string
  contact?: string
  phone?: string
  email?: string
  address?: string
  /** 1-启用 0-停用（新增默认 1） */
  status: number
  remark?: string
}

/** 供应商（asset-backend Supplier） */
export interface Supplier {
  id: number
  name: string
  contact?: string
  phone?: string
  email?: string
  address?: string
  /** 1-启用 0-停用 */
  status: number
  remark?: string
  createdAt: string
  updatedAt: string
}

/** 供应商新增/编辑表单 */
export interface SupplierForm {
  name: string
  contact?: string
  phone?: string
  email?: string
  address?: string
  /** 1-启用 0-停用（新增默认 1） */
  status: number
  remark?: string
}

/** 资产分类（树形结构） */
export interface Category {
  id: number
  name: string
  code?: string
  /** 资产编码前缀（新增资产服务端自动生成编码用，空则回退 SK） */
  barcodePrefix?: string
  parentId?: number
  sortOrder: number
  remark?: string
  children?: Category[]
}

/** 分类新增/编辑表单（契约：CategoryController CRUD；code 由后端生成不可编辑） */
export interface CategoryForm {
  name: string
  /** 父分类 ID（仅一级分类可选为父；空 = 顶级分类） */
  parentId?: number
  /** 资产编码前缀（空则回退 SK） */
  barcodePrefix?: string
  sortOrder?: number
  remark?: string
}

/** 资产位置（树形结构） */
export interface Location {
  id: number
  name: string
  code?: string
  parentId?: number
  path: string
  sortOrder: number
  remark?: string
  children?: Location[]
}

/** 位置新增/编辑表单（契约：LocationController CRUD；code/path 由后端生成不可编辑） */
export interface LocationForm {
  name: string
  /** 父位置 ID（空 = 顶级位置） */
  parentId?: number
  sortOrder?: number
  remark?: string
}

/** 资产型号（asset-backend AssetModel） */
export interface AssetModel {
  id: number
  name: string
  modelNumber?: string
  categoryId?: number
  manufacturerId?: number
  depreciationId?: number
  eolMonths?: number
  notes?: string
  createdAt: string
  updatedAt: string
}

/** 型号新增/编辑表单 */
export interface AssetModelForm {
  name: string
  modelNumber?: string
  categoryId?: number
  manufacturerId?: number
  depreciationId?: number
  eolMonths?: number
  notes?: string
}

/* ============ 审批链配置（两级审批人路由，仅超管） ============ */

/** 配置类型：DEPT_SUPERVISOR-部门主管（键=部门路径） WAREHOUSE_KEEPER-领料仓管理员（键=位置 id） */
export type ApprovalConfigType = 'DEPT_SUPERVISOR' | 'WAREHOUSE_KEEPER'

/** 审批链配置（契约：ApprovalConfigController，仅 systemAdmin 可管理） */
export interface ApprovalConfig {
  id: number
  configType: ApprovalConfigType
  configTypeLabel?: string
  /** DEPT=部门路径字符串；WAREHOUSE=位置 id（字符串） */
  configKey: string
  /** 展示名：DEPT=部门路径原文；WAREHOUSE=位置名称 */
  configKeyLabel?: string
  approverUserId: number
  approverUserName?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

/** 审批链配置新增/编辑表单 */
export interface ApprovalConfigForm {
  configType: ApprovalConfigType
  configKey: string
  approverUserId: number
  remark?: string
}
