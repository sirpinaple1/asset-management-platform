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

  // 操作方法
  const fetchCompanies = async () => {
    loading.value.companies = true
    try {
      companies.value = await basedataApi.getCompanies()
    } finally {
      loading.value.companies = false
    }
  }

  /** 厂商列表（服务端分页：列表页传 page/size/keyword/status；下拉等全量场景传 size:500） */
  const fetchManufacturers = async (query?: BasedataPageQuery) => {
    loading.value.manufacturers = true
    try {
      const page = await basedataApi.getManufacturers(query)
      manufacturers.value = page.records
      manufacturerTotal.value = page.total
    } finally {
      loading.value.manufacturers = false
    }
  }

  /** 供应商列表（服务端分页，语义同厂商） */
  const fetchSuppliers = async (query?: BasedataPageQuery) => {
    loading.value.suppliers = true
    try {
      const page = await basedataApi.getSuppliers(query)
      suppliers.value = page.records
      supplierTotal.value = page.total
    } finally {
      loading.value.suppliers = false
    }
  }

  const fetchCategories = async () => {
    loading.value.categories = true
    try {
      categories.value = await basedataApi.getCategories()
    } finally {
      loading.value.categories = false
    }
  }

  const fetchLocations = async (parentId?: number) => {
    loading.value.locations = true
    try {
      locations.value = await basedataApi.getLocations(parentId)
    } finally {
      loading.value.locations = false
    }
  }

  const fetchModels = async (categoryId?: number) => {
    loading.value.models = true
    try {
      models.value = await basedataApi.getModels(categoryId)
    } finally {
      loading.value.models = false
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
    fetchLocations,
    fetchModels,
    deleteManufacturersOptimistic: manufacturerOps.optimisticDelete,
    undoDeleteManufacturers: manufacturerOps.undoDelete,
    upsertManufacturerLocal: manufacturerOps.upsertLocal,
    deleteSuppliersOptimistic: supplierOps.optimisticDelete,
    undoDeleteSuppliers: supplierOps.undoDelete,
    upsertSupplierLocal: supplierOps.upsertLocal,
  }
})
