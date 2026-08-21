import { request } from '@/api/config/request'
import type {
  BasedataPageQuery,
  Company,
  Manufacturer,
  ManufacturerForm,
  Supplier,
  SupplierForm,
  Category,
  Location,
  LocationForm,
  AssetModel,
  AssetModelForm,
} from '@/api/interface/basedata'
import type { PageResp } from '@/api/interface/common'

/** M02 基础数据 API（baseURL 已含 /api，路径以 /v1 开头） */
export const basedataApi = {
  // 公司
  getCompanies: () => request<Company[]>({ url: '/v1/companies', method: 'get' }),
  getCompanyById: (id: number) => request<Company>({ url: `/v1/companies/${id}`, method: 'get' }),

  // 厂商（M02.5 起服务端分页：keyword 仅匹配名称，status 1-启用 0-停用）
  getManufacturers: (params?: BasedataPageQuery) =>
    request<PageResp<Manufacturer>>({ url: '/v1/manufacturers', method: 'get', params }),
  getManufacturerById: (id: number) => request<Manufacturer>({ url: `/v1/manufacturers/${id}`, method: 'get' }),
  createManufacturer: (data: ManufacturerForm) =>
    request<Manufacturer>({ url: '/v1/manufacturers', method: 'post', data }),
  updateManufacturer: (id: number, data: ManufacturerForm) =>
    request<Manufacturer>({ url: `/v1/manufacturers/${id}`, method: 'put', data }),
  deleteManufacturer: (id: number) =>
    request<void>({ url: `/v1/manufacturers/${id}`, method: 'delete' }),
  restoreManufacturer: (id: number) =>
    request<void>({ url: `/v1/manufacturers/${id}/restore`, method: 'put' }),

  // 供应商（M02.5 起服务端分页）
  getSuppliers: (params?: BasedataPageQuery) =>
    request<PageResp<Supplier>>({ url: '/v1/suppliers', method: 'get', params }),
  getSupplierById: (id: number) => request<Supplier>({ url: `/v1/suppliers/${id}`, method: 'get' }),
  createSupplier: (data: SupplierForm) =>
    request<Supplier>({ url: '/v1/suppliers', method: 'post', data }),
  updateSupplier: (id: number, data: SupplierForm) =>
    request<Supplier>({ url: `/v1/suppliers/${id}`, method: 'put', data }),
  deleteSupplier: (id: number) =>
    request<void>({ url: `/v1/suppliers/${id}`, method: 'delete' }),
  restoreSupplier: (id: number) =>
    request<void>({ url: `/v1/suppliers/${id}/restore`, method: 'put' }),

  // 分类（树形结构）
  getCategories: () => request<Category[]>({ url: '/v1/categories', method: 'get' }),
  getCategoryById: (id: number) => request<Category>({ url: `/v1/categories/${id}`, method: 'get' }),

  // 位置（树形结构）
  getLocations: (parentId?: number) =>
    request<Location[]>({ url: '/v1/locations', method: 'get', params: { parentId } }),
  getLocationById: (id: number) => request<Location>({ url: `/v1/locations/${id}`, method: 'get' }),
  createLocation: (data: LocationForm) =>
    request<Location>({ url: '/v1/locations', method: 'post', data }),

  // 型号
  getModels: (categoryId?: number) =>
    request<AssetModel[]>({ url: '/v1/models', method: 'get', params: { categoryId } }),
  getModelById: (id: number) => request<AssetModel>({ url: `/v1/models/${id}`, method: 'get' }),
  createModel: (data: AssetModelForm) =>
    request<AssetModel>({ url: '/v1/models', method: 'post', data }),
  updateModel: (id: number, data: AssetModelForm) =>
    request<AssetModel>({ url: `/v1/models/${id}`, method: 'put', data }),
}
