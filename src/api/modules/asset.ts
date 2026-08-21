import { ElMessage } from 'element-plus'
import { request } from '@/api/config/request'
import service from '@/api/config/request'
import type { Asset, AssetForm, AssetLog, AssetPageQuery } from '@/api/interface/asset'
import type { PageResp } from '@/api/interface/asset'

/** M03 资产 API（契约依据：asset-backend AssetController，后端已实现） */
export const assetApi = {
  /** 资产分页列表（支持状态/分类/位置/公司/使用人/管理员/关键词筛选，服务端分页） */
  getAssets: (params?: AssetPageQuery) =>
    request<PageResp<Asset>>({ url: '/v1/assets', method: 'get', params }),

  /** 资产详情（含关联名称） */
  getAssetById: (id: number) =>
    request<Asset>({ url: `/v1/assets/${id}`, method: 'get' }),

  /** 新增资产（后端强制状态 IDLE，barcode 唯一冲突返回 409） */
  createAsset: (data: AssetForm) =>
    request<Asset>({ url: '/v1/assets', method: 'post', data }),

  /** 编辑资产基础信息（不含状态变更） */
  updateAsset: (id: number, data: AssetForm) =>
    request<Asset>({ url: `/v1/assets/${id}`, method: 'put', data }),

  /** 报废资产（仅 闲置/在用 可报废，写操作日志） */
  discardAsset: (id: number, reason?: string) =>
    request<void>({ url: `/v1/assets/${id}/discard`, method: 'post', data: { reason } }),

  /** 资产操作日志（时间倒序） */
  getAssetLogs: (id: number) =>
    request<AssetLog[]>({ url: `/v1/assets/${id}/logs`, method: 'get' }),
}

/**
 * 资产 Excel 导出：按当前筛选条件导出全部（blob 下载，文件名取自 Content-Disposition）。
 * 注意：导出端点鉴权失败时返回的是 JSON 错误体（blob 包裹），此处校验 content-type 兜底提示。
 */
export async function exportAssets(params?: Omit<AssetPageQuery, 'page' | 'size'>): Promise<void> {
  const resp = await service.get<Blob>('/v1/assets/export', { params, responseType: 'blob' })
  const contentType = String(resp.headers?.['content-type'] ?? '')
  if (contentType.includes('application/json')) {
    // 鉴权/业务失败：blob 里是 JSON 错误体，解析后统一提示
    const text = await resp.data.text()
    let message = '导出失败'
    try {
      message = JSON.parse(text)?.message || message
    } catch {
      /* 保持默认提示 */
    }
    ElMessage.error(message)
    return
  }

  // 文件名解析：Content-Disposition attachment;filename*=utf-8''资产清单.xlsx
  const disposition = String(resp.headers?.['content-disposition'] ?? '')
  const match = disposition.match(/filename\*=(?:utf-8|UTF-8)''([^;]+)/)
  const fileName = match ? decodeURIComponent(match[1]) : '资产清单.xlsx'

  const url = URL.createObjectURL(resp.data)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}
