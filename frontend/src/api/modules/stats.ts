import { request } from '@/api/config/request'
import type { AssetStatusSlice, StatsOverviewVO } from '@/api/interface/stats'

/** 后端 B3 实际响应（扁平字段，见 asset-backend StatsOverviewResp） */
interface StatsOverviewRaw {
  /** key=AssetStatus 枚举名（含 0 值），value=数量 */
  assetStatusCounts: Record<string, number>
  /** 待我处理单据数（三单合计，与审批中心"待我处理"口径一致） */
  myTodoCount: number
  /** 我的持有数：asset.user_id=我 且 status=IN_USE */
  myHoldingCount: number
  /** 我创建的进行中盘点数 */
  inProgressStocktakeCount: number
}

/** 资产状态展示元数据（label/颜色语义，枚举对齐后端 AssetStatus：IDLE/IN_USE/PENDING_CONFIRM/DISCARD） */
const STATUS_META: Record<string, { label: string; color: AssetStatusSlice['color'] }> = {
  IDLE: { label: '闲置', color: 'info' },
  IN_USE: { label: '在用', color: 'primary' },
  PENDING_CONFIRM: { label: '待确认', color: 'warning' },
  DISCARD: { label: '报废', color: 'danger' },
}

/**
 * 后端扁平结构 → 前端 VO 适配：
 * assetStatusCounts 展开成饼图切片（补 label/percent/color），
 * myTodoCount/myHoldingCount/inProgressStocktakeCount 装配进三个小卡。
 */
const adapt = (raw: StatsOverviewRaw): StatsOverviewVO => {
  const entries = Object.entries(raw.assetStatusCounts ?? {})
  const total = entries.reduce((acc, [, count]) => acc + (count || 0), 0)
  const assetStatusPie: AssetStatusSlice[] = entries.map(([status, count]) => {
    const meta = STATUS_META[status] ?? { label: status, color: 'info' as const }
    return {
      status,
      count: count || 0,
      label: meta.label,
      percent: total > 0 ? (count || 0) / total : 0,
      color: meta.color,
    }
  })
  return {
    assetStatusPie,
    todoStat: {
      // 后端仅给合计（定向+共享池不分列），定向细分留空由前端退回审批 store 计数
      total: raw.myTodoCount ?? 0,
      urgent: 0,
      sharedPool: 0,
    },
    holdingStat: { myHoldings: raw.myHoldingCount ?? 0 },
    stocktakeStat: { ongoing: raw.inProgressStocktakeCount ?? 0 },
  }
}

/** B3：工作台统计聚合（每小时可缓存，此处直接拉最新） */
export const statsApi = {
  overview: async () => adapt(await request<StatsOverviewRaw>({ url: '/v1/stats/overview', method: 'get' })),
}
