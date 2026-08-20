export interface Company {
  id: number
  code: string
  name: string
  remark?: string
  createdAt: string
  updatedAt: string
}

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

export interface ManufacturerForm {
  name: string
  contact?: string
  phone?: string
  address?: string
  remark?: string
}

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

export interface SupplierForm {
  name: string
  contact?: string
  phone?: string
  address?: string
  remark?: string
}

export interface Category {
  id: number
  name: string
  code?: string
  parentId?: number
  sortOrder: number
  remark?: string
  children?: Category[]
}

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

export interface AssetModelForm {
  name: string
  modelNumber?: string
  categoryId?: number
  manufacturerId?: number
  depreciationId?: number
  eolMonths?: number
  notes?: string
}
