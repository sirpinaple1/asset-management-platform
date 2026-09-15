import { request } from '@/api/config/request'
import type { TransferOrder } from '@/api/interface/transfer'
import type {
  Stocktake,
  StocktakeBarcodeScanForm,
  StocktakeCreateForm,
  StocktakeItem,
  StocktakeItemStatus,
  StocktakeQuery,
  StocktakeReport,
  StocktakeScanForm,
} from '@/api/interface/stocktake'

/**
 * M07 盘点 API。
 * 契约依据：asset-backend c113b5a StocktakeController（/api/v1/stocktakes）。
 */
export const stocktakeApi = {
  /** 创建盘点任务（范围快照：位置含子树/分类，均可空=全库；报废资产不参与） */
  create: (data: StocktakeCreateForm) =>
    request<Stocktake>({ url: '/v1/stocktakes', method: 'post', data }),

  /** 盘点任务列表（status/userId/date 筛选，含范围名称与统计计数） */
  getStocktakes: (params?: StocktakeQuery) =>
    request<Stocktake[]>({ url: '/v1/stocktakes', method: 'get', params }),

  /** 盘点任务详情（含明细行、范围名称与统计计数） */
  getStocktakeById: (id: number) =>
    request<Stocktake>({ url: `/v1/stocktakes/${id}`, method: 'get' }),

  /** 开始盘点（PENDING → IN_PROGRESS） */
  start: (id: number) =>
    request<Stocktake>({ url: `/v1/stocktakes/${id}/start`, method: 'post' }),

  /** 取消盘点（仅 PENDING/IN_PROGRESS 且创建人可操作，明细与资产不变） */
  cancel: (id: number) =>
    request<Stocktake>({ url: `/v1/stocktakes/${id}/cancel`, method: 'post' }),

  /** 盘点明细列表（status 可选筛选五态之一） */
  getItems: (id: number, status?: StocktakeItemStatus) =>
    request<StocktakeItem[]>({
      url: `/v1/stocktakes/${id}/items`,
      method: 'get',
      params: status ? { status } : undefined,
    }),

  /** 明细确认（扫码或人工：实际位置判定 相符/位置不符，或 notFound=true 记盘亏；已盘不可重盘） */
  scanItem: (id: number, itemId: number, data: StocktakeScanForm) =>
    request<StocktakeItem>({
      url: `/v1/stocktakes/${id}/items/${itemId}/scan`,
      method: 'post',
      data,
    }),

  /** 按条码扫码（PDA 入口）：条码在任务明细中→更新；不在明细但已登记→记盘盈；未登记→404 */
  scanByBarcode: (id: number, data: StocktakeBarcodeScanForm) =>
    request<StocktakeItem>({ url: `/v1/stocktakes/${id}/scan`, method: 'post', data }),

  /** 完成盘点（剩余待盘明细记盘亏并写资产日志，IN_PROGRESS → COMPLETED） */
  complete: (id: number) =>
    request<Stocktake>({ url: `/v1/stocktakes/${id}/complete`, method: 'post' }),

  /** 盘点报告（五态汇总 + 位置不符/盘亏/盘盈差异明细；进行中可看实时统计） */
  getReport: (id: number) =>
    request<StocktakeReport>({ url: `/v1/stocktakes/${id}/report`, method: 'get' }),

  /** 对位置不符明细批量生成调拨单（按实际位置分组，source=INVENTORY_TRIGGERED；任务须已完成且未生成过） */
  createTransfers: (id: number) =>
    request<TransferOrder[]>({ url: `/v1/stocktakes/${id}/transfer`, method: 'post' }),
}
