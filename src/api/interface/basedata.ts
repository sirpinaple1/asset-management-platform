/** 公司主体（asset-backend Company） */
export interface Company {
  id: number
  code: string
  name: string
  remark?: string
  createdAt: string
  updatedAt: string
}

/** 厂商（asset-backend Manufacturer） */
export interface Manufacturer {
  id: number
  name: string
  contact?: string
  phone?: string
  address?: string
  remark?: string
  createdAt: string
  updatedAt: string
}

/** 厂商新增/编辑表单 */
export interface ManufacturerForm {
  name: string
  contact?: string
  phone?: string
  address?: string
  remark?: string
}

/** 供应商（asset-backend Supplier） */
export interface Supplier {
  id: number
  name: string
  contact?: string
  phone?: string
  address?: string
  remark?: string
  createdAt: string
  updatedAt: string
}

/** 供应商新增/编辑表单 */
export interface SupplierForm {
  name: string
  contact?: string
  phone?: string
  address?: string
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
