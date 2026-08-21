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
  parentId?: number
  sortOrder: number
  remark?: string
  children?: Category[]
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

/** 位置新增表单 */
export interface LocationForm {
  name: string
  parentId?: number
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
