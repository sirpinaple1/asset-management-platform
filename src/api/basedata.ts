import request from './request'
import type {
  Company,
  Manufacturer,
  ManufacturerForm,
  Supplier,
  SupplierForm,
  Category,
  Location,
  AssetModel,
  AssetModelForm,
} from '@/types/basedata'

export const basedataApi = {
  // 公司
  getCompanies: () => request.get<never, Company[]>('/api/v1/companies'),
  getCompanyById: (id: number) => request.get<never, Company>(`/api/v1/companies/${id}`),

  // 厂商
  getManufacturers: () => request.get<never, Manufacturer[]>('/api/v1/manufacturers'),
  getManufacturerById: (id: number) => request.get<never, Manufacturer>(`/api/v1/manufacturers/${id}`),
  createManufacturer: (data: ManufacturerForm) =>
    request.post<never, Manufacturer>('/api/v1/manufacturers', data),
  updateManufacturer: (id: number, data: ManufacturerForm) =>
    request.put<never, Manufacturer>(`/api/v1/manufacturers/${id}`, data),
  deleteManufacturer: (id: number) =>
    request.delete<never, void>(`/api/v1/manufacturers/${id}`),

  // 供应商
  getSuppliers: () => request.get<never, Supplier[]>('/api/v1/suppliers'),
  getSupplierById: (id: number) => request.get<never, Supplier>(`/api/v1/suppliers/${id}`),
  createSupplier: (data: SupplierForm) =>
    request.post<never, Supplier>('/api/v1/suppliers', data),
  updateSupplier: (id: number, data: SupplierForm) =>
    request.put<never, Supplier>(`/api/v1/suppliers/${id}`, data),
  deleteSupplier: (id: number) =>
    request.delete<never, void>(`/api/v1/suppliers/${id}`),

  // 分类（树形结构）
  getCategories: () => request.get<never, Category[]>('/api/v1/categories'),
  getCategoryById: (id: number) => request.get<never, Category>(`/api/v1/categories/${id}`),

  // 位置（树形结构）
  getLocations: (parentId?: number) =>
    request.get<never, Location[]>('/api/v1/locations', { params: { parentId } }),
  getLocationById: (id: number) => request.get<never, Location>(`/api/v1/locations/${id}`),
  createLocation: (data: { name: string; parentId?: number; remark?: string }) =>
    request.post<never, Location>('/api/v1/locations', data),

  // 型号
  getModels: (categoryId?: number) =>
    request.get<never, AssetModel[]>('/api/v1/models', { params: { categoryId } }),
  getModelById: (id: number) => request.get<never, AssetModel>(`/api/v1/models/${id}`),
  createModel: (data: AssetModelForm) =>
    request.post<never, AssetModel>('/api/v1/models', data),
  updateModel: (id: number, data: AssetModelForm) =>
    request.put<never, AssetModel>(`/api/v1/models/${id}`, data),
}
