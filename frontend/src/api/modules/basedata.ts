import { request } from '@/api/config/request'
import type {
  BasedataPageQuery,
  Company,
  Manufacturer,
  ManufacturerForm,
  Supplier,
  SupplierForm,
  Category,
  CategoryForm,
  Location,
  LocationForm,
  AssetModel,
  AssetModelForm,
  ApprovalConfig,
  ApprovalConfigForm,
  ApprovalConfigType,
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
  createCategory: (data: CategoryForm) =>
    request<Category>({ url: '/v1/categories', method: 'post', data }),
  updateCategory: (id: number, data: CategoryForm) =>
    request<Category>({ url: `/v1/categories/${id}`, method: 'put', data }),
  deleteCategory: (id: number) =>
    request<void>({ url: `/v1/categories/${id}`, method: 'delete' }),

  // 位置（树形结构）
  getLocations: (parentId?: number) =>
    request<Location[]>({ url: '/v1/locations', method: 'get', params: { parentId } }),
  getLocationById: (id: number) => request<Location>({ url: `/v1/locations/${id}`, method: 'get' }),
  createLocation: (data: LocationForm) =>
    request<Location>({ url: '/v1/locations', method: 'post', data }),
  updateLocation: (id: number, data: LocationForm) =>
    request<Location>({ url: `/v1/locations/${id}`, method: 'put', data }),
  deleteLocation: (id: number) =>
    request<void>({ url: `/v1/locations/${id}`, method: 'delete' }),

  // 型号
  getModels: (categoryId?: number) =>
    request<AssetModel[]>({ url: '/v1/models', method: 'get', params: { categoryId } }),
  getModelById: (id: number) => request<AssetModel>({ url: `/v1/models/${id}`, method: 'get' }),
  createModel: (data: AssetModelForm) =>
    request<AssetModel>({ url: '/v1/models', method: 'post', data }),
  updateModel: (id: number, data: AssetModelForm) =>
    request<AssetModel>({ url: `/v1/models/${id}`, method: 'put', data }),

  // 审批链配置（两级审批人路由，仅 systemAdmin；后端 403 门禁，前端菜单同步隐藏）
  getApprovalConfigs: (params?: { page?: number; size?: number; type?: ApprovalConfigType; keyword?: string }) =>
    request<PageResp<ApprovalConfig>>({ url: '/v1/approval-configs', method: 'get', params }),
  createApprovalConfig: (data: ApprovalConfigForm) =>
    request<ApprovalConfig>({ url: '/v1/approval-configs', method: 'post', data }),
  updateApprovalConfig: (id: number, data: ApprovalConfigForm) =>
    request<ApprovalConfig>({ url: `/v1/approval-configs/${id}`, method: 'put', data }),
  deleteApprovalConfig: (id: number) =>
    request<void>({ url: `/v1/approval-configs/${id}`, method: 'delete' }),
}
