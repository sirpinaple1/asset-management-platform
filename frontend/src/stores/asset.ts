import { defineStore } from 'pinia'
import { ref } from 'vue'
import { assetApi } from '@/api/modules/asset'
import type { Asset, AssetPageQuery } from '@/api/interface/asset'

/**
 * M03 资产状态管理（服务端分页：只持有当前页数据与总数，
 * 筛选/翻页由页面组装 AssetPageQuery 触发重新拉取）。
 */
export const useAssetStore = defineStore('asset', () => {
  /** 当前页资产列表 */
  const assets = ref<Asset[]>([])
  /** 筛选条件下的总条数（服务端返回） */
  const total = ref(0)
  const loading = ref(false)

  /** 请求序号：快速切换筛选时只保留最新一次请求的结果（竞态保护） */
  let requestSeq = 0

  const fetchAssets = async (query: AssetPageQuery) => {
    const seq = ++requestSeq
    loading.value = true
    try {
      const page = await assetApi.getAssets(query)
      if (seq !== requestSeq) return /* 已有更新的请求，丢弃过期结果 */
      assets.value = page.records
      total.value = page.total
    } finally {
      if (seq === requestSeq) loading.value = false
    }
  }

  /** 编辑成功：用响应数据原地合并当前页对应行（免整页刷新闪烁） */
  const upsertLocal = (item: Asset) => {
    const idx = assets.value.findIndex((it) => it.id === item.id)
    if (idx >= 0) assets.value[idx] = item
  }

  return { assets, total, loading, fetchAssets, upsertLocal }
})
