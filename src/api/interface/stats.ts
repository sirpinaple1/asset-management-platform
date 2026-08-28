/**
 * B3 stats/overview 工作台统计聚合接口类型
 */

/** 资产状态占比单块 */
export interface AssetStatusSlice {
  /** 'IDLE' | 'IN_USE' | 'PENDING_CONFIRM' | 'DISCARD' 等，服务端透传 */
  status: string
  count: number
  /** 前端展示标签：闲置 / 在用 / 待确认 / 报废 等 */
  label: string
  /** 0 ~ 1 */
  percent: number
  /** Element Plus tag/color 语义：primary / success / warning / danger / info */
  color: string
}

/** 待办概况 */
export interface TodoStat {
  /** 总待办（= 定向给我 + 共享池） */
  total: number
  /** 定向给我（更紧急） */
  urgent: number
  /** 共享池待办（任何有权限用户可处理） */
  sharedPool: number
}

/** 持有概况 */
export interface HoldingStat {
  /** 我名下正在使用的资产数量 */
  myHoldings: number
  /** 我所属部门持有的资产数量（可能为 undefined） */
  deptHoldings?: number
}

/** 盘点概况 */
export interface StocktakeStat {
  /** 进行中的盘点单数 */
  ongoing: number
  /** 最近 30 天已完成的盘点单数量（可能为 undefined） */
  recent?: number
}

/** 工作台统计总响应 */
export interface StatsOverviewVO {
  assetStatusPie: AssetStatusSlice[]
  todoStat: TodoStat
  holdingStat: HoldingStat
  stocktakeStat: StocktakeStat
}
