import { defineStore } from 'pinia'
import { ref, type Ref } from 'vue'
import { basedataApi } from '@/api/modules/basedata'
import type {
  BasedataPageQuery,
  Company,
  Manufacturer,
  Supplier,
  Category,
  Location,
  AssetModel,
} from '@/api/interface/basedata'

/** 乐观删除快照：记录被移除的行及其原始位置，用于失败回滚与撤销恢复 */
export interface DeleteSnapshot<T> {
  item: T
  index: number
}

/** 乐观删除结果：failedCount>0 表示部分失败（失败项已回滚），snapshot 为成功项（供撤销） */
export interface OptimisticDeleteResult<T> {
  failedCount: number
  okCount: number
  snapshot: DeleteSnapshot<T>[]
}

/**
 * 生成乐观删除工具集（厂商/供应商同构复用）：
 * - optimisticDelete：本地立即移除（UI 即时反馈），并发调删除接口，失败项按原位插回
 * - undoDelete：调恢复接口，成功项按原位插回
 * - upsertLocal：新增/编辑成功后用响应数据原地合并，避免全量 refetch 闪烁
 */
function createOptimisticOps<T extends { id: number }>(
  list: Ref<T[]>,
  deleteApi: (id: number) => Promise<void>,
  restoreApi: (id: number) => Promise<void>,
) {
  /** 按原始索引把快照插回列表（索引升序逐个插入，min 防越界） */
  const insertBack = (snapshot: DeleteSnapshot<T>[]) => {
    const next = [...list.value]
    ;[...snapshot]
      .sort((a, b) => a.index - b.index)
      .forEach(({ item, index }) => next.splice(Math.min(index, next.length), 0, item))
    list.value = next
  }

  const optimisticDelete = async (ids: number[]): Promise<OptimisticDeleteResult<T>> => {
    const idSet = new Set(ids)
    // 快照并本地移除（从后往前 splice 不影响前面的索引）
    const snapshot: DeleteSnapshot<T>[] = []
    const next = [...list.value]
    for (let i = next.length - 1; i >= 0; i--) {
      if (idSet.has(next[i].id)) {
        snapshot.unshift({ item: next[i], index: i })
        next.splice(i, 1)
      }
    }
    list.value = next

    // 并发删除，收集失败项
    const results = await Promise.allSettled(ids.map((id) => deleteApi(id)))
    const failedIds = new Set<number>()
    results.forEach((r, i) => {
      if (r.status === 'rejected') failedIds.add(ids[i])
    })
    if (failedIds.size) insertBack(snapshot.filter((s) => failedIds.has(s.item.id)))

    const okSnapshot = snapshot.filter((s) => !failedIds.has(s.item.id))
    return { failedCount: failedIds.size, okCount: okSnapshot.length, snapshot: okSnapshot }
  }

  /** 撤销删除：全部恢复成功返回 true */
  const undoDelete = async (snapshot: DeleteSnapshot<T>[]): Promise<boolean> => {
    if (!snapshot.length) return true
    const results = await Promise.allSettled(snapshot.map((s) => restoreApi(s.item.id)))
    const okItems = snapshot.filter((_, i) => results[i].status === 'fulfilled')
    insertBack(okItems)
    return okItems.length === snapshot.length
  }

  const upsertLocal = (item: T) => {
    const idx = list.value.findIndex((it) => it.id === item.id)
    if (idx >= 0) {
      const next = [...list.value]
      next[idx] = item
      list.value = next
    } else {
      list.value = [...list.value, item]
    }
  }

  return { optimisticDelete, undoDelete, upsertLocal }
}

export const useBasedataStore = defineStore('basedata', () => {
  // 状态
  const companies = ref<Company[]>([])
  const manufacturers = ref<Manufacturer[]>([])
  const suppliers = ref<Supplier[]>([])
  const categories = ref<Category[]>([])
  const locations = ref<Location[]>([])
  const models = ref<AssetModel[]>([])

  // 服务端分页 total（M02.5：厂商/供应商）
  const manufacturerTotal = ref(0)
  const supplierTotal = ref(0)

  // 加载状态
  const loading = ref({
    companies: false,
    manufacturers: false,
    suppliers: false,
    categories: false,
    locations: false,
    models: false,
  })

  /** 各类基础数据的请求序号（竞态保护）：快速连续触发时只保留最新一次请求的结果 */
  const fetchSeqMap: Record<string, number> = {}
  const nextSeq = (key: string) => (++fetchSeqMap[key] || (fetchSeqMap[key] = 1))
  const isLatest = (key: string, seq: number) => fetchSeqMap[key] === seq

  // 操作方法
  const fetchCompanies = async () => {
    const seq = nextSeq('companies')
    loading.value.companies = true
    try {
      const list = await basedataApi.getCompanies()
      if (!isLatest('companies', seq)) return
      companies.value = list
    } finally {
      if (isLatest('companies', seq)) loading.value.companies = false
    }
  }

  /** 厂商列表（服务端分页：列表页传 page/size/keyword/status；下拉等全量场景传 size:500） */
  const fetchManufacturers = async (query?: BasedataPageQuery) => {
    const seq = nextSeq('manufacturers')
    loading.value.manufacturers = true
    try {
      const page = await basedataApi.getManufacturers(query)
      if (!isLatest('manufacturers', seq)) return
      manufacturers.value = page.records
      manufacturerTotal.value = page.total
    } finally {
      if (isLatest('manufacturers', seq)) loading.value.manufacturers = false
    }
  }

  /** 供应商列表（服务端分页，语义同厂商） */
  const fetchSuppliers = async (query?: BasedataPageQuery) => {
    const seq = nextSeq('suppliers')
    loading.value.suppliers = true
    try {
      const page = await basedataApi.getSuppliers(query)
      if (!isLatest('suppliers', seq)) return
      suppliers.value = page.records
      supplierTotal.value = page.total
    } finally {
      if (isLatest('suppliers', seq)) loading.value.suppliers = false
    }
  }

  const fetchCategories = async () => {
    const seq = nextSeq('categories')
    loading.value.categories = true
    try {
      const list = await basedataApi.getCategories()
      if (!isLatest('categories', seq)) return
      categories.value = list
    } finally {
      if (isLatest('categories', seq)) loading.value.categories = false
    }
  }

  /** 删除分类（有子分类/有资产引用时后端 400 拦截；树形结构由调用方重拉） */
  const deleteCategory = (id: number) => basedataApi.deleteCategory(id)

  const fetchLocations = async (parentId?: number) => {
    const seq = nextSeq('locations')
    loading.value.locations = true
    try {
      const list = await basedataApi.getLocations(parentId)
      if (!isLatest('locations', seq)) return
      locations.value = list
    } finally {
      if (isLatest('locations', seq)) loading.value.locations = false
    }
  }

  /** 删除位置（有子位置/有资产引用时后端 400 拦截；树形结构由调用方重拉） */
  const deleteLocation = (id: number) => basedataApi.deleteLocation(id)

  const fetchModels = async (categoryId?: number) => {
    const seq = nextSeq('models')
    loading.value.models = true
    try {
      const list = await basedataApi.getModels(categoryId)
      if (!isLatest('models', seq)) return
      models.value = list
    } finally {
      if (isLatest('models', seq)) loading.value.models = false
    }
  }

  // 乐观更新与撤销（厂商/供应商）
  const manufacturerOps = createOptimisticOps(manufacturers, basedataApi.deleteManufacturer, basedataApi.restoreManufacturer)
  const supplierOps = createOptimisticOps(suppliers, basedataApi.deleteSupplier, basedataApi.restoreSupplier)

  return {
    companies,
    manufacturers,
    suppliers,
    categories,
    locations,
    models,
    manufacturerTotal,
    supplierTotal,
    loading,
    fetchCompanies,
    fetchManufacturers,
    fetchSuppliers,
    fetchCategories,
    deleteCategory,
    fetchLocations,
    deleteLocation,
    fetchModels,
    deleteManufacturersOptimistic: manufacturerOps.optimisticDelete,
    undoDeleteManufacturers: manufacturerOps.undoDelete,
    upsertManufacturerLocal: manufacturerOps.upsertLocal,
    deleteSuppliersOptimistic: supplierOps.optimisticDelete,
    undoDeleteSuppliers: supplierOps.undoDelete,
    upsertSupplierLocal: supplierOps.upsertLocal,
  }
})
