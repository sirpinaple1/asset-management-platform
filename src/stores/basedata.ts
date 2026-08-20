import { defineStore } from 'pinia'
import { ref } from 'vue'
import { basedataApi } from '@/api/modules/basedata'
import type {
  Company,
  Manufacturer,
  Supplier,
  Category,
  Location,
  AssetModel,
} from '@/api/interface/basedata'

export const useBasedataStore = defineStore('basedata', () => {
  // 状态
  const companies = ref<Company[]>([])
  const manufacturers = ref<Manufacturer[]>([])
  const suppliers = ref<Supplier[]>([])
  const categories = ref<Category[]>([])
  const locations = ref<Location[]>([])
  const models = ref<AssetModel[]>([])

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

  const fetchManufacturers = async () => {
    loading.value.manufacturers = true
    try {
      manufacturers.value = await basedataApi.getManufacturers()
    } finally {
      loading.value.manufacturers = false
    }
  }

  const fetchSuppliers = async () => {
    loading.value.suppliers = true
    try {
      suppliers.value = await basedataApi.getSuppliers()
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

  return {
    companies,
    manufacturers,
    suppliers,
    categories,
    locations,
    models,
    loading,
    fetchCompanies,
    fetchManufacturers,
    fetchSuppliers,
    fetchCategories,
    fetchLocations,
    fetchModels,
  }
})
